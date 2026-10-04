package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.player.v2.logic.miniPlayerWaveOffset
import kotlin.math.PI
import kotlin.math.ceil

/** A quiet remaining track, separated from the gently moving played segment. */
@Composable
internal fun MiniPlayerProgress(
    content: MiniPlayerContent,
    colorScheme: ColorScheme,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val running = content.isPlaying && !content.isLoading && motionEnabled
    val progress = animateFloatAsState(
        targetValue = miniPlayerCompactProgress(content),
        animationSpec = tween(350, easing = LinearEasing),
        label = "miniProgress",
    )
    val amplitude = animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(240),
        label = "miniProgressWave",
    )
    // Reuse the compact contour's drawing-only clock and lifecycle pause behavior.
    val phase = rememberCompactPlayerRotation(content.episode.id, running)
    Canvas(modifier = modifier) {
        val stroke = 2.5.dp.toPx().coerceAtMost(size.height).coerceAtMost(size.width)
        if (stroke <= 0f) return@Canvas
        val start = stroke / 2f
        val end = size.width - start
        val playedLength = (end - start) * progress.value.coerceIn(0f, 1f)
        val playedEnd = start + playedLength
        val center = size.height / 2f
        val gap = if (playedLength > 0f) 3.dp.toPx() + stroke else 0f
        val remainingStart = playedEnd + gap
        if (remainingStart < end) {
            drawLine(
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                start = Offset(remainingStart, center),
                end = Offset(end, center),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        if (playedLength <= 0f) return@Canvas
        val wave = Path().apply { moveTo(start, center) }
        val steps = ceil(playedLength / 1.dp.toPx()).toInt().coerceAtLeast(1)
        val waveAmplitude = ((size.height - stroke) / 2f).coerceAtMost(1.25.dp.toPx()) * amplitude.value
        val wavePhase = phase.value / CompactPlayerRotationPeriod * (2 * PI).toFloat()
        for (step in 1..steps) {
            val distance = playedLength * step / steps
            wave.lineTo(
                start + distance,
                center + miniPlayerWaveOffset(distance, playedLength, waveAmplitude, 24.dp.toPx(), wavePhase),
            )
        }
        drawPath(wave, colorScheme.primary, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
