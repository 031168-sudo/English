package com.english.pronunciation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val FloatingWords = listOf(
    "hello" to 0.08f,
    "th" to 0.72f,
    "world" to 0.55f,
    "apple" to 0.24f,
    "sheep" to 0.80f,
    "water" to 0.40f,
    "bridge" to 0.14f,
    "rainbow" to 0.62f
)

/** Playful opener: the parrot swaying on its branch, drifting words and the app's own waveform. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "splash")
    val drop by pulse.animateFloat(
        initialValue = 6f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drop"
    )
    val wobble by pulse.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobble"
    )
    val drift by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "drift"
    )

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        delay(2000)
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
            .clickable { onFinished() }
    ) {
        FloatingWords.forEachIndexed { index, (word, xFraction) ->
            val phase = (drift + index / FloatingWords.size.toFloat()) % 1f
            Text(
                text = word,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .offset(x = maxWidth * xFraction, y = maxHeight * (1f - phase))
                    .alpha(0.10f + 0.28f * sin(phase * PI.toFloat()))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp)
                .alpha(appear.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.parrot),
                contentDescription = "Попугай на ветке",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = 210.dp, height = 252.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.78f)
                        rotationZ = wobble
                        translationY = drop
                    }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Учимся говорить\nправильно\nпо-английски",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "30 тем · 600 слов · слушай, повторяй, получай процент",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                VoiceWaveform(
                    level = 0f,
                    active = true,
                    synthetic = true,
                    color = Color.White,
                    accent = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Text(
            text = "нажмите, чтобы пропустить",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )
    }
}
