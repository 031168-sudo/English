package com.english.pronunciation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val ButtonHeight = 56.dp

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
        overallPercent >= 40 -> "Неплохо, продолжайте!" to "💪"
        else -> "Есть куда расти!" to "🔥"
    }
    val scoreColor = feedbackFor(overallPercent).second
    val animatedScore by animateFloatAsState(
        targetValue = overallPercent / 100f,
        animationSpec = tween(durationMillis = 900),
        label = "summaryScore"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${category.icon} ${category.title}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedScore },
                    modifier = Modifier.size(180.dp),
                    color = scoreColor,
                    strokeWidth = 14.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$overallPercent%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                    Text(
                        text = "средняя точность",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = if (mistakeIndices.isEmpty()) {
                    "Все ${wordIndices.size} слов на 100% — идеально!"
                } else {
                    "Ниже 100%: ${mistakeIndices.size} из ${wordIndices.size} слов"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mistakeIndices.isNotEmpty()) {
                    Button(
                        onClick = onRetryMistakes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight)
                    ) {
                        Text("🎯  Повторить ошибки (${mistakeIndices.size})")
                    }
                }
                FilledTonalButton(
                    onClick = onRestartAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonHeight)
                ) {
                    Text("🔁  Пройти заново")
                }
                OutlinedButton(
                    onClick = onBackToCategories,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonHeight)
                ) {
                    Text("←  К категориям")
                }
            }
        }
    }
}
