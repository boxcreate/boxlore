package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/**
 * Places a menu or popup the way Material places a dropdown menu (beside its anchor, flipping above
 * it when there is no room below), but inside the fold-safe region that holds the anchor instead of
 * the whole window. On a half-open fold a menu opened from the right half stays in the right half
 * and never crosses the hinge. With one region it places exactly as Material does.
 *
 * Pair it with [boxloreMaxHeightInRegion] on the popup's content, so a long menu scrolls inside its
 * region instead of running past the hinge.
 */
@Stable
class BoxloreFoldSafePopupPositionProvider internal constructor(
    private val window: BoxloreWindowLayout,
    private val density: Density,
    private val offset: DpOffset,
    private val preferEndAligned: Boolean,
) : PopupPositionProvider {
    /** The height in px of the region the popup last opened in; [Int.MAX_VALUE] until it has opened. */
    var regionHeightPx by mutableIntStateOf(Int.MAX_VALUE)
        private set

    /**
     * The tallest the popup can be in px and still open beside its anchor without covering it: the
     * larger of the space below and above the anchor, inside the region and its margins.
     * [Int.MAX_VALUE] until it has opened.
     */
    var heightBesideAnchorPx by mutableIntStateOf(Int.MAX_VALUE)
        private set

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val region = boxloreFoldSafeRegionPx(window, anchorBounds, windowSize, density)
        if (regionHeightPx != region.height) regionHeightPx = region.height
        return with(density) {
            val margins = popupMargins(region, windowSize, FoldSafePopupVerticalMargin.roundToPx(), FoldSafePopupHingeMargin.roundToPx())
            val offsetY = offset.y.roundToPx()
            val beside = popupHeightBesideAnchor(anchorBounds, region, offsetY, margins)
            if (heightBesideAnchorPx != beside) heightBesideAnchorPx = beside
            placePopupInRegion(
                anchor = anchorBounds,
                region = region,
                layoutDirection = layoutDirection,
                popup = popupContentSize,
                offsetX = offset.x.roundToPx(),
                offsetY = offsetY,
                margins = margins,
                preferEndAligned = preferEndAligned,
            )
        }
    }
}

/**
 * A [BoxloreFoldSafePopupPositionProvider] for the current window and density; a new one when the
 * device folds or unfolds, so an open popup moves with its region. [offset] is applied as Material
 * applies a menu's offset. [preferEndAligned] tries lining the popup's end up with the anchor's end
 * first, for anchors at the end of a top bar or a row.
 */
@Composable
fun rememberBoxloreFoldSafePopupPositionProvider(offset: DpOffset = DpOffset.Zero, preferEndAligned: Boolean = false): BoxloreFoldSafePopupPositionProvider {
    val window = LocalBoxloreWindowLayout.current
    val density = LocalDensity.current
    return remember(window, density, offset, preferEndAligned) {
        BoxloreFoldSafePopupPositionProvider(window, density, offset, preferEndAligned)
    }
}

/**
 * The fold-safe region, in window px, that holds [anchorBounds]: for custom popups that position
 * themselves. It never extends past [windowSize]; without a window model it is the whole window.
 */
fun boxloreFoldSafeRegionPx(window: BoxloreWindowLayout, anchorBounds: IntRect, windowSize: IntSize, density: Density): IntRect {
    val whole = IntRect(0, 0, windowSize.width, windowSize.height)
    if (!window.isSpecified) return whole
    val scale = density.density
    val anchor = BoxloreLayoutBounds(anchorBounds.left / scale, anchorBounds.top / scale, anchorBounds.right / scale, anchorBounds.bottom / scale)
    val region = window.foldSafeRegionContaining(anchor)
    val px = IntRect(
        (region.left * scale).roundToInt(),
        (region.top * scale).roundToInt(),
        (region.right * scale).roundToInt(),
        (region.bottom * scale).roundToInt(),
    ).intersect(whole)
    return if (px.width > 0 && px.height > 0) px else whole
}

/**
 * Caps a popup's height to the fold-safe region it opens in, less Material's menu margins, so a long
 * menu scrolls instead of crossing the hinge. With [provider] it uses the region that provider
 * placed the popup in; without one it uses the shortest region. With one region it changes nothing.
 */
fun Modifier.boxloreMaxHeightInRegion(provider: BoxloreFoldSafePopupPositionProvider? = null): Modifier = composed {
    val window = LocalBoxloreWindowLayout.current
    if (!window.isSpecified || window.foldSafeRegions().size == 1) return@composed this
    val density = LocalDensity.current
    val regionPx = provider?.regionHeightPx?.takeIf { it != Int.MAX_VALUE }
    val regionDp = regionPx?.let { with(density) { it.toDp() } } ?: window.foldSafeRegions().minOf { it.height }.dp
    heightIn(max = (regionDp - FoldSafePopupVerticalMargin * 2).coerceAtLeast(FoldSafePopupMinHeight))
}

/** How far a popup keeps from its region's top and bottom, in px. */
internal data class PopupMargins(val top: Int, val bottom: Int)

/**
 * Material's margin on region edges that are window edges, and the smaller [hingeMargin] on an edge
 * that faces a hinge, so a menu in a tabletop half uses the half's height.
 */
internal fun popupMargins(region: IntRect, windowSize: IntSize, windowMargin: Int, hingeMargin: Int): PopupMargins = PopupMargins(
    top = if (region.top <= 0) windowMargin else hingeMargin,
    bottom = if (region.bottom >= windowSize.height) windowMargin else hingeMargin,
)

/** The larger of the space below and above [anchor] inside [region] and its [margins], at least 0. */
internal fun popupHeightBesideAnchor(anchor: IntRect, region: IntRect, offsetY: Int, margins: PopupMargins): Int {
    val below = region.bottom - margins.bottom - (anchor.bottom + offsetY)
    val above = anchor.top + offsetY - (region.top + margins.top)
    return maxOf(below, above, 0)
}

/**
 * Material's dropdown placement, inside [region] instead of the window: beside the anchor's start
 * (or end), else its end (or start), else against the region's nearer side; below the anchor, else
 * above it, else on the side with more room, so it never covers the anchor while it fits beside it.
 * The result stays inside the region when the popup fits in it.
 */
@Suppress("LongParameterList") // Material's inputs plus the region and the end-first choice.
internal fun placePopupInRegion(
    anchor: IntRect,
    region: IntRect,
    layoutDirection: LayoutDirection,
    popup: IntSize,
    offsetX: Int,
    offsetY: Int,
    margins: PopupMargins,
    preferEndAligned: Boolean,
): IntOffset {
    val ltr = layoutDirection == LayoutDirection.Ltr
    val startToAnchorStart = if (ltr) anchor.left + offsetX else anchor.right - popup.width - offsetX
    val endToAnchorEnd = if (ltr) anchor.right - popup.width + offsetX else anchor.left - offsetX
    val toRegionSide = if (anchor.center.x < region.center.x) region.left else region.right - popup.width
    val xCandidates = if (preferEndAligned) {
        listOf(endToAnchorEnd, startToAnchorStart, toRegionSide)
    } else {
        listOf(startToAnchorStart, endToAnchorEnd, toRegionSide)
    }
    val x = xCandidates.firstOrNull { it >= region.left && it + popup.width <= region.right } ?: xCandidates.last()

    val below = anchor.bottom + offsetY
    val above = anchor.top - popup.height + offsetY
    val roomBelow = region.bottom - margins.bottom - below
    val roomAbove = anchor.top + offsetY - (region.top + margins.top)
    val y = listOf(below, above).firstOrNull { it >= region.top + margins.top && it + popup.height <= region.bottom - margins.bottom }
        ?: if (roomBelow >= roomAbove) below else above
    return IntOffset(
        x.coerceIn(region.left, maxOf(region.left, region.right - popup.width)),
        y.coerceIn(region.top, maxOf(region.top, region.bottom - popup.height)),
    )
}

/** Material's dropdown menu keeps this far from the window's top and bottom edges. */
private val FoldSafePopupVerticalMargin = 48.dp

/** A popup keeps this far from a hinge. */
private val FoldSafePopupHingeMargin = 8.dp

/** A capped popup still shows at least one row. */
private val FoldSafePopupMinHeight = 48.dp
