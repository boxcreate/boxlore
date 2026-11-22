package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Keeps portal/overlay content in one contiguous region. Inside a pane that sits in one half (the
 * context pane, a tabletop half) that is the half the pane is in, so a panel opened from the right
 * half opens in the right half. A pane that spans the hinge, such as browsing on a half-open fold
 * with nothing open, uses the region single-half screens use ([foldSafeBrowsingRegion]). With one
 * region it changes nothing. Inside the region, system-bar insets count only on sides that reach the
 * screen edge.
 */
@Composable
fun Modifier.boxloreFoldSafeViewport(): Modifier {
    val window = LocalBoxloreWindowLayout.current
    val bounds = foldSafeViewportBounds(window, LocalBoxlorePaneBounds.current) ?: return this
    // System bars belong to the screen edges: a region below the top of the screen has no status bar to clear.
    val sides = listOfNotNull(
        WindowInsetsSides.Top.takeIf { bounds.top > EDGE_TOLERANCE_DP },
        WindowInsetsSides.Left.takeIf { bounds.left > EDGE_TOLERANCE_DP },
        WindowInsetsSides.Right.takeIf { bounds.right < window.widthDp - EDGE_TOLERANCE_DP },
        WindowInsetsSides.Bottom.takeIf { bounds.bottom < window.heightDp - EDGE_TOLERANCE_DP },
    ).reduceOrNull { all, side -> all + side }
    val consumed = if (sides != null) consumeWindowInsets(WindowInsets.safeDrawing.only(sides)) else this
    return consumed.layout { measurable, constraints ->
        val left = (bounds.left * density).roundToInt().coerceIn(0, constraints.maxWidth)
        val top = (bounds.top * density).roundToInt().coerceIn(0, constraints.maxHeight)
        val width = (bounds.width * density).roundToInt().coerceIn(0, constraints.maxWidth - left)
        val height = (bounds.height * density).roundToInt().coerceIn(0, constraints.maxHeight - top)
        val child = measurable.measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) { child.place(left, top) }
    }
}

/** The window-relative region [boxloreFoldSafeViewport] confines content to, or null when it confines nothing. */
internal fun foldSafeViewportBounds(window: BoxloreWindowLayout, paneBounds: BoxloreLayoutBounds?): BoxloreLayoutBounds? {
    if (!window.usesTabletLayout || window.foldSafeRegions().size == 1) return null
    val regions = window.foldSafeRegions()
    if (paneBounds != null && regions.count { it.overlaps(paneBounds) } <= 1) return window.foldSafeRegionContaining(paneBounds)
    return window.foldSafeBrowsingRegion()
}

private fun BoxloreLayoutBounds.overlaps(other: BoxloreLayoutBounds): Boolean =
    minOf(right, other.right) > maxOf(left, other.left) && minOf(bottom, other.bottom) > maxOf(top, other.top)
