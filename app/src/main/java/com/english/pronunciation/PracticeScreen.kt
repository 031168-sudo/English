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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Practices the words at [wordIndices] (indices into [category].words), in order.
 * [onFinished] receives a score (0-100) per practiced word index once the last
 * word is completed.
 */
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
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateMapOf<Int, Int>() }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { }
        ttsEngine = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    fun speakSlowThenFast(word: String) {
        val engine = ttsEngine ?: return
        isSpeaking = true
        engine.language = Locale.US
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
                mainHandler.post { isSpeaking = false }
            }
        })
        engine.setSpeechRate(0.5f)
        engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, "slow")
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

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            statusMessage = "На этом устройстве недоступно распознавание речи."
            return
        }
        statusMessage = null
        resultPercent = null
        isListening = true
        micLevel = 0f
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
                statusMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось разобрать слово. Попробуйте ещё раз."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Вы ничего не сказали. Попробуйте снова."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят, попробуйте ещё раз."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Проблема с сетью при распознавании речи."
                    else -> "Ошибка распознавания речи."
                }
            }
            override fun onResults(bundleResults: Bundle) {
                isListening = false
                micLevel = 0f
                val matches = bundleResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: arrayListOf()
                if (matches.isEmpty()) {
                    statusMessage = "Речь не распознана. Попробуйте ещё раз."
                } else {
                    val score = PronunciationScorer.score(currentWord.english, matches)
                    resultPercent = score
                    results[wordIndices[position]] = score
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        recognizer.startListening(intent)
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

    fun goToNextWord() {
        speechRecognizer?.cancel()
        isListening = false
        micLevel = 0f
        val wordIndex = wordIndices[position]
        if (resultPercent == null) {
            results[wordIndex] = 0
        }
        if (isLastWord) {
            onFinished(results.toMap())
        } else {
            position++
            resultPercent = null
            statusMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${category.title} · слово ${position + 1} из ${wordIndices.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Text(text = currentWord.emoji, fontSize = 96.sp)

        Spacer(Modifier.height(16.dp))

        Text(text = currentWord.english, style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "/${currentWord.transcription}/",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = currentWord.russian, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(32.dp))

        Button(onClick = { speakSlowThenFast(currentWord.english) }, enabled = !isSpeaking) {
            Text(if (isSpeaking) "Проигрывание..." else "🔊 Слушать (медленно → быстро)")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onRepeatClicked() },
            enabled = recognitionAvailable && !isSpeaking && !isListening
        ) {
            Text(if (isListening) "Слушаю..." else "🎤 Повторить слово")
        }

        if (isListening) {
            Spacer(Modifier.height(12.dp))
            val animatedLevel by animateFloatAsState(targetValue = micLevel, label = "micLevel")
            LinearProgressIndicator(
                progress = { animatedLevel },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!recognitionAvailable) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "На этом устройстве недоступно распознавание речи.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))

        statusMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        resultPercent?.let { percent ->
            val (label, color) = feedbackFor(percent)
            Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "$percent%", style = MaterialTheme.typography.displaySmall, color = color)
                    Text(text = label, color = color)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = { goToNextWord() }) {
            Text(if (isLastWord) "Завершить 🏁" else "Следующее слово →")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onExit) {
            Text("← К категориям")
        }
    }
}

internal fun feedbackFor(percent: Int): Pair<String, Color> = when {
    percent >= 85 -> "Отлично! 🎉" to Color(0xFF2E7D32)
    percent >= 65 -> "Хорошо, но можно лучше 👍" to Color(0xFFF9A825)
    percent >= 40 -> "Нужно ещё потренироваться 💪" to Color(0xFFEF6C00)
    else -> "Попробуй ещё раз 🔄" to Color(0xFFC62828)
}
