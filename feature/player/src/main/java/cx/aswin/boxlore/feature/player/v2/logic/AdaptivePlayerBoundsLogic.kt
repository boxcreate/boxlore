package cx.aswin.boxlore.feature.player.v2.logic

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import cx.aswin.boxlore.core.designsystem.component.AppLoreNavigationActionSize
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerContractionProgress
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerDockingProgress

internal data class AdaptivePlayerBoundsInput(
    val containerWidth: Dp,
    val containerHeight: Dp,
    val collapsedHorizontalPadding: Dp,
    val collapsedTargetY: Float,
    val compactTargetY: Float,
    val miniPlayerHeight: Dp,
    val compactFraction: Float,
    val expansionFraction: Float,
    val isRtl: Boolean = false,
    val adaptiveEnabled: Boolean = true,
    val isFullscreenVideo: Boolean = false,
)

internal data class AdaptivePlayerBounds(
    val width: Dp,
    val height: Dp,
    val offsetX: Dp,
    val offsetY: Float,
    val collapsedHeight: Dp,
    val compactFraction: Float,
    val cookieFraction: Float,
)

/** Browse compactness changes visual bounds only; the vertical draggable anchors stay fixed. */
internal fun calculateAdaptivePlayerBounds(input: AdaptivePlayerBoundsInput): AdaptivePlayerBounds {
    val compact = if (input.adaptiveEnabled) input.compactFraction.finiteFraction() else 0f
    // Finish shrinking above the dock before descending into its external action slot.
    // The same path in reverse lifts the circle clear of navigation before widening it.
    val contraction = adaptivePlayerContractionProgress(compact)
    val descent = adaptivePlayerDockingProgress(compact)
    val expansion = if (input.isFullscreenVideo) 1f else input.expansionFraction.finiteFraction()
    val width = input.containerWidth.coerceAtLeast(0.dp)
    val gutter = input.collapsedHorizontalPadding.coerceIn(0.dp, width / 2)
    val normalWidth = (width - gutter * 2).coerceAtLeast(0.dp)
    val circleSize = AppLoreNavigationActionSize.coerceAtMost(normalWidth)
    val circleX = if (input.isRtl) gutter else (width - gutter - circleSize).coerceAtLeast(0.dp)
    val collapsedHeight = lerp(input.miniPlayerHeight, circleSize, contraction)
    val normalY = input.collapsedTargetY.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val compactY = input.compactTargetY.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: normalY
    val collapsedY = lerp(normalY, compactY, descent)
    return AdaptivePlayerBounds(
        width = lerp(lerp(normalWidth, circleSize, contraction), width, expansion),
        height = lerp(collapsedHeight, input.containerHeight.coerceAtLeast(0.dp), expansion),
        offsetX = lerp(lerp(gutter, circleX, contraction), 0.dp, expansion),
        offsetY = lerp(collapsedY, 0f, expansion),
        collapsedHeight = collapsedHeight,
        compactFraction = contraction,
        // Restore regular sheet clipping before full-player content starts fading in.
        cookieFraction = descent * (1f - expansion / 0.06f).coerceAtLeast(0f),
    )
}

private fun Float.finiteFraction(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f

/** Both regular corner radii approach the compact radius; fullscreen video stays square. */
internal fun calculateAdaptivePlayerCornerRadius(
    regularRadius: Dp,
    expansionFraction: Float,
    compactFraction: Float,
    isFullscreenVideo: Boolean,
): Dp {
    if (isFullscreenVideo) return 0.dp
    return lerp(regularRadius, AppLoreNavigationActionSize / 2 * (1f - expansionFraction), compactFraction)
}

internal fun isPlayerSheetInteractionActive(
    expansionFraction: Float,
    animationRunning: Boolean,
    isDragging: Boolean,
    isFullscreenVideo: Boolean,
): Boolean = expansionFraction > 0.001f || animationRunning || isDragging || isFullscreenVideo
