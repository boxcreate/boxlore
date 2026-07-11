package cx.aswin.boxcast.feature.player.v2.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import kotlin.math.max

@Stable
data class PlayerSheetVisualState(
    val expansionFraction: Float,
    val horizontalPaddingCollapsed: Dp,
    val containerHeight: Dp,
) {
    val playerContentAreaHeight: Dp
        get() = lerp(
            PlayerChromeGeometry.MiniPlayerHeight,
            containerHeight,
            expansionFraction,
        )

    val topCornerRadius: Dp
        get() = lerp(
            PlayerChromeGeometry.MiniPlayerCollapsedCorner,
            PlayerChromeGeometry.SheetTopCornerExpanded,
            expansionFraction,
        )

    val bottomCornerRadius: Dp
        get() = lerp(
            PlayerChromeGeometry.MiniPlayerDockedBottomCorner,
            PlayerChromeGeometry.SheetTopCornerExpanded,
            expansionFraction,
        )

    val horizontalPadding: Dp
        get() = lerp(horizontalPaddingCollapsed, 0.dp, expansionFraction)

    val elevation: Dp
        get() = lerp(3.dp, 16.dp, expansionFraction)

    val miniAlpha: Float
        get() = (1f - expansionFraction * 2f).coerceIn(0f, 1f)

    val fullPlayerAlpha: Float
        get() = ((expansionFraction - 0.25f).coerceIn(0f, 0.75f) / 0.75f)

    val fullPlayerTranslationY: Float
        get() = lerp(24f, 0f, fullPlayerAlpha)
}

@Composable
fun rememberPlayerSheetVisualState(
    expansionFraction: Float,
    horizontalPaddingCollapsed: Dp = PlayerChromeGeometry.MiniPlayerHorizontalInset,
    containerHeight: Dp,
): PlayerSheetVisualState {
    val state by remember(expansionFraction, horizontalPaddingCollapsed, containerHeight) {
        derivedStateOf {
            PlayerSheetVisualState(
                expansionFraction = expansionFraction,
                horizontalPaddingCollapsed = horizontalPaddingCollapsed,
                containerHeight = containerHeight,
            )
        }
    }
    return state
}

private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction).dp

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
