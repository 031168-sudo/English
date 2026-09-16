package com.english.pronunciation

data class Category(
    val id: String,
    val title: String,
    val icon: String,
    val words: List<Word>
)
