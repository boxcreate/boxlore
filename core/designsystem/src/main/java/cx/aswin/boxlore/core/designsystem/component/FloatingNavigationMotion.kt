package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

internal val FloatingNavigationIndicatorMotion = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)

/** Lore's colours follow the shared indicator, including interrupted tab changes. */
internal fun floatingNavigationLoreAuroraBlend(indicatorIndex: Float): Float {
    val progress = if (indicatorIndex.isFinite()) (indicatorIndex - 2f).coerceIn(0f, 1f) else 0f
    return progress * progress * (3f - 2f * progress)
}

/** A small press response preserves the navigation action's measured touch target. */
@Composable
internal fun rememberNavigationPressScale(interactionSource: MutableInteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f),
        label = "navigationPressScale",
    )
}
