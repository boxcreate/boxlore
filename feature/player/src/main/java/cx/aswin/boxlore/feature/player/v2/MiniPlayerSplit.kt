package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.ArtworkMiniPlayerColors
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

/** The Play end of the split bar. */
internal val MiniPlaySegmentWidth = 72.dp

/** The see-through gap between the info segment and the Play end. */
internal val MiniSegmentGap = 3.dp

/** Inner corners where the two segments meet; outer ends follow the bar's own rounding. */
internal val MiniSegmentInnerCorner = 8.dp

/** The bar shows as two joined segments at rest and merges into one surface as it starts to shrink. */
internal fun miniSplitAmount(compactFraction: Float): Float = (1f - compactFraction / 0.35f).coerceIn(0f, 1f)

/** "1h 5m left" or "23m left"; null without a known duration. */
internal fun miniPlayerTimeLeft(positionMs: Long, durationMs: Long): String? {
    if (durationMs <= 0L) return null
    val remainingMinutes = ((durationMs - positionMs.coerceIn(0L, durationMs) + 59_999L) / 60_000L).coerceAtLeast(1L)
    return if (remainingMinutes >= 60) "${remainingMinutes / 60}h ${remainingMinutes % 60}m left" else "${remainingMinutes}m left"
}

/**
 * Paints the bar as a connected pair: the info segment in the bar colour with the played share
 * swept in a step deeper (lighter in dark mode), a see-through gap, and the Play end in the accent.
 * The outer ends come from the bar's clip; [split] at 0 paints the single surface the compact
 * cookie morph starts from.
 */
internal fun Modifier.miniPlayerSplitSurface(
    colors: ArtworkMiniPlayerColors,
    cookieFraction: Float,
    split: () -> Float,
    progress: State<Float>,
): Modifier = drawBehind {
    val alpha = (1f - cookieFraction).coerceIn(0f, 1f)
    if (alpha <= 0f) return@drawBehind
    val amount = split().coerceIn(0f, 1f)
    if (amount <= 0f) {
        drawRect(colors.bar, alpha = alpha)
        return@drawBehind
    }
    val playWidth = MiniPlaySegmentWidth.toPx().coerceAtMost(size.width)
    val gap = MiniSegmentGap.toPx() * amount
    val inner = CornerRadius(MiniSegmentInnerCorner.toPx() * amount)
    val rtl = layoutDirection == LayoutDirection.Rtl
    val infoWidth = (size.width - playWidth - gap).coerceAtLeast(0f)
    val infoLeft = if (rtl) playWidth + gap else 0f
    val playLeft = if (rtl) 0f else infoWidth + gap
    // Each segment rounds only the corners where it meets the other; the bar's clip rounds the outer ends.
    val info = segmentPath(infoLeft, infoWidth, size.height, roundRight = !rtl, inner)
    val play = segmentPath(playLeft, playWidth, size.height, roundRight = rtl, inner)
    drawPath(info, colors.bar, alpha = alpha)
    val played = infoWidth * progress.value.coerceIn(0f, 1f)
    if (played > 0f) {
        clipPath(info) {
            drawRect(
                color = colors.progress,
                topLeft = Offset(if (rtl) infoLeft + infoWidth - played else infoLeft, 0f),
                size = Size(played, size.height),
                alpha = alpha * amount,
            )
        }
    }
    drawPath(play, lerp(colors.bar, colors.play, amount), alpha = alpha)
}

private fun segmentPath(left: Float, width: Float, height: Float, roundRight: Boolean, inner: CornerRadius): Path = Path().apply {
    val right = if (roundRight) inner else CornerRadius.Zero
    val leftCorner = if (roundRight) CornerRadius.Zero else inner
    addRoundRect(RoundRect(left, 0f, left + width, height, leftCorner, right, right, leftCorner))
}

/** The scalloped progress contour around the cover, shared by the bar and the compact cookie. */
internal fun DrawScope.drawPlayerProgressRing(progress: Float, cookieFraction: Float, rotation: Float, track: Color, played: Color) {
    val stroke = MiniPlayerCompactProgressStroke.toPx()
    val contour = compactPlayerPath(size, cookieFraction, inset = stroke / 2f + 0.25.dp.toPx(), rotation = rotation)
    drawPath(path = contour, color = track, style = Stroke(stroke))
    val measure = PathMeasure().apply { setPath(contour, true) }
    val playedPath = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), playedPath, startWithMoveTo = true)
    drawPath(path = playedPath, color = played, style = Stroke(stroke, cap = StrokeCap.Round))
}

/** The bar's revolving wavy ring around the cover; it fades out as the bar shrinks into the cookie. */
@Composable
internal fun MiniPlayerBarRing(
    progress: State<Float>,
    colors: ArtworkMiniPlayerColors,
    rotation: State<Float>,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.graphicsLayer { this.alpha = alpha() }.clearAndSetSemantics {}) {
        drawPlayerProgressRing(progress.value, cookieFraction = 1f, rotation = rotation.value, track = colors.onBar.copy(alpha = 0.18f), played = colors.play)
    }
}

/** The bar's Play end: the whole segment is the target, its colour painted by the split surface. */
@Composable
internal fun MiniPlaySegment(
    isPlaying: Boolean,
    isLoading: Boolean,
    colors: ArtworkMiniPlayerColors,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(topStart = MiniSegmentInnerCorner, bottomStart = MiniSegmentInnerCorner)
    Box(
        modifier = modifier.expressiveClickable(
            feedback = ExpressivePressFeedback.Control,
            shape = shape,
            indication = ripple(bounded = true),
            enabled = !isLoading && enabled,
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPlayPause()
        },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = isLoading, animationSpec = tween(220), label = "miniLoading") { loading ->
            if (loading) {
                BoxLoreLoader.CircularWavy(size = 24.dp, color = colors.onPlay, trackColor = colors.onPlay.copy(alpha = 0.24f))
            } else {
                Crossfade(targetState = isPlaying, animationSpec = tween(180), label = "miniPlayPause") { playing ->
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = colors.onPlay,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
