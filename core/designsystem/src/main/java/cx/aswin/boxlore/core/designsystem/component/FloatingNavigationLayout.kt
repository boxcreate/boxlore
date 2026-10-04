package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

internal val NavPillContentPadding = 6.dp
internal val CompactNavigationTabSize = 48.dp

internal data class FloatingNavigationLayout(
    val pillWidth: Dp,
    val tabWidth: Dp,
    val loreStart: Dp,
    val loreSize: Dp,
)

internal data class FloatingNavigationIndicatorBounds(
    val start: Dp,
    val width: Dp,
    val height: Dp,
)

/** One selection surface travels between the pill tabs and the same moving Lore action. */
internal fun floatingNavigationIndicatorBounds(
    availableWidth: Dp,
    compactProgress: Float,
    indicatorIndex: Float,
): FloatingNavigationIndicatorBounds {
    val geometry = floatingNavigationLayout(availableWidth, compactProgress)
    val index = if (indicatorIndex.isFinite()) indicatorIndex.coerceIn(0f, 3f) else 0f
    val loreTravel = (index - 2f).coerceIn(0f, 1f)
    val contraction = adaptivePlayerContractionProgress(compactProgress)
    val primaryHeight = AppNavigationBarHeight - NavPillContentPadding * 2
    val primaryCenter = NavPillContentPadding + geometry.tabWidth * (index.coerceAtMost(2f) + 0.5f)
    val loreCenter = geometry.loreStart + geometry.loreSize / 2
    val loreWidth = lerp(geometry.loreSize, geometry.tabWidth, contraction)
    val loreHeight = lerp(geometry.loreSize, primaryHeight, contraction)
    val width = lerp(geometry.tabWidth, loreWidth, loreTravel)
    return FloatingNavigationIndicatorBounds(
        start = lerp(primaryCenter, loreCenter, loreTravel) - width / 2,
        width = width,
        height = lerp(primaryHeight, loreHeight, loreTravel),
    )
}

internal fun boundedNavigationCompactProgress(progress: Float): Float = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

internal fun floatingNavigationTabWidth(pillWidth: Dp, compactProgress: Float): Dp =
    (pillWidth - NavPillContentPadding * 2).coerceAtLeast(0.dp) / (3f + adaptivePlayerContractionProgress(compactProgress))

/** Geometry is relative to the padded chrome, so the trailing player slot stays fixed. */
internal fun floatingNavigationLayout(availableWidth: Dp, compactProgress: Float): FloatingNavigationLayout {
    val progress = adaptivePlayerContractionProgress(compactProgress)
    val pillWidth = (availableWidth - AppLoreNavigationActionSize - AppFloatingNavigationActionGap).coerceAtLeast(0.dp)
    val tabWidth = floatingNavigationTabWidth(pillWidth, compactProgress)
    val compactLoreStart = pillWidth - NavPillContentPadding - tabWidth / 2 - CompactNavigationTabSize / 2
    val restingLoreStart = pillWidth + AppFloatingNavigationActionGap
    return FloatingNavigationLayout(
        pillWidth = pillWidth,
        tabWidth = tabWidth,
        loreStart = restingLoreStart + (compactLoreStart - restingLoreStart) * progress,
        loreSize = AppLoreNavigationActionSize + (CompactNavigationTabSize - AppLoreNavigationActionSize) * progress,
    )
}
