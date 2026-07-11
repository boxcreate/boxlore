package cx.aswin.boxcast.feature.player.v2.logic

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import cx.aswin.boxcast.feature.player.v2.HeroDimensions
import cx.aswin.boxcast.feature.player.v2.MiniPlayerHeight

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
    val sheetHeight: Dp,
    val topCornerRadius: Dp,
    val bottomCornerRadius: Dp,
    val horizontalPadding: Dp,
    val sheetElevation: Dp,
    val miniAlpha: Float,
    val fullAlpha: Float,
    val fullTranslationY: Float
)

internal fun calculatePlayerSheetGeometry(
    sheetOffset: Float,
    collapsedTargetY: Float,
    containerHeight: Dp,
    collapsedHorizontalPadding: Dp,
    fullEntranceOffsetPx: Float
): PlayerSheetGeometryValues {
    val expansionFraction = if (collapsedTargetY <= 0f) {
        0f
    } else {
        (1f - sheetOffset / collapsedTargetY).coerceIn(0f, 1f)
    }
    val fullAlpha = ((expansionFraction - 0.25f).coerceIn(0f, 0.75f) / 0.75f)
    return PlayerSheetGeometryValues(
        expansionFraction = expansionFraction,
        sheetHeight = lerp(MiniPlayerHeight, containerHeight, expansionFraction),
        topCornerRadius = lerp(26.dp, 0.dp, expansionFraction),
        bottomCornerRadius = lerp(14.dp, 0.dp, expansionFraction),
        horizontalPadding = lerp(collapsedHorizontalPadding, 0.dp, expansionFraction),
        sheetElevation = lerp(3.dp, 16.dp, expansionFraction),
        miniAlpha = (1f - expansionFraction * 2f).coerceIn(0f, 1f),
        fullAlpha = fullAlpha,
        fullTranslationY = lerp(fullEntranceOffsetPx, 0f, fullAlpha)
    )
}
