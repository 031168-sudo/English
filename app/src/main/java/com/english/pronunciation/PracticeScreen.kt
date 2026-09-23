package com.english.pronunciation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.LaunchedEffect
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

    var engineStatus by remember { mutableStateOf(SpeechEngine.status) }
    var session by remember { mutableStateOf<SpeechEngine.Session?>(null) }
    val recognitionAvailable = engineStatus == SpeechEngine.Status.READY

    LaunchedEffect(Unit) {
        SpeechEngine.prepare(context) { engineStatus = SpeechEngine.status }
    }

    DisposableEffect(Unit) {
        onDispose { session?.stop() }
    }

    /** Scores [matches] against the current word and shows the result. */
    fun acceptMatches(matches: List<String>): Boolean {
        val usable = matches.filter { it.isNotBlank() }
        if (usable.isEmpty()) return false
        val score = PronunciationScorer.score(currentWord.english, usable)
        resultPercent = score
        heardText = usable.first()
        results[wordIndices[position]] = score
        heardTexts[wordIndices[position]] = usable.first()
        return true
    }

    fun startListening() {
        when (engineStatus) {
            SpeechEngine.Status.LOADING -> {
                statusMessage = "Распознавание ещё готовится, попробуйте через секунду."
                return
            }
            SpeechEngine.Status.FAILED -> {
                statusMessage = "Распознавание не запустилось: ${SpeechEngine.failure.orEmpty()}"
                return
            }
            SpeechEngine.Status.READY -> Unit
        }
        statusMessage = null
        resultPercent = null
        heardText = null
        isListening = true
        micLevel = 0f
        session = SpeechEngine.listen(
            onLevel = { level -> micLevel = level },
            onResult = { matches ->
                isListening = false
                micLevel = 0f
                session = null
                if (!acceptMatches(matches)) {
                    statusMessage = "Не удалось разобрать слово. Попробуйте ещё раз."
                }
            },
            onError = { message ->
                isListening = false
                micLevel = 0f
                session = null
                statusMessage = message
            }
        )
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
        session?.stop()
        session = null
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
            session?.stop()
        session = null
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
                        Text(
                            when {
                                isListening -> "🎙  Слушаю вас..."
                                engineStatus == SpeechEngine.Status.LOADING -> "⏳  Готовим распознавание…"
                                else -> "🎤  Повторить слово"
                            }
                        )
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
                    text = when (engineStatus) {
                        SpeechEngine.Status.LOADING -> "Готовим распознавание речи…"
                        else -> "Распознавание не запустилось: ${SpeechEngine.failure.orEmpty()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (engineStatus == SpeechEngine.Status.LOADING) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
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
