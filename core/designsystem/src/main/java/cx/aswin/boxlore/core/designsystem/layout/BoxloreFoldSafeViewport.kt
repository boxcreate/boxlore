package cx.aswin.boxlore.core.designsystem.layout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Keeps portal/overlay content in one contiguous region. Inside a pane that sits in one half (the
 * context pane, a tabletop half) that is the half the pane is in, so a panel opened from the right
 * half opens in the right half. A pane that spans the hinge, such as browsing on a half-open fold
 * with nothing open, uses the region single-half screens use ([foldSafeBrowsingRegion]). With one
 * region it changes nothing. The region is in window coordinates, so content placed inside a pane
 * (beside the rail, below a hinge) gets the part of the region inside its own bounds. Inside the
 * region, system-bar and keyboard insets count only on sides that reach the screen edge.
 */
@Composable
fun Modifier.boxloreFoldSafeViewport(): Modifier {
    val window = LocalBoxloreWindowLayout.current
    val pane = LocalBoxlorePaneBounds.current
    val bounds = foldSafeViewportBounds(window, pane) ?: return this
    val density = LocalDensity.current.density
    // Where this layout starts in the window, in dp. Until it is placed: the pane's corner in the
    // app's own window, the window's corner in a dialog or popup window.
    val view = LocalView.current
    var origin by remember(view) {
        val inAppWindow = view.rootView === view.context.findActivity()?.window?.decorView
        mutableStateOf(if (inAppWindow && pane != null) Offset(pane.left, pane.top) else Offset.Zero)
    }
    return consumeInsetsOutside(bounds, window)
        .onPlaced { coordinates ->
            val placed = coordinates.positionInWindow() / density
            if (abs(placed.x - origin.x) > EDGE_TOLERANCE_DP || abs(placed.y - origin.y) > EDGE_TOLERANCE_DP) origin = placed
        }
        .layout { measurable, constraints ->
            val local = bounds.localTo(origin)
            val left = (local.left * density).roundToInt().coerceIn(0, constraints.maxWidth)
            val top = (local.top * density).roundToInt().coerceIn(0, constraints.maxHeight)
            val right = (local.right * density).roundToInt().coerceIn(left, constraints.maxWidth)
            val bottom = (local.bottom * density).roundToInt().coerceIn(top, constraints.maxHeight)
            val child = measurable.measure(Constraints.fixed(right - left, bottom - top))
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(left, top) }
        }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun BoxloreLayoutBounds.localTo(origin: Offset): BoxloreLayoutBounds =
    BoxloreLayoutBounds(left - origin.x, top - origin.y, right - origin.x, bottom - origin.y)

/** The window-relative region [boxloreFoldSafeViewport] confines content to, or null when it confines nothing. */
internal fun foldSafeViewportBounds(window: BoxloreWindowLayout, paneBounds: BoxloreLayoutBounds?): BoxloreLayoutBounds? {
    if (!window.usesTabletLayout || window.foldSafeRegions().size == 1) return null
    val regions = window.foldSafeRegions()
    if (paneBounds != null && regions.count { it.overlaps(paneBounds) } <= 1) return window.foldSafeRegionContaining(paneBounds)
    return window.foldSafeBrowsingRegion()
}

private fun BoxloreLayoutBounds.overlaps(other: BoxloreLayoutBounds): Boolean =
    minOf(right, other.right) > maxOf(left, other.left) && minOf(bottom, other.bottom) > maxOf(top, other.top)
