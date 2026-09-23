package com.english.pronunciation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val ButtonHeight = 52.dp

/** Tall enough for the score card, so the buttons never move. */
private val FeedbackSlotHeight = 118.dp

/**
 * Practices the words at [wordIndices] (indices into [category].words), in order.
 * [onFinished] receives a score (0-100) per practiced word index once the last
 * word is completed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    category: Category,
    wordIndices: List<Int>,
    onFinished: (Map<Int, Int>) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var position by remember { mutableStateOf(0) }
    val currentWord = category.words[wordIndices[position]]
    val isLastWord = position == wordIndices.lastIndex

    var isSpeaking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var micLevel by remember { mutableStateOf(0f) }
    var resultPercent by remember { mutableStateOf<Int?>(null) }
    var heardText by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateMapOf<Int, Int>() }
    val heardTexts = remember { mutableStateMapOf<Int, String>() }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsStatus by remember { mutableStateOf<Int?>(null) }
    // Bumped per attempt so a watchdog from an earlier attempt cannot cut a newer one short.
    var speakAttempt by remember { mutableStateOf(0) }
    var listenAttempt by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            mainHandler.post { ttsStatus = status }
        }
        ttsEngine = engine
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            engine.stop()
            engine.shutdown()
        }
    }

    fun speakSlowThenFast(word: String) {
        val engine = ttsEngine
        if (engine == null || ttsStatus == null) {
            statusMessage = "Синтез речи ещё запускается, попробуйте через секунду."
            return
        }
        if (ttsStatus != TextToSpeech.SUCCESS) {
            statusMessage = "Синтез речи недоступен. Установите голосовой движок в настройках Android."
            return
        }
        val language = engine.setLanguage(Locale.US)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            statusMessage = "Не установлен английский голос. Настройки → Язык и ввод → Синтез речи."
            return
        }
        statusMessage = null
        isSpeaking = true
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "slow") {
                    engine.setSpeechRate(1.2f)
                    engine.speak(word, TextToSpeech.QUEUE_ADD, null, "fast")
                } else {
                    mainHandler.post { isSpeaking = false }
                }
            }
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    isSpeaking = false
                    statusMessage = "Не удалось произнести слово."
                }
            }
        })
        engine.setSpeechRate(0.5f)
        if (engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, "slow") == TextToSpeech.ERROR) {
            isSpeaking = false
            statusMessage = "Не удалось запустить произношение."
            return
        }
        // Without this the screen deadlocks if the engine never calls back:
        // both buttons stay disabled while isSpeaking is stuck true.
        speakAttempt++
        val attempt = speakAttempt
        mainHandler.postDelayed({ if (attempt == speakAttempt) isSpeaking = false }, 10_000)
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    val recognitionAvailable = speechRecognizer != null

    DisposableEffect(Unit) {
        onDispose { speechRecognizer?.destroy() }
    }

    /** Scores [matches] against the current word and shows the result. */
    fun acceptMatches(matches: List<String>, confidences: FloatArray?): Boolean {
        val usable = matches.filter { it.isNotBlank() }
        if (usable.isEmpty()) return false
        val score = PronunciationScorer.score(currentWord.english, usable, confidences)
        resultPercent = score
        heardText = usable.first()
        results[wordIndices[position]] = score
        heardTexts[wordIndices[position]] = usable.first()
        return true
    }

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            statusMessage = "На этом устройстве недоступно распознавание речи."
            return
        }
        statusMessage = null
        resultPercent = null
        heardText = null
        isListening = true
        micLevel = 0f
        // Short words ("tea", "eye") are often dropped from the final result
        // while they did appear in a partial one, so the last partial is kept
        // as a fallback.
        var lastPartial: List<String> = emptyList()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                micLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                micLevel = 0f
            }
            override fun onError(error: Int) {
                isListening = false
                micLevel = 0f
                if ((error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                    acceptMatches(lastPartial, null)
                ) {
                    return
                }
                statusMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось разобрать слово. Попробуйте ещё раз."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Вы ничего не сказали. Попробуйте снова."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят, попробуйте ещё раз."
                    SpeechRecognizer.ERROR_AUDIO -> "Не удалось записать звук с микрофона."
                    SpeechRecognizer.ERROR_CLIENT -> "Распознаватель отказал (ошибка 5)."
                    SpeechRecognizer.ERROR_SERVER -> "Сервис распознавания вернул ошибку."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Проблема с сетью при распознавании речи."
                    // Added in API 33; referenced by value to keep minSdk 26 happy.
                    12, 13 -> "На устройстве не установлен английский для распознавания речи."
                    else -> "Ошибка распознавания речи (код $error)."
                }
            }
            override fun onResults(bundleResults: Bundle) {
                isListening = false
                micLevel = 0f
                val matches = bundleResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: arrayListOf()
                val confidences = bundleResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                if (!acceptMatches(matches, confidences) && !acceptMatches(lastPartial, null)) {
                    statusMessage = "Речь не распознана. Попробуйте ещё раз."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                if (!partial.isNullOrEmpty()) lastPartial = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 8)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // A single short word is otherwise cut off before the engine
            // decides it heard anything at all.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1200
            )
        }
        recognizer.startListening(intent)
        listenAttempt++
        val attempt = listenAttempt
        mainHandler.postDelayed({
            if (attempt == listenAttempt && isListening) {
                isListening = false
                recognizer.cancel()
                statusMessage = "Распознаватель не ответил. Попробуйте ещё раз."
            }
        }, 15_000)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            statusMessage = "Нужен доступ к микрофону, чтобы проверить произношение."
        }
    }

    fun onRepeatClicked() {
        statusMessage = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Moves to [newPosition], restoring whatever that word already scored. */
    fun showWordAt(newPosition: Int) {
        speechRecognizer?.cancel()
        ttsEngine?.stop()
        isListening = false
        isSpeaking = false
        micLevel = 0f
        position = newPosition
        val wordIndex = wordIndices[newPosition]
        resultPercent = results[wordIndex]
        heardText = heardTexts[wordIndex]
        statusMessage = null
    }

    fun goToNextWord() {
        if (isLastWord) {
            speechRecognizer?.cancel()
            ttsEngine?.stop()
            isListening = false
            isSpeaking = false
            // Words the user skipped never scored, so they count as zero.
            wordIndices.forEach { index -> if (index !in results) results[index] = 0 }
            onFinished(results.toMap())
        } else {
            showWordAt(position + 1)
        }
    }

    fun goToPreviousWord() {
        if (position > 0) showWordAt(position - 1)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "${category.icon}  ${category.title}",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К категориям")
                    }
                }
            )
        },
        bottomBar = {
            // Pinned: on a 360 dp phone the card alone fills the screen, and a
            // scrolling button row ended up under the navigation bar.
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { speakSlowThenFast(currentWord.english) },
                        enabled = !isSpeaking && !isListening,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight)
                    ) {
                        Text(if (isSpeaking) "🔊  Произносим..." else "🔊  Слушать (медленно → быстро)")
                    }

                    Button(
                        onClick = { onRepeatClicked() },
                        enabled = recognitionAvailable && !isSpeaking && !isListening,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight)
                    ) {
                        Text(if (isListening) "🎙  Слушаю вас..." else "🎤  Повторить слово")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { goToPreviousWord() },
                            enabled = position > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(ButtonHeight)
                        ) {
                            Text("←  Назад")
                        }

                        Button(
                            onClick = { goToNextWord() },
                            modifier = Modifier
                                .weight(1f)
                                .height(ButtonHeight)
                        ) {
                            Text(if (isLastWord) "Завершить  🏁" else "Далее  →")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val progress by animateFloatAsState(
                targetValue = (position + 1f) / wordIndices.size,
                label = "blockProgress"
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Слово ${position + 1} из ${wordIndices.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            WordCard(currentWord)

            Spacer(Modifier.height(8.dp))

            // The waveform, the score and any error all live in one slot of a
            // fixed height: showing a result draws over the wave instead of
            // pushing the buttons down the screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FeedbackSlotHeight),
                contentAlignment = Alignment.Center
            ) {
                VoiceWaveform(
                    level = micLevel,
                    active = isListening || isSpeaking,
                    synthetic = isSpeaking,
                    color = MaterialTheme.colorScheme.primary,
                    accent = MaterialTheme.colorScheme.tertiary
                )

                val score = resultPercent
                if (score != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ScoreRow(percent = score, heard = heardText)
                    }
                } else if (statusMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            if (!recognitionAvailable) {
                Text(
                    text = "На этом устройстве недоступно распознавание речи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

        }
    }
}

@Composable
private fun WordCard(word: Word) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = word.emoji, fontSize = 46.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = word.english,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "/${word.transcription}/",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = word.russian,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScoreRow(percent: Int, heard: String?) {
    val (label, color) = feedbackFor(percent)
    val animated by animateFloatAsState(targetValue = percent / 100f, label = "scoreBar")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        LinearProgressIndicator(
            progress = { animated },
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(vertical = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.Center
        )
        if (heard != null) {
            Text(
                text = "услышано: $heard",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun feedbackFor(percent: Int): Pair<String, Color> = when {
    percent >= 90 -> "Отлично! 🎉" to Color(0xFF2E7D32)
    percent >= 70 -> "Хорошо, но можно чище 👍" to Color(0xFF558B2F)
    percent >= 50 -> "Похоже, но нужно потренироваться 💪" to Color(0xFFEF6C00)
    else -> "Попробуйте ещё раз 🔄" to Color(0xFFC62828)
}
