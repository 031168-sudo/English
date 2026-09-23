package com.english.pronunciation

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Pictures to choose from — things, animals and food, never people. */
private val EMOJI_CHOICES = listOf(
    "📝", "🐶", "🐱", "🐭", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮",
    "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦋", "🐢", "🐍", "🐟", "🐬", "🐳",
    "🦒", "🐘", "🦓", "🦖", "🍎", "🍌", "🍓", "🍉", "🥕", "🍞", "🧀", "🍕",
    "🍰", "🍦", "🥛", "☕", "🚗", "🚌", "🚲", "✈️", "🚀", "⛵", "🏠", "🏫",
    "🌳", "🌸", "☀️", "🌙", "⭐", "🌈", "❄️", "⚽", "🎈", "🎁", "🧸", "🎨",
    "📚", "✏️", "🎵", "⏰", "👕", "👟", "❤️", "😊"
)

private val ButtonHeight = 52.dp
private val WarningColor = Color(0xFFB26A00)
private val OkColor = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWordsScreen(onBack: () -> Unit, onPractice: () -> Unit) {
    val context = LocalContext.current
    val speaker = rememberSpeaker()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Word?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Start reading the dictionaries now, so they are ready when "Добавить" is tapped.
    LaunchedEffect(Unit) { Dictionary.prepare(context) { } }

    if (adding) {
        WordEditorScreen(
            original = null,
            speak = speaker::speak,
            onCancel = { adding = false },
            onSave = { word ->
                MyWordsStore.add(context, word)
                adding = false
            }
        )
        return
    }
    editing?.let { original ->
        WordEditorScreen(
            original = original,
            speak = speaker::speak,
            onCancel = { editing = null },
            onSave = { word ->
                MyWordsStore.replace(context, original, word)
                editing = null
            }
        )
        return
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "${MyWordsStore.ICON}  ${MyWordsStore.TITLE}",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К категориям")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Half-width buttons on a 360 dp phone with a large font: the
                    // default 24 dp padding plus an icon would wrap "Тренировать".
                    FilledTonalButton(
                        onClick = { adding = true },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(ButtonHeight)
                    ) {
                        OneLine("＋ Добавить")
                    }
                    Button(
                        onClick = onPractice,
                        enabled = MyWordsStore.words.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(ButtonHeight)
                    ) {
                        OneLine("Тренировать")
                    }
                }
            }
        }
    ) { innerPadding ->
        if (MyWordsStore.words.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = MyWordsStore.DEFAULT_EMOJI, fontSize = 56.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Здесь будут ваши слова",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Нажмите «Добавить» и напишите слово по-английски — " +
                        "транскрипцию и перевод приложение подскажет само.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                DictionaryCredits()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item {
                    Text(
                        text = wordsLabel(MyWordsStore.words.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
                items(MyWordsStore.words, key = { it.english }) { word ->
                    MyWordRow(
                        word = word,
                        onEdit = { editing = word },
                        onDelete = {
                            val index = MyWordsStore.remove(context, word)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar(
                                    message = "«${word.english}» удалено",
                                    actionLabel = "Отменить",
                                    duration = SnackbarDuration.Short
                                )
                                // Re-adding the same word meanwhile would give the list two equal keys.
                                if (result == SnackbarResult.ActionPerformed &&
                                    !MyWordsStore.contains(word.english)
                                ) {
                                    MyWordsStore.restore(context, index, word)
                                }
                            }
                        }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    DictionaryCredits()
                }
            }
        }
    }
}

@Composable
private fun OneLine(text: String) {
    Text(text = text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun MyWordRow(word: Word, onEdit: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = word.emoji, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.english,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (word.transcription.isNotBlank()) {
                    Text(
                        text = "/${word.transcription}/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (word.russian.isNotBlank()) {
                    Text(
                        text = word.russian,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Изменить ${word.english}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Удалить ${word.english}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The dictionaries' licences ask for credit wherever their data is shown. */
@Composable
private fun DictionaryCredits() {
    Text(
        text = "Транскрипции: CMU Pronouncing Dictionary © Carnegie Mellon University. " +
            "Переводы: OpenRussian.org, лицензия CC BY-SA 4.0.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    )
}

// ------------------------------------------------------- add or edit a word

/**
 * Adds a word, or edits [original] when it is given: the same screen, opened
 * with the saved values in place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WordEditorScreen(
    original: Word?,
    speak: (String) -> Boolean,
    onCancel: () -> Unit,
    onSave: (Word) -> Unit
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current
    val originalKey = original?.let { Dictionary.normalise(it.english) }

    var english by remember { mutableStateOf(original?.english.orEmpty()) }
    var transcription by remember { mutableStateOf(original?.transcription.orEmpty()) }
    var transcriptionEdited by remember { mutableStateOf(false) }
    var russian by remember { mutableStateOf(original?.russian.orEmpty()) }
    var russianEdited by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(original?.emoji ?: MyWordsStore.DEFAULT_EMOJI) }

    var dictionaryReady by remember { mutableStateOf(Dictionary.isReady) }
    var entry by remember { mutableStateOf(Dictionary.Entry(null, emptyList())) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var speechUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Dictionary.prepare(context) { dictionaryReady = Dictionary.isReady }
    }

    val key = Dictionary.normalise(english)
    val formatError = when {
        key.isEmpty() -> null
        !key.matches(Regex("[a-z' -]+")) -> "Только английские буквы, дефис и апостроф"
        key.split(' ').size > 3 -> "Не больше трёх слов"
        else -> null
    }
    // The word being edited does not clash with itself.
    val duplicate = key.isNotEmpty() && formatError == null && key != originalKey &&
        MyWordsStore.contains(key)
    val canSave = key.isNotEmpty() && formatError == null && !duplicate

    // Exact lookups are instant and follow every keystroke; the slower
    // "may be…" search waits for a pause in typing.
    LaunchedEffect(key, dictionaryReady) {
        suggestions = emptyList()
        if (key.isEmpty() || formatError != null) {
            entry = Dictionary.Entry(null, emptyList())
            return@LaunchedEffect
        }
        val found = Dictionary.lookup(key)
        entry = found
        // Opening a saved word must not overwrite what was saved; the
        // dictionary only fills in again once the English word is changed.
        if (key != originalKey) {
            if (!transcriptionEdited) transcription = found.transcription.orEmpty()
            if (!russianEdited) russian = found.translations.firstOrNull().orEmpty()
        }
        delay(350)
        if (!found.known && dictionaryReady) {
            suggestions = withContext(Dispatchers.Default) { Dictionary.suggest(key) }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (original == null) "Новое слово" else "Изменить слово",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Отмена")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        onSave(
                            Word(
                                english = key,
                                transcription = transcription.trim().trim('/').trim(),
                                russian = russian.trim(),
                                emoji = emoji
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Stays above the keyboard while it is open.
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .height(ButtonHeight)
                ) {
                    Text("Сохранить")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = english,
                onValueChange = { english = it },
                label = { Text("Слово по-английски") },
                singleLine = true,
                isError = formatError != null || duplicate,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { speechUnavailable = !speak(key) },
                        enabled = canSave || duplicate
                    ) {
                        Text("🔊", fontSize = 20.sp)
                    }
                },
                supportingText = {
                    val (text, color) = when {
                        formatError != null -> formatError to MaterialTheme.colorScheme.error
                        duplicate -> "Это слово уже есть в списке" to MaterialTheme.colorScheme.error
                        key.isEmpty() -> "Например: giraffe" to MaterialTheme.colorScheme.onSurfaceVariant
                        !dictionaryReady -> "Загружаем словарь…" to MaterialTheme.colorScheme.onSurfaceVariant
                        entry.known -> "✓ Есть в словаре" to OkColor
                        else -> "Такого слова нет в словаре — проверьте написание" to WarningColor
                    }
                    Text(text, color = color)
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Может быть:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(end = 2.dp)
                    )
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { english = suggestion },
                            label = { Text(suggestion) }
                        )
                    }
                }
            }

            if (speechUnavailable) {
                Text(
                    text = "Синтез речи пока недоступен — попробуйте через секунду.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = transcription,
                onValueChange = {
                    transcription = it
                    transcriptionEdited = it.isNotBlank()
                },
                label = { Text("Транскрипция") },
                singleLine = true,
                supportingText = {
                    Text(
                        when {
                            transcription.isBlank() -> "Можно оставить пустой"
                            transcription == entry.transcription -> "Из словаря"
                            else -> ""
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = russian,
                onValueChange = {
                    russian = it
                    russianEdited = it.isNotBlank()
                },
                label = { Text("Перевод") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = {
                    val options = entry.translations
                    Text(
                        when {
                            options.isEmpty() && key.isNotEmpty() && dictionaryReady ->
                                "Перевода нет в словаре — впишите свой"
                            options.isNotEmpty() && russian.isNotBlank() &&
                                russian.trim() !in options -> "В словаре: ${options.joinToString(", ")}"
                            else -> ""
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (entry.translations.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entry.translations.forEach { option ->
                        FilterChip(
                            selected = russian.trim() == option,
                            onClick = {
                                russian = option
                                russianEdited = true
                            },
                            label = { Text(option) }
                        )
                    }
                }
            }

            Text(
                text = "Картинка",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EMOJI_CHOICES.forEach { choice ->
                    val selected = choice == emoji
                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = if (selected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { emoji = choice }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = choice, fontSize = 22.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------ text to speech

@Composable
private fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }
    return speaker
}

/** Reads a word aloud so it can be checked before saving. */
private class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private var ready = false
    private val tts = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS &&
            tts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
    }

    /** Returns false when there is nothing to speak with yet. */
    fun speak(text: String): Boolean {
        if (!ready || text.isBlank()) return false
        tts.setSpeechRate(0.9f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
        return true
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
