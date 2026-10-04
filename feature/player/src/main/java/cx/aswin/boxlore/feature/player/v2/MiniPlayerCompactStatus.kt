package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.feature.player.v2.logic.playbackFraction

/** Non-interactive status decoration around the same artwork used by the normal mini player. */
@Composable
internal fun MiniPlayerCompactStatus(
    content: MiniPlayerContent,
    colorScheme: ColorScheme,
    compactFraction: Float,
    cookieFraction: Float,
    rotation: State<Float>?,
    modifier: Modifier = Modifier,
) {
    if (compactFraction <= 0f) return
    val alpha = ((compactFraction - 0.4f) / 0.6f).coerceIn(0f, 1f)
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }.clearAndSetSemantics {}) {
        // Keep the whole track visible, including before Media3 has a duration.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = MiniPlayerCompactProgressStroke.toPx()
            val track = compactPlayerPath(
                size,
                cookieFraction,
                inset = stroke / 2f + 0.25.dp.toPx(),
                rotation = rotation?.value ?: 0f,
            )
            drawPath(
                path = track,
                color = colorScheme.surface,
                style = Stroke(stroke),
            )
            val measure = PathMeasure().apply { setPath(track, true) }
            val played = Path()
            measure.getSegment(0f, measure.length * miniPlayerCompactProgress(content), played, startWithMoveTo = true)
            drawPath(
                path = played,
                color = colorScheme.primary,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (content.isLoading) {
            BoxLoreLoader.CircularWavy(
                modifier = Modifier.align(Alignment.Center).size(20.dp),
                color = colorScheme.primary,
                trackColor = colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
            )
        }
        if (content.isCasting) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                    .size(18.dp).background(colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CastConnected,
                    contentDescription = null,
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

internal fun miniPlayerCompactDuration(content: MiniPlayerContent): Long =
    content.duration.takeIf { it > 0L } ?: content.episode.duration.coerceAtLeast(0).toLong() * 1_000L

internal fun miniPlayerCompactProgress(content: MiniPlayerContent): Float =
    playbackFraction(content.position, miniPlayerCompactDuration(content))

internal fun miniPlayerCompactStateDescription(content: MiniPlayerContent): String {
    val state = when {
        content.isLoading -> "Loading"
        content.isPlaying -> "Playing"
        else -> "Paused"
    }
    return if (content.isCasting) "$state, casting to ${content.castDeviceName ?: "Cast device"}" else state
}
