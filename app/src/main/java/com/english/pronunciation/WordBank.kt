package com.english.pronunciation

data class Word(
    val english: String,
    val transcription: String,
    val russian: String,
    val emoji: String
)

/**
 * Words that Russian speakers commonly mispronounce (th, w/v, vowel length),
 * mixed with everyday vocabulary that is easy to depict with an emoji.
 */
object WordBank {
    val words = listOf(
        Word("apple", "ˈæp.əl", "яблоко", "🍎"),
        Word("water", "ˈwɔː.tər", "вода", "💧"),
        Word("squirrel", "ˈskwɪr.əl", "белка", "🐿️"),
        Word("thumb", "θʌm", "большой палец", "👍"),
        Word("this", "ðɪs", "это", "👉"),
        Word("world", "wɜːld", "мир", "🌍"),
        Word("clothes", "kloʊðz", "одежда", "👕"),
        Word("vegetable", "ˈvedʒ.tə.bəl", "овощ", "🥦"),
        Word("weather", "ˈweð.ər", "погода", "🌦️"),
        Word("wolf", "wʊlf", "волк", "🐺"),
        Word("hello", "həˈloʊ", "привет", "👋"),
        Word("comfortable", "ˈkʌmf.tə.bəl", "удобный", "🛋️"),
        Word("chocolate", "ˈtʃɒk.lət", "шоколад", "🍫"),
        Word("island", "ˈaɪ.lənd", "остров", "🏝️"),
        Word("beautiful", "ˈbjuː.tɪ.fəl", "красивый", "🌸"),
        Word("mountain", "ˈmaʊn.tən", "гора", "⛰️"),
        Word("umbrella", "ʌmˈbrel.ə", "зонт", "☂️"),
        Word("bird", "bɜːd", "птица", "🐦"),
        Word("bridge", "brɪdʒ", "мост", "🌉"),
        Word("rabbit", "ˈræb.ɪt", "кролик", "🐰")
    )
}
