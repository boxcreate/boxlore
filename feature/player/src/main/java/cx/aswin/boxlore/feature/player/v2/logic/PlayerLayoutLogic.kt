package cx.aswin.boxlore.feature.player.v2.logic

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import cx.aswin.boxlore.feature.player.v2.HeroDimensions

internal data class ResponsiveHeroLayout(
    val isCompact: Boolean,
    val dimensions: HeroDimensions
)

internal fun calculateResponsiveHeroLayout(
    maxWidth: Dp,
    maxHeight: Dp,
    isVideo: Boolean
): ResponsiveHeroLayout {
    val isCompact = maxHeight < 620.dp
    val availableHeight = maxHeight * (if (isCompact) 0.28f else 0.34f)
    if (!isVideo) {
        val size = min(maxWidth * 0.68f, availableHeight).coerceAtLeast(138.dp)
        return ResponsiveHeroLayout(isCompact, HeroDimensions(size, size))
    }
    val targetWidth = maxWidth * 0.95f - 48.dp
    val targetHeight = targetWidth * (9f / 16f)
    val dimensions = if (targetHeight > availableHeight) {
        HeroDimensions(availableHeight * (16f / 9f), availableHeight)
    } else {
        HeroDimensions(targetWidth, targetHeight)
    }
    return ResponsiveHeroLayout(isCompact, dimensions)
}

internal data class PlayerSheetGeometryValues(
    val expansionFraction: Float,
    val miniPlayerHeight: Dp,
    val sheetHeight: Dp,
    val topCornerRadius: Dp,
    val bottomCornerRadius: Dp,
    val horizontalPadding: Dp,
    val sheetElevation: Dp,
    val miniAlpha: Float,
    val fullAlpha: Float,
    val fullTranslationY: Float
)

internal data class PlayerSheetGeometryInput(
    val sheetOffset: Float,
    val collapsedTargetY: Float,
    val containerHeight: Dp,
    val collapsedHorizontalPadding: Dp,
    val fullEntranceOffsetPx: Float,
    val miniPlayerHeight: Dp = 64.dp,
    val collapsedTopCornerRadius: Dp = 32.dp,
    val collapsedBottomCornerRadius: Dp = 32.dp,
)

internal fun calculatePlayerSheetGeometry(
    input: PlayerSheetGeometryInput,
): PlayerSheetGeometryValues {
    val expansionFraction = if (input.collapsedTargetY <= 0f) {
        0f
    } else {
        (1f - input.sheetOffset / input.collapsedTargetY).coerceIn(0f, 1f)
    }
    val fullAlpha = ((expansionFraction - 0.25f).coerceIn(0f, 0.75f) / 0.75f)
    return PlayerSheetGeometryValues(
        expansionFraction = expansionFraction,
        miniPlayerHeight = input.miniPlayerHeight,
        sheetHeight = lerp(input.miniPlayerHeight, input.containerHeight, expansionFraction),
        topCornerRadius = lerp(input.collapsedTopCornerRadius, 0.dp, expansionFraction),
        bottomCornerRadius = lerp(input.collapsedBottomCornerRadius, 0.dp, expansionFraction),
        horizontalPadding = lerp(input.collapsedHorizontalPadding, 0.dp, expansionFraction),
        sheetElevation = lerp(3.dp, 16.dp, expansionFraction),
        miniAlpha = (1f - expansionFraction * 2f).coerceIn(0f, 1f),
        fullAlpha = fullAlpha,
        fullTranslationY = lerp(input.fullEntranceOffsetPx, 0f, fullAlpha)
    )
}
