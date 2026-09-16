package com.english.pronunciation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SummaryScreen(
    category: Category,
    wordIndices: List<Int>,
    results: Map<Int, Int>,
    onRestartAll: () -> Unit,
    onRetryMistakes: () -> Unit,
    onBackToCategories: () -> Unit
) {
    val overallPercent = if (wordIndices.isEmpty()) {
        0
    } else {
        wordIndices.sumOf { results[it] ?: 0 } / wordIndices.size
    }
    val mistakeIndices = wordIndices.filter { (results[it] ?: 0) < 100 }
    val (title, emoji) = when {
        overallPercent >= 90 -> "Превосходно!" to "🏆"
        overallPercent >= 70 -> "Отличная работа!" to "🎉"
        overallPercent >= 40 -> "Неплохо, продолжай!" to "💪"
        else -> "Есть куда расти!" to "🔥"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 72.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Text(text = "$overallPercent%", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (mistakeIndices.isEmpty()) {
                    "Все слова на 100%!"
                } else {
                    "Слов не на 100%: ${mistakeIndices.size} из ${wordIndices.size}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            Button(onClick = onRestartAll) {
                Text("🔁 Пройти заново")
            }

            if (mistakeIndices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetryMistakes) {
                    Text("🎯 Повторить только ошибки (${mistakeIndices.size})")
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBackToCategories) {
                Text("← К категориям")
            }
        }
    }
}
