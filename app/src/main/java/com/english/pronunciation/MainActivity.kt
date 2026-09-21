package com.english.pronunciation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private sealed class Screen {
    object Splash : Screen()
    object CategoryList : Screen()
    data class Practice(val category: Category, val wordIndices: List<Int>) : Screen()
    data class Summary(
        val category: Category,
        val wordIndices: List<Int>,
        val results: Map<Int, Int>
    ) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PronunciationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PronunciationTrainerApp()
                }
            }
        }
    }
}

@Composable
private fun PronunciationTrainerApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }

    when (val current = screen) {
        is Screen.Splash -> {
            SplashScreen(onFinished = { screen = Screen.CategoryList })
        }
        is Screen.CategoryList -> {
            CategoryListScreen(
                categories = CategoryBank.categories,
                onCategorySelected = { category ->
                    screen = Screen.Practice(category, category.words.indices.toList())
                }
            )
        }
        is Screen.Practice -> {
            PracticeScreen(
                category = current.category,
                wordIndices = current.wordIndices,
                onFinished = { results ->
                    screen = Screen.Summary(current.category, current.wordIndices, results)
                },
                onExit = { screen = Screen.CategoryList }
            )
        }
        is Screen.Summary -> {
            SummaryScreen(
                category = current.category,
                wordIndices = current.wordIndices,
                results = current.results,
                onRestartAll = {
                    screen = Screen.Practice(current.category, current.category.words.indices.toList())
                },
                onRetryMistakes = {
                    val mistakes = current.wordIndices.filter { (current.results[it] ?: 0) < 100 }
                    screen = Screen.Practice(current.category, mistakes)
                },
                onBackToCategories = { screen = Screen.CategoryList }
            )
        }
    }
}
