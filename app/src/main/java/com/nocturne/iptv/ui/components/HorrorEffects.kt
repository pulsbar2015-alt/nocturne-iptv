package com.nocturne.iptv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.nocturne.iptv.ui.theme.NocturnePalette
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * A slow, irregular opacity oscillation that reads like a failing bulb.
 * Returns an alpha in [min, max] that never fully settles.
 */
@Composable
fun rememberFlicker(min: Float = 0.82f, max: Float = 1f, periodMs: Int = 140): Float {
    val transition = rememberInfiniteTransition(label = "flicker")
    val value by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flickerAlpha"
    )
    return value
}

/**
 * Full-screen overlays stacked on top of content: a vignette that darkens the
 * edges and faint scanlines that breathe with the flicker.
 */
@Composable
fun HorrorOverlay(
    modifier: Modifier = Modifier,
    vignetteStrength: Float = 0.9f,
    scanlineAlpha: Float = 0.16f,
    showScanlines: Boolean = true
) {
    val flicker = rememberFlicker(min = 0.9f, max = 1f, periodMs = 220)

    Canvas(modifier = modifier) {
        drawVignette(vignetteStrength)
        if (showScanlines) {
            drawScanlines(alpha = scanlineAlpha * flicker)
        }
    }
}

private fun DrawScope.drawVignette(strength: Float) {
    val radius = size.maxDimension * 0.75f
    val brush = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            Color.Transparent,
            Color.Black.copy(alpha = (strength * 0.55f).coerceIn(0f, 1f)),
            Color.Black.copy(alpha = strength)
        ),
        center = center,
        radius = radius
    )
    drawRect(brush = brush)
}

private fun DrawScope.drawScanlines(alpha: Float) {
    if (alpha <= 0f) return
    val spacing = 4.dp.toPx()
    val lineColor = Color.Black.copy(alpha = alpha)
    var y = 0f
    while (y < size.height) {
        drawRect(color = lineColor, topLeft = Offset(0f, y), size = Size(size.width, 1.2f))
        y += spacing
    }
}

/**
 * Text that occasionally tears sideways in blood-red and cold-blue ghosts,
 * the way a corrupted VHS title card would.
 */
@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    glitchEveryMs: LongRange = 1_600L..4_500L,
    enabled: Boolean = true
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var ghostAlpha by remember { mutableFloatStateOf(0f) }

    if (enabled) {
        LaunchedEffect(text) {
            while (true) {
                delay(Random.nextLong(glitchEveryMs.first, glitchEveryMs.last))
                repeat(Random.nextInt(2, 5)) {
                    offsetX = Random.nextFloat() * 8f - 4f
                    ghostAlpha = Random.nextFloat() * 0.7f + 0.3f
                    delay(Random.nextLong(40, 110))
                }
                offsetX = 0f
                ghostAlpha = 0f
            }
        }
    }

    val resolvedColor = style.color.takeIf { it != Color.Unspecified } ?: NocturnePalette.Bone

    Box(modifier = modifier) {
        if (ghostAlpha > 0f) {
            Text(
                text = text,
                style = style.copy(color = NocturnePalette.Ember.copy(alpha = ghostAlpha * 0.8f)),
                modifier = Modifier.graphicsLayer { translationX = -offsetX }
            )
            Text(
                text = text,
                style = style.copy(color = Color(0xFF35D0D0).copy(alpha = ghostAlpha * 0.5f)),
                modifier = Modifier.graphicsLayer { translationX = offsetX }
            )
        }
        Text(text = text, style = style.copy(color = resolvedColor))
    }
}

/** Convenience: tint a subtree with a breathing red glow. */
@Composable
fun EmberGlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val flicker = rememberFlicker(min = 0.72f, max = 1f, periodMs = 900)
    Box(modifier = modifier.alpha(flicker)) { content() }
}

/** Deterministic pseudo-noise band used behind headers. */
@Composable
fun NoiseBand(modifier: Modifier = Modifier, color: Color = NocturnePalette.Blood, alpha: Float = 0.08f) {
    Canvas(modifier = modifier) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                val wobble = (sin((x + y) * 0.05f) + 1f) / 2f
                if (wobble > 0.6f) {
                    drawRect(
                        color = color.copy(alpha = alpha * wobble),
                        topLeft = Offset(x, y),
                        size = Size(step, step)
                    )
                }
                x += step
            }
            y += step
        }
    }
}