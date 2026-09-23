package com.english.pronunciation

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Speech recognition that lives entirely inside the app.
 *
 * The acoustic model ships in the APK and the microphone is read by our own
 * loop, so nothing is sent anywhere and no system recognition service is
 * involved — the app works the same on a phone without Google services.
 */
object SpeechEngine {

    enum class Status { LOADING, READY, FAILED }

    private const val TAG = "SpeechEngine"
    private const val ASSET_DIR = "model-en-us"

    /** Bump when the bundled model changes, so the copy on disk is refreshed. */
    private const val MODEL_STAMP = "vosk-model-small-en-us-0.15"

    private const val SAMPLE_RATE = 16000
    private const val CHUNK = 3200                 // 200 ms of 16-bit mono audio
    private const val SPEECH_LEVEL = 0.10f         // above this counts as talking
    private const val TAIL_SILENCE_MS = 1200L      // stop this long after the last sound
    private const val NO_SPEECH_MS = 5000L         // give up if nothing is said at all
    private const val MAX_UTTERANCE_MS = 9000L

    @Volatile
    var status: Status = Status.LOADING
        private set

    @Volatile
    var failure: String? = null
        private set

    @Volatile
    private var model: Model? = null

    @Volatile
    private var modelDir: File? = null

    private var vocabulary: Set<String>? = null
    private var vocabularyChecked = false

    private var loadStarted = false
    private val main = Handler(Looper.getMainLooper())

    /**
     * Copies the model out of the APK the first time and loads it. Safe to call
     * from every composition; [onChanged] fires on the main thread whenever
     * [status] moves on.
     */
    fun prepare(context: Context, onChanged: () -> Unit) {
        if (status == Status.READY || loadStarted) {
            main.post(onChanged)
            return
        }
        loadStarted = true
        val appContext = context.applicationContext
        thread(name = "vosk-load") {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val dir = unpackModel(appContext)
                model = Model(dir.absolutePath)
                modelDir = dir
                status = Status.READY
            } catch (t: Throwable) {
                Log.e(TAG, "model failed to load", t)
                failure = t.message ?: t::class.java.simpleName
                status = Status.FAILED
            }
            main.post(onChanged)
        }
    }

    /**
     * Records one attempt and reports what was heard. The returned handle stops
     * the recording early; otherwise it ends on its own after a pause.
     */
    fun listen(
        onLevel: (Float) -> Unit,
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit
    ): Session {
        val loaded = model
        if (loaded == null) {
            main.post { onError("Распознавание ещё готовится, попробуйте через секунду.") }
            return Session { }
        }
        val session = Recording(loaded, onLevel, onResult, onError)
        session.start()
        return Session { session.requestStop() }
    }

    /**
     * Whether the model can recognise every word of [phrase] at all. A word it
     * has never heard of cannot be "heard" however well it is said, so the
     * score would be unfairly low. Null when the model does not ship a word
     * list or is not unpacked yet. Reads a file on first use: call it off the
     * main thread.
     */
    fun knowsWords(phrase: String): Boolean? {
        val words = loadVocabulary() ?: return null
        return phrase.lowercase().split(' ').filter { it.isNotBlank() }.all { it in words }
    }

    @Synchronized
    private fun loadVocabulary(): Set<String>? {
        if (vocabularyChecked) return vocabulary
        val dir = modelDir ?: return null
        vocabularyChecked = true
        val file = File(dir, "graph/words.txt")
        if (!file.isFile) return null
        vocabulary = runCatching {
            file.useLines { lines ->
                lines.map { it.substringBefore(' ').lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("<") && !it.startsWith("#") }
                    .toHashSet()
            }
        }.getOrNull()
        return vocabulary
    }

    /** Handle for the caller: cancelling is the only thing it needs to do. */
    class Session(private val stopper: () -> Unit) {
        fun stop() = stopper()
    }

    // ---------------------------------------------------------------- model

    private fun unpackModel(context: Context): File {
        val target = File(context.filesDir, ASSET_DIR)
        val stamp = File(target, "stamp")
        if (stamp.isFile && stamp.readText() == MODEL_STAMP) return target

        target.deleteRecursively()
        target.mkdirs()
        copyAssetDir(context, ASSET_DIR, target)
        if (!File(target, "am").isDirectory && !File(target, "am-onnx").isDirectory) {
            throw IllegalStateException("в APK нет файлов модели ($ASSET_DIR)")
        }
        stamp.writeText(MODEL_STAMP)
        return target
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val entries = context.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            // A leaf: assets.list() returns nothing for plain files.
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
            }
            return
        }
        target.mkdirs()
        for (entry in entries) {
            copyAssetDir(context, "$assetPath/$entry", File(target, entry))
        }
    }

    // ------------------------------------------------------------ recording

    private class Recording(
        private val model: Model,
        private val onLevel: (Float) -> Unit,
        private val onResult: (List<String>) -> Unit,
        private val onError: (String) -> Unit
    ) {
        @Volatile
        private var stopRequested = false

        @Volatile
        private var finished = false

        fun requestStop() {
            stopRequested = true
        }

        fun start() {
            thread(name = "vosk-listen") { run() }
        }

        private fun finishOnMain(block: () -> Unit) {
            if (finished) return
            finished = true
            main.post(block)
        }

        private fun run() {
            var recorder: AudioRecord? = null
            var recognizer: Recognizer? = null
            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuffer <= 0) {
                    finishOnMain { onError("Микрофон недоступен на этом устройстве.") }
                    return
                }
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, CHUNK * 4)
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    finishOnMain { onError("Нет доступа к микрофону.") }
                    return
                }
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat()).apply {
                    setMaxAlternatives(5)
                }

                recorder.startRecording()
                val buffer = ShortArray(CHUNK)
                val startedAt = System.currentTimeMillis()
                var lastVoiceAt = 0L
                var heardSomething = false

                while (!stopRequested) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    val level = levelOf(buffer, read)
                    main.post { onLevel(level) }
                    recognizer.acceptWaveForm(buffer, read)

                    val now = System.currentTimeMillis()
                    if (level > SPEECH_LEVEL) {
                        heardSomething = true
                        lastVoiceAt = now
                    }
                    val elapsed = now - startedAt
                    if (heardSomething && now - lastVoiceAt > TAIL_SILENCE_MS) break
                    if (!heardSomething && elapsed > NO_SPEECH_MS) break
                    if (elapsed > MAX_UTTERANCE_MS) break
                }

                recorder.stop()
                main.post { onLevel(0f) }

                if (!heardSomething && !stopRequested) {
                    finishOnMain { onError("Вы ничего не сказали. Попробуйте снова.") }
                    return
                }
                val hypotheses = parse(recognizer.finalResult)
                if (hypotheses.isEmpty()) {
                    finishOnMain { onError("Не удалось разобрать слово. Попробуйте ещё раз.") }
                } else {
                    finishOnMain { onResult(hypotheses) }
                }
            } catch (se: SecurityException) {
                finishOnMain { onError("Нет доступа к микрофону.") }
            } catch (t: Throwable) {
                Log.e(TAG, "recording failed", t)
                finishOnMain { onError("Сбой распознавания: ${t.message ?: "неизвестная ошибка"}") }
            } finally {
                runCatching { recorder?.release() }
                runCatching { recognizer?.close() }
            }
        }

        /** Loudness on a 0..1 scale, spread over the range a voice actually uses. */
        private fun levelOf(buffer: ShortArray, read: Int): Float {
            var sum = 0.0
            for (i in 0 until read) {
                val v = buffer[i].toDouble()
                sum += v * v
            }
            val rms = sqrt(sum / read) / Short.MAX_VALUE
            if (rms <= 0.0) return 0f
            val db = 20.0 * log10(rms)
            return ((db + 50.0) / 40.0).toFloat().coerceIn(0f, 1f)
        }

        /**
         * Vosk answers either `{"alternatives":[{"text":…}]}` or `{"text":…}`.
         * The alternatives come back best-first, which is what the scorer wants.
         */
        private fun parse(json: String): List<String> {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
            val alternatives = root.optJSONArray("alternatives")
            val texts = mutableListOf<String>()
            if (alternatives != null) {
                for (i in 0 until alternatives.length()) {
                    alternatives.optJSONObject(i)?.optString("text")?.let { texts += it }
                }
            } else {
                texts += root.optString("text")
            }
            return texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
    }
}
