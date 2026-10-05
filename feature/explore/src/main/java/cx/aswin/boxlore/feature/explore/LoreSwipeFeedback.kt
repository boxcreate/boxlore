package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

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

@Composable
internal fun BoxScope.LoreSwipeFeedback(swipeState: SwipeableCardState, thresholdPx: Float, accentColor: Color) {
    LoreSwipeActionBadge(
        swipeState = swipeState,
        thresholdPx = thresholdPx,
        direction = SwipeDirection.Left,
        accentColor = accentColor,
        modifier = Modifier.align(AbsoluteAlignment.CenterLeft).padding(horizontal = 12.dp),
    )
    LoreSwipeActionBadge(
        swipeState = swipeState,
        thresholdPx = thresholdPx,
        direction = SwipeDirection.Right,
        accentColor = accentColor,
        modifier = Modifier.align(AbsoluteAlignment.CenterRight).padding(horizontal = 12.dp),
    )
}

@Composable
private fun LoreSwipeActionBadge(
    swipeState: SwipeableCardState,
    thresholdPx: Float,
    direction: SwipeDirection,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val isSkip = direction == SwipeDirection.Left
    val shape = RoundedCornerShape(24.dp)
    val contentColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .graphicsLayer {
                val motion = loreSwipeFeedbackMotion(swipeState.offset.value.x, thresholdPx, direction)
                alpha = motion.alpha
                scaleX = motion.scale
                scaleY = motion.scale
                translationX = motion.translationDp * density
                transformOrigin = TransformOrigin(if (isSkip) 0f else 1f, 0.5f)
            }
            // Visual gesture feedback; the existing action buttons provide accessible actions.
            .clearAndSetSemantics {}
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)), shape),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 80.dp, max = 144.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (isSkip) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(if (isSkip) R.string.lore_action_skip else R.string.lore_action_add_to_queue),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = GoogleSansWeight.medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
