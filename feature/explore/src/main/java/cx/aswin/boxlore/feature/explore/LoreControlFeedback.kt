package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

internal const val LoreControlPressedScale = 0.97f
internal val LoreControlPressAnimation = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)

/** Small, bounded feedback for controls; the card itself only follows the swipe. */
@Composable
internal fun Modifier.loreControlClickable(enabled: Boolean, shape: Shape, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) LoreControlPressedScale else 1f,
        animationSpec = LoreControlPressAnimation,
        label = "LoreControlPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            clip = true
            this.shape = shape
        }
        .clickable(interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
}
