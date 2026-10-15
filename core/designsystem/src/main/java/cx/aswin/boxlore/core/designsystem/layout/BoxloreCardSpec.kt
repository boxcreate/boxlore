package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

enum class BoxloreCardFamily { Hero, Poster, Episode, RailItem, Reading, Utility }

@Immutable
data class BoxloreCardSpec(
    val minimumWidthDp: Float,
    val maximumWidthDp: Float,
    val artworkAspectRatio: Float?,
    val preserveCallerGeometry: Boolean,
)

/** Compact cards retain the caller's complete constraints, including existing fixed rail sizes. */
fun boxloreCardSpec(layout: BoxloreWindowLayout, family: BoxloreCardFamily): BoxloreCardSpec {
    if (!layout.usesTabletLayout) return BoxloreCardSpec(0f, Float.POSITIVE_INFINITY, null, true)
    return when (family) {
        BoxloreCardFamily.Hero -> BoxloreCardSpec(280f, 480f, null, false)
        BoxloreCardFamily.Poster -> BoxloreCardSpec(140f, 208f, 1f, false)
        BoxloreCardFamily.Episode -> BoxloreCardSpec(280f, 720f, null, false)
        BoxloreCardFamily.RailItem -> BoxloreCardSpec(140f, 310f, null, false)
        BoxloreCardFamily.Reading -> BoxloreCardSpec(280f, 720f, null, false)
        BoxloreCardFamily.Utility -> BoxloreCardSpec(280f, 720f, null, false)
    }
}

/** Minimums guide packing, never force content wider than a narrow pane. */
fun boxlorePosterColumns(
    layout: BoxloreWindowLayout,
    paneWidthDp: Float,
    compactColumns: Int = 2,
    horizontalPaddingDp: Float = 32f,
    gapDp: Float = 16f,
): Int {
    if (!layout.usesTabletLayout) return compactColumns
    val available = (paneWidthDp - horizontalPaddingDp).coerceAtLeast(0f)
    val poster = boxloreCardSpec(layout, BoxloreCardFamily.Poster)
    return floor((available + gapDp) / (poster.minimumWidthDp + gapDp)).toInt().coerceAtLeast(1)
}

/**
 * Bounds the surface inside its original slot. Placing this before a card's paint/clip modifiers
 * also works in tight fill-width parents: the child receives loosened, capped constraints.
 * The slot keeps its original extent, so connected list and grid alignment remain predictable.
 */
@Composable
fun Modifier.boxloreCardWidth(family: BoxloreCardFamily): Modifier {
    val spec = boxloreCardSpec(LocalBoxloreWindowLayout.current, family)
    if (spec.preserveCallerGeometry) return this
    return layout { measurable, constraints ->
        val maximum = min(constraints.maxWidth, (spec.maximumWidthDp * density).roundToInt())
        val child = measurable.measure(constraints.copy(minWidth = min(constraints.minWidth, maximum), maxWidth = maximum))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.constrainWidth(child.width)
        layout(width, child.height) { child.placeRelative((width - child.width) / 2, 0) }
    }
}

/** The app shell supplies actual content-pane width, including after a split or rail is applied. */
@Composable
fun boxloreContentWidthDp(): Float {
    val paneWidth = LocalBoxlorePaneWidth.current
    val window = LocalBoxloreWindowLayout.current
    return if (paneWidth > 0f) paneWidth else window.widthDp
}
