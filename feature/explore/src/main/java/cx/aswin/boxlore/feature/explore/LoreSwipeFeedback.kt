package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

internal data class LoreSwipeFeedbackMotion(val alpha: Float, val scale: Float, val translationDp: Float)

internal fun loreSwipeFeedbackMotion(offsetX: Float, threshold: Float, direction: SwipeDirection): LoreSwipeFeedbackMotion {
    val sign = if (direction == SwipeDirection.Left) -1f else 1f
    val directedTravel = offsetX * sign
    val progress = if (directedTravel > 0f) loreSwipeProgress(directedTravel, threshold) else 0f
    // Wait for deliberate travel, then ease smoothly to full size at the action threshold.
    val reveal = ((progress - 0.12f) / 0.88f).coerceIn(0f, 1f)
    val eased = reveal * reveal * (3f - 2f * reveal)
    return LoreSwipeFeedbackMotion(
        alpha = eased,
        scale = 0.62f + 0.38f * eased,
        translationDp = sign * 28f * (1f - eased),
    )
}

internal const val LoreCueExitMillis = 140

/**
 * The cue while the card leaves: it keeps the size it had when released and fades out quickly,
 * instead of following the card's whole fly-out. A button action releases at 0, so no cue shows.
 */
internal fun loreSwipeCueMotion(
    offsetX: Float,
    releaseOffsetX: Float,
    exiting: Boolean,
    exitFade: Float,
    threshold: Float,
    direction: SwipeDirection,
): LoreSwipeFeedbackMotion {
    if (!exiting) return loreSwipeFeedbackMotion(offsetX, threshold, direction)
    val released = loreSwipeFeedbackMotion(releaseOffsetX, threshold, direction)
    return released.copy(alpha = released.alpha * exitFade.coerceIn(0f, 1f))
}

@Composable
internal fun BoxScope.LoreSwipeFeedback(swipeState: SwipeableCardState, thresholdPx: Float, accentColor: Color) {
    val exitFade = remember(swipeState) { Animatable(1f) }
    LaunchedEffect(swipeState, swipeState.phase) {
        if (swipeState.phase == LoreSwipePhase.Exiting) {
            exitFade.animateTo(0f, tween(LoreCueExitMillis, easing = FastOutLinearInEasing))
        } else {
            exitFade.snapTo(1f)
        }
    }
    LoreSwipeActionBadge(
        swipeState = swipeState,
        exitFade = exitFade,
        thresholdPx = thresholdPx,
        direction = SwipeDirection.Left,
        accentColor = accentColor,
        modifier = Modifier.align(AbsoluteAlignment.CenterLeft).padding(horizontal = 12.dp),
    )
    LoreSwipeActionBadge(
        swipeState = swipeState,
        exitFade = exitFade,
        thresholdPx = thresholdPx,
        direction = SwipeDirection.Right,
        accentColor = accentColor,
        modifier = Modifier.align(AbsoluteAlignment.CenterRight).padding(horizontal = 12.dp),
    )
}

@Composable
private fun LoreSwipeActionBadge(
    swipeState: SwipeableCardState,
    exitFade: Animatable<Float, *>,
    thresholdPx: Float,
    direction: SwipeDirection,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val isSkip = direction == SwipeDirection.Left
    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer {
                val motion = loreSwipeCueMotion(
                    offsetX = swipeState.offset.value.x,
                    releaseOffsetX = swipeState.releaseOffsetX,
                    exiting = swipeState.phase == LoreSwipePhase.Exiting,
                    exitFade = exitFade.value,
                    threshold = thresholdPx,
                    direction = direction,
                )
                alpha = motion.alpha
                scaleX = motion.scale
                scaleY = motion.scale
                translationX = motion.translationDp * density
                transformOrigin = TransformOrigin(if (isSkip) 0f else 1f, 0.5f)
            }
            // Visual gesture feedback; the existing action buttons provide accessible actions.
            .clearAndSetSemantics {}
            .clip(CircleShape)
            .background(lerp(accentColor, Color.Black, 0.58f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isSkip) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(36.dp),
        )
    }
}
