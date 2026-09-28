package com.nocturne.iptv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nocturne.iptv.ui.components.GlitchText
import com.nocturne.iptv.ui.components.HorrorOverlay
import com.nocturne.iptv.ui.theme.NocturnePalette
import kotlinx.coroutines.delay

/**
 * Title card. Holds for a beat, then hands control to the caller.
 * A ring of red creeps out from the centre while the wordmark settles.
 */
@Composable
fun IntroScreen(
    onFinished: () -> Unit,
    holdMs: Long = 2_400L
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(holdMs)
        onFinished()
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 1_200, easing = LinearEasing),
        label = "introAlpha"
    )

    val pulse by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = tween(durationMillis = 1_800),
        label = "introPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(NocturnePalette.Coffin, NocturnePalette.Abyss, NocturnePalette.Abyss)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GlitchText(
                text = "NOCTURNE",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier
                    .alpha(contentAlpha)
                    .scale(pulse)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "iptv",
                style = MaterialTheme.typography.labelLarge,
                color = NocturnePalette.Ember,
                modifier = Modifier.alpha(contentAlpha * 0.9f)
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "do not adjust your set",
                style = MaterialTheme.typography.labelMedium,
                color = NocturnePalette.Ash,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(contentAlpha * 0.7f)
            )
        }

        HorrorOverlay(modifier = Modifier.fillMaxSize(), vignetteStrength = 1f, scanlineAlpha = 0.22f)
    }
}