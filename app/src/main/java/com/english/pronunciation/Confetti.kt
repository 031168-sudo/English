package com.english.pronunciation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

private val confettiColors = listOf(
    Color(0xFFE53935),
    Color(0xFFFDD835),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFFFB8C00)
)

private data class ConfettiParticle(
    val x: Float,
    val color: Color,
    val delay: Float,
    val size: Float,
    val rotationSpeed: Float
)

/**
 * One-shot confetti burst that falls from the top of the screen.
 * Plays once when this composable enters composition.
 */
@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val particles = remember {
        List(70) {
            ConfettiParticle(
                x = Random.nextFloat(),
                color = confettiColors.random(),
                delay = Random.nextFloat() * 0.4f,
                size = Random.nextFloat() * 10f + 6f,
                rotationSpeed = Random.nextFloat() * 720f - 360f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 2400, easing = LinearEasing))
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val t = ((progress.value - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
            if (t > 0f) {
                val y = t * (size.height + 200f) - 100f
                val x = particle.x * size.width
                rotate(particle.rotationSpeed * t, pivot = Offset(x, y)) {
                    drawRect(
                        color = particle.color,
                        topLeft = Offset(x - particle.size / 2, y - particle.size / 2),
                        size = Size(particle.size, particle.size * 1.6f)
                    )
                }
            }
        }
    }
}
