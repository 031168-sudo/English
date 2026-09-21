package com.english.pronunciation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private const val BAR_COUNT = 36
private const val FRAME_MS = 55L

/**
 * Rolling mirrored bar waveform.
 *
 * [level] carries the live microphone amplitude (0..1) while the user speaks.
 * With [synthetic] the bars animate on their own, which is what plays while the
 * app itself pronounces the word and no microphone signal exists.
 */
@Composable
fun VoiceWaveform(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    synthetic: Boolean = false,
    color: Color = Color.Unspecified,
    accent: Color = Color.Unspecified
) {
    val bars = remember { mutableStateListOf<Float>().apply { repeat(BAR_COUNT) { add(0f) } } }

    LaunchedEffect(active, synthetic) {
        var smoothed = 0f
        var phase = 0f
        while (true) {
            val target = when {
                !active -> 0f
                synthetic -> {
                    phase += 0.45f
                    (0.45f + 0.35f * sin(phase) + 0.2f * Random.nextFloat()).coerceIn(0f, 1f)
                }
                else -> level
            }
            smoothed = smoothed * 0.55f + target * 0.45f
            bars.removeAt(0)
            bars.add(smoothed)
            if (!active && bars.all { it < 0.01f }) return@LaunchedEffect
            delay(FRAME_MS)
        }
    }

    val baseColor = if (color == Color.Unspecified) Color(0xFF6750A4) else color
    val accentColor = if (accent == Color.Unspecified) Color(0xFF7D5260) else accent

    Canvas(modifier = modifier.fillMaxSize()) {
        val barWidth = size.width / (BAR_COUNT * 1.8f)
        val gap = (size.width - barWidth * BAR_COUNT) / (BAR_COUNT - 1).coerceAtLeast(1)
        val centerY = size.height / 2f
        val maxHeight = size.height * 0.9f
        val brush = Brush.horizontalGradient(listOf(baseColor, accentColor))

        bars.forEachIndexed { index, value ->
            val x = index * (barWidth + gap)
            // Taper the oldest bars so the trail fades out to the left.
            val fade = (index + 1f) / BAR_COUNT
            val height = (barWidth + value * maxHeight * fade).coerceAtMost(maxHeight)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, centerY - height / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
                alpha = 0.35f + 0.65f * fade
            )
        }

        if (bars.all { it < 0.02f }) {
            val idle = lerp(baseColor, accentColor, 0.5f)
            drawLine(
                color = idle.copy(alpha = 0.25f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
