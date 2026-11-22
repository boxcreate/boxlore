package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class BoxloreWindowWidth { Compact, Medium, Expanded, Large }

enum class BoxloreWindowHeight { Compact, Medium, Expanded }

enum class BoxloreWindowOrientation { Portrait, Landscape }

enum class BoxloreFoldOrientation { Horizontal, Vertical }

enum class BoxloreFoldState { Flat, HalfOpened }

/** Window-relative dp coordinates, including the physical hinge's occluded area. */
@Immutable
data class BoxloreLayoutBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

@Immutable
data class BoxloreFold(
    val state: BoxloreFoldState,
    val orientation: BoxloreFoldOrientation,
    val bounds: BoxloreLayoutBounds,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
)

/** Owned by the app shell. Reading this model never restarts a data/ranking producer. */
@Immutable
data class BoxloreWindowLayout(
    val widthDp: Float,
    val heightDp: Float,
    val folds: List<BoxloreFold> = emptyList(),
    // Retain configuration-based phone geometry where it differs from edge-to-edge window bounds.
    val configurationWidthDp: Int = widthDp.toInt(),
    val configurationHeightDp: Int = heightDp.toInt(),
) {
    init {
        require(widthDp.isFinite() && widthDp >= 0f)
        require(heightDp.isFinite() && heightDp >= 0f)
    }

    val widthClass: BoxloreWindowWidth
        get() = when {
            widthDp >= 1_200f -> BoxloreWindowWidth.Large
            widthDp >= 840f -> BoxloreWindowWidth.Expanded
            widthDp >= 600f -> BoxloreWindowWidth.Medium
            else -> BoxloreWindowWidth.Compact
        }

    val heightClass: BoxloreWindowHeight
        get() = when {
            heightDp >= 900f -> BoxloreWindowHeight.Expanded
            heightDp >= 480f -> BoxloreWindowHeight.Medium
            else -> BoxloreWindowHeight.Compact
        }

    val orientation: BoxloreWindowOrientation
        get() = if (widthDp > heightDp) BoxloreWindowOrientation.Landscape else BoxloreWindowOrientation.Portrait

    val isSpecified: Boolean get() = widthDp > 0f && heightDp > 0f
    val usesTabletLayout: Boolean get() = widthDp >= 600f && heightDp >= 480f
    val usesSplitLayout: Boolean get() = usesTabletLayout && widthDp >= 840f && widthDp > heightDp
    val tabletopFold: BoxloreFold?
        get() = folds.firstOrNull {
            it.state == BoxloreFoldState.HalfOpened &&
                it.orientation == BoxloreFoldOrientation.Horizontal &&
                it.bounds.top > 0f &&
                it.bounds.bottom < heightDp
        }

    /**
     * A vertical hinge splits this window into two regions at least 360dp wide, so a detail or the
     * player can open in the second half, whatever the aspect ratio (an upright book foldable too).
     */
    val usesHingeSplit: Boolean
        get() {
            if (!usesTabletLayout || tabletopFold != null) return false
            val vertical = folds.any { it.orientation == BoxloreFoldOrientation.Vertical && it.splitsRegions }
            val regions = foldSafeRegions()
            return vertical && regions.size == 2 && regions.all { it.width >= HINGE_SPLIT_MIN_REGION_DP }
        }

    companion object {
        /** Previews and isolated feature tests retain their existing phone layout by default. */
        val Unspecified = BoxloreWindowLayout(0f, 0f)
    }
}

val LocalBoxloreWindowLayout = staticCompositionLocalOf { BoxloreWindowLayout.Unspecified }

private const val HINGE_SPLIT_MIN_REGION_DP = 360f

private val BoxloreFold.splitsRegions: Boolean get() = isSeparating || isOccluding || state == BoxloreFoldState.HalfOpened

/** Contiguous regions are used for pane placement; no region includes an occluding hinge. */
fun BoxloreWindowLayout.foldSafeRegions(): List<BoxloreLayoutBounds> =
    folds.filter { it.splitsRegions }
        .fold(listOf(BoxloreLayoutBounds(0f, 0f, widthDp, heightDp))) { regions, fold ->
        regions.flatMap { region -> splitAtFold(region, fold) }
    }

/**
 * The one region a screen that must stay in a single half uses: the lower half in tabletop (the
 * half by the listener's hands), otherwise the widest region. With one region this is the window.
 */
fun BoxloreWindowLayout.foldSafeBrowsingRegion(): BoxloreLayoutBounds {
    val regions = foldSafeRegions()
    return if (tabletopFold != null) regions.last() else regions.maxBy { it.width }
}

private fun splitAtFold(region: BoxloreLayoutBounds, fold: BoxloreFold): List<BoxloreLayoutBounds> {
    val bounds = fold.bounds
    return when {
        fold.orientation == BoxloreFoldOrientation.Horizontal && bounds.top > region.top && bounds.bottom < region.bottom ->
            listOf(region.copy(bottom = bounds.top), region.copy(top = bounds.bottom))
        fold.orientation == BoxloreFoldOrientation.Vertical && bounds.left > region.left && bounds.right < region.right ->
            listOf(region.copy(right = bounds.left), region.copy(left = bounds.right))
        else -> listOf(region)
    }
}

/**
 * The fold-safe region that overlaps [bounds] most, such as the half a pane, an anchor or a panel
 * sits in. Bounds that overlap no region (inside the hinge, or with no area) use the region under
 * their centre, then the first region. With one region this is the whole window.
 */
fun BoxloreWindowLayout.foldSafeRegionContaining(bounds: BoxloreLayoutBounds): BoxloreLayoutBounds {
    val regions = foldSafeRegions()
    val best = regions.maxBy { overlapArea(it, bounds) }
    if (overlapArea(best, bounds) > 0f) return best
    val centreX = (bounds.left + bounds.right) / 2f
    val centreY = (bounds.top + bounds.bottom) / 2f
    return regions.firstOrNull { it.contains(centreX, centreY) } ?: regions.first()
}

/** The fold-safe region under a window point, or the nearest one when the point is on the hinge. */
fun BoxloreWindowLayout.foldSafeRegionAt(xDp: Float, yDp: Float): BoxloreLayoutBounds {
    val regions = foldSafeRegions()
    return regions.firstOrNull { it.contains(xDp, yDp) } ?: regions.minBy { it.distanceTo(xDp, yDp) }
}

private fun BoxloreLayoutBounds.contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

private fun BoxloreLayoutBounds.distanceTo(x: Float, y: Float): Float {
    val dx = maxOf(left - x, 0f, x - right)
    val dy = maxOf(top - y, 0f, y - bottom)
    return dx * dx + dy * dy
}

private fun overlapArea(a: BoxloreLayoutBounds, b: BoxloreLayoutBounds): Float {
    val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
    val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    return if (width > 0f && height > 0f) width * height else 0f
}
