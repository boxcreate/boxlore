package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** The app supplies the same clock used by the player and navigation morph. */
val LocalAdaptivePlayerCompactProgress = compositionLocalOf<State<Float>?> { null }

/** Ease each leg to rest so shrinking and docking meet without a velocity jump. */
fun adaptivePlayerContractionProgress(compactProgress: Float): Float =
    smoothNavigationProgress(boundedNavigationCompactProgress(compactProgress) / 0.65f)

/** The player contracts above navigation before descending into its compact slot. */
fun adaptivePlayerDockingProgress(compactProgress: Float): Float =
    smoothNavigationProgress((boundedNavigationCompactProgress(compactProgress) - 0.65f) / 0.35f)

internal fun smoothNavigationProgress(progress: Float): Float {
    val fraction = boundedNavigationCompactProgress(progress)
    return fraction * fraction * (3f - 2f * fraction)
}

/** Space released above navigation as the compact player docks alongside it. */
fun appBottomChromeOverlayOffset(
    style: NavigationStyle,
    isMiniPlayerVisible: Boolean,
    compactProgress: Float,
): Dp {
    if (style != NavigationStyle.Floating || !isMiniPlayerVisible) return 0.dp
    val metrics = navigationChromeMetrics(style)
    return (metrics.miniPlayerHeight + metrics.miniPlayerNavigationGap) *
        adaptivePlayerDockingProgress(compactProgress)
}

/** Move only the overlay's placement; preserve list measurement and scroll position. */
@Composable
fun Modifier.adaptivePlayerOverlayOffset(isMiniPlayerVisible: Boolean): Modifier {
    val style = LocalNavigationStyle.current
    val progress = LocalAdaptivePlayerCompactProgress.current
    if (style != NavigationStyle.Floating || !isMiniPlayerVisible || progress == null) return this
    return offset {
        IntOffset(
            x = 0,
            y = appBottomChromeOverlayOffset(style, isMiniPlayerVisible, progress.value).roundToPx(),
        )
    }
}
