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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

private val ButtonHeight = 56.dp

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
                    val confidences =
                        bundleResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val score = PronunciationScorer.score(
                        currentWord.english,
                        matches,
                        confidences
                    )
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
        if (resultPercent == null) {
            results[wordIndices[position]] = 0
        }
        if (isLastWord) {
            onFinished(results.toMap())
        } else {
            position++
            resultPercent = null
            statusMessage = null
        }
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

            Spacer(Modifier.height(16.dp))

            WordCard(currentWord)

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceWaveform(
                    level = micLevel,
                    active = isListening || isSpeaking,
                    synthetic = isSpeaking,
                    color = MaterialTheme.colorScheme.primary,
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                Button(
                    onClick = { goToNextWord() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonHeight)
                ) {
                    Text(if (isLastWord) "Завершить  🏁" else "Следующее слово  →")
                }
            }

            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }

            AnimatedVisibility(
                visible = resultPercent != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ScoreCard(percent = resultPercent ?: 0)
            }

            if (!recognitionAvailable) {
                Text(
                    text = "На этом устройстве недоступно распознавание речи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WordCard(word: Word) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(132.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = word.emoji, fontSize = 68.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = word.english,
                style = MaterialTheme.typography.displaySmall,
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
private fun ScoreCard(percent: Int) {
    val (label, color) = feedbackFor(percent)
    val animated by animateFloatAsState(targetValue = percent / 100f, label = "scoreBar")
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animated },
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = color,
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
