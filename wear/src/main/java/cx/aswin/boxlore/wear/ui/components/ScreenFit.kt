package cx.aswin.boxlore.wear.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

/**
 * The screens are drawn for a 225dp watch. A smaller watch gets the same layout scaled down, and
 * below [COMPACT_BELOW] the screens also drop what no longer fits (a second title line, tile
 * labels). Nothing is ever scaled up.
 */
internal object ScreenFit {
    const val DESIGN_DP = 225f
    const val SMALLEST = 0.8f
    const val COMPACT_BELOW = 0.92f

    fun scale(screenDp: Int): Float = (screenDp / DESIGN_DP).coerceIn(SMALLEST, 1f)

    fun isCompact(scale: Float): Boolean = scale < COMPACT_BELOW
}

/** How much this watch's screen shrinks the 225dp design; 1 on a large watch. */
@Composable
internal fun rememberScreenScale(): Float {
    val configuration = LocalConfiguration.current
    return ScreenFit.scale(minOf(configuration.screenWidthDp, configuration.screenHeightDp))
}

internal fun Dp.fit(scale: Float): Dp = this * scale
