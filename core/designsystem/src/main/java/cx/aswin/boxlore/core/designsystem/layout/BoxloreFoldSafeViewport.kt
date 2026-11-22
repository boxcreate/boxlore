package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Keeps portal/overlay content in one contiguous region. Inside a pane the shell supplied bounds
 * for (the context pane, the player), that is the region the pane is in, so a panel opened from the
 * right half opens in the right half; elsewhere it is the region hidden-navigation shell routes use.
 * With one region it changes nothing.
 */
@Composable
fun Modifier.boxloreFoldSafeViewport(): Modifier {
    val bounds = foldSafeViewportBounds(LocalBoxloreWindowLayout.current, LocalBoxlorePaneBounds.current) ?: return this
    return layout { measurable, constraints ->
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
    if (paneBounds != null) return window.foldSafeRegionContaining(paneBounds)
    val spec = boxlorePaneSpec(window, false)
    return spec.primary.copy(left = spec.primary.left - spec.railWidthDp)
}
