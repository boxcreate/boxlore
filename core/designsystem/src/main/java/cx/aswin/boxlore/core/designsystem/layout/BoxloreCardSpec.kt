package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** [Grid] bounds whole grid screens (and their top bars) on tablets; it has no effect on compact windows. */
enum class BoxloreCardFamily { Hero, Poster, Episode, RailItem, Reading, Utility, Grid }

@Immutable
data class BoxloreCardSpec(
    val minimumWidthDp: Float,
    val maximumWidthDp: Float,
    val artworkAspectRatio: Float?,
    val preserveCallerGeometry: Boolean,
)

private val PreserveCallerGeometry = BoxloreCardSpec(0f, Float.POSITIVE_INFINITY, null, true)
private val PosterSpec = BoxloreCardSpec(140f, 208f, 1f, false)

/**
 * Compact cards retain the caller's complete constraints, including existing fixed rail sizes. The
 * one exception is a poster in a short compact window (a sideways phone, a split-screen half): it
 * keeps the tablet poster bounds, so a poster is never taller than about half the window.
 */
fun boxloreCardSpec(layout: BoxloreWindowLayout, family: BoxloreCardFamily): BoxloreCardSpec {
    if (!layout.usesTabletLayout) {
        val shortWindow = layout.isSpecified && layout.heightDp < SHORT_COMPACT_HEIGHT_DP
        return if (family == BoxloreCardFamily.Poster && shortWindow) PosterSpec else PreserveCallerGeometry
    }
    return when (family) {
        BoxloreCardFamily.Hero -> BoxloreCardSpec(280f, 480f, null, false)
        BoxloreCardFamily.Poster -> PosterSpec
        BoxloreCardFamily.Episode -> BoxloreCardSpec(280f, 720f, null, false)
        BoxloreCardFamily.RailItem -> BoxloreCardSpec(140f, 310f, null, false)
        BoxloreCardFamily.Reading -> BoxloreCardSpec(280f, 720f, null, false)
        BoxloreCardFamily.Utility -> BoxloreCardSpec(280f, 720f, null, false)
        BoxloreCardFamily.Grid -> BoxloreCardSpec(280f, 1_040f, null, false)
    }
}

/**
 * How many posters fit side by side in the pane. Minimums guide packing and never force content
 * wider than a narrow pane. Portrait phones up to 430dp and the Galaxy Z Fold 8 cover keep
 * [compactColumns]; wider compact windows (sideways phones, wide split halves) fit as many as the
 * tablet rule does, never fewer than [compactColumns].
 */
fun boxlorePosterColumns(
    layout: BoxloreWindowLayout,
    paneWidthDp: Float,
    compactColumns: Int = 2,
    horizontalPaddingDp: Float = 32f,
    gapDp: Float = 16f,
): Int {
    val packed = {
        val available = (paneWidthDp - horizontalPaddingDp).coerceAtLeast(0f)
        floor((available + gapDp) / (PosterSpec.minimumWidthDp + gapDp)).toInt().coerceAtLeast(1)
    }
    if (layout.usesTabletLayout) return packed()
    if (paneWidthDp <= COMPACT_PHONE_MAX_WIDTH_DP) return compactColumns
    return max(compactColumns, packed())
}

/** Compact windows this wide or narrower keep their phone layout: every portrait phone. */
internal const val COMPACT_PHONE_MAX_WIDTH_DP = 430f

/** Compact windows shorter than this are sideways phones or split halves (the tablet height gate). */
private const val SHORT_COMPACT_HEIGHT_DP = 480f

/**
 * Caps text-led content at a readable width, centred, on every window including portrait phones
 * (narrower panes keep their full width). [boxloreCardWidth] does nothing on compact windows; use
 * this where a page needs the cap there too.
 */
fun Modifier.boxloreReadingColumn(maxWidthDp: Float = 720f): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidthDp.dp).fillMaxWidth()

/**
 * Bounds the surface inside its original slot. Placing this before a card's paint/clip modifiers
 * also works in tight fill-width parents: the child receives loosened, capped constraints.
 * The slot keeps its original extent, so connected list and grid alignment remain predictable.
 */
@Composable
fun Modifier.boxloreCardWidth(family: BoxloreCardFamily, maximumWidthDp: Float? = null): Modifier {
    val spec = boxloreCardSpec(LocalBoxloreWindowLayout.current, family)
    if (spec.preserveCallerGeometry) return this
    return layout { measurable, constraints ->
        val maximum = min(constraints.maxWidth, (min(spec.maximumWidthDp, maximumWidthDp ?: spec.maximumWidthDp) * density).roundToInt())
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
