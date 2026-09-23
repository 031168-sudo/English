package com.english.pronunciation

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The words the user adds themselves. They live in a small JSON file on the
 * phone, newest first, and the list is Compose state so every screen that
 * shows it stays current.
 */
object MyWordsStore {

    const val CATEGORY_ID = "my_words"
    const val TITLE = "Мои слова"
    const val ICON = "✏️"
    const val DEFAULT_EMOJI = "📝"

    private const val TAG = "MyWordsStore"
    private const val FILE_NAME = "my_words.json"

    val words = mutableStateListOf<Word>()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                words += Word(
                    english = item.getString("en"),
                    transcription = item.optString("ipa"),
                    russian = item.optString("ru"),
                    emoji = item.optString("emoji").ifBlank { DEFAULT_EMOJI }
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "could not read $FILE_NAME", t)
        }
    }

    /** A practice block over the current list. */
    fun category(): Category = Category(CATEGORY_ID, TITLE, ICON, words.toList())

    fun contains(english: String): Boolean {
        val key = Dictionary.normalise(english)
        return words.any { Dictionary.normalise(it.english) == key }
    }

    fun add(context: Context, word: Word) {
        words.add(0, word)
        save(context)
    }

    /** Puts [updated] where [original] was, keeping the list order. */
    fun replace(context: Context, original: Word, updated: Word) {
        val index = words.indexOf(original)
        if (index >= 0) words[index] = updated else words.add(0, updated)
        save(context)
    }

    /** Removes [word] and returns where it was, so it can be put back. */
    fun remove(context: Context, word: Word): Int {
        val index = words.indexOf(word)
        if (index >= 0) {
            words.removeAt(index)
            save(context)
        }
        return index
    }

    fun restore(context: Context, index: Int, word: Word) {
        words.add(index.coerceIn(0, words.size), word)
        save(context)
    }

    private fun save(context: Context) {
        val array = JSONArray()
        for (word in words) {
            array.put(
                JSONObject()
                    .put("en", word.english)
                    .put("ipa", word.transcription)
                    .put("ru", word.russian)
                    .put("emoji", word.emoji)
            )
        }
        // Written aside and renamed, so a crash mid-write cannot eat the list.
        val target = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            temp.writeText(array.toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "could not write $FILE_NAME", t)
        }
    }
}

/** "1 слово", "3 слова", "12 слов". */
fun wordsLabel(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val noun = when {
        mod10 == 1 && mod100 != 11 -> "слово"
        mod10 in 2..4 && mod100 !in 12..14 -> "слова"
        else -> "слов"
    }
    return "$count $noun"
}
