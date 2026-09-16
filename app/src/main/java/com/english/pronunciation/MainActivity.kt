package com.english.pronunciation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PronunciationTrainerScreen()
                }
            }
        }
    }
}

@Composable
fun PronunciationTrainerScreen() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    var currentIndex by remember { mutableStateOf(0) }
    val currentWord = WordBank.words[currentIndex]

    var isSpeaking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var resultPercent by remember { mutableStateOf<Int?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val sessionScores = remember { mutableStateListOf<Int>() }

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

    val recognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?: arrayListOf()
            if (matches.isEmpty()) {
                statusMessage = "Речь не распознана. Попробуйте ещё раз."
                resultPercent = null
            } else {
                val score = PronunciationScorer.score(currentWord.english, matches)
                resultPercent = score
                sessionScores.add(score)
                statusMessage = null
            }
        } else {
            statusMessage = "Запись отменена."
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: ${currentWord.english}")
        }
        isListening = true
        runCatching { recognizerLauncher.launch(intent) }.onFailure {
            isListening = false
            statusMessage = "На этом устройстве недоступно распознавание речи."
        }
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

    fun nextWord() {
        currentIndex = (currentIndex + 1) % WordBank.words.size
        resultPercent = null
        statusMessage = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Слово ${currentIndex + 1} из ${WordBank.words.size}",
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

        if (sessionScores.isNotEmpty()) {
            Text(
                text = "Средний результат за сессию: ${sessionScores.average().toInt()}% " +
                    "(${sessionScores.size} попыток)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(onClick = { nextWord() }) {
            Text("Следующее слово →")
        }
    }
}

private fun feedbackFor(percent: Int): Pair<String, Color> = when {
    percent >= 85 -> "Отлично! 🎉" to Color(0xFF2E7D32)
    percent >= 65 -> "Хорошо, но можно лучше 👍" to Color(0xFFF9A825)
    percent >= 40 -> "Нужно ещё потренироваться 💪" to Color(0xFFEF6C00)
    else -> "Попробуй ещё раз 🔄" to Color(0xFFC62828)
}
