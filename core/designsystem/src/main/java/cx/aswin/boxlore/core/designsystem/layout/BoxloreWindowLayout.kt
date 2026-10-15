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

    companion object {
        /** Previews and isolated feature tests retain their existing phone layout by default. */
        val Unspecified = BoxloreWindowLayout(0f, 0f)
    }
}

val LocalBoxloreWindowLayout = staticCompositionLocalOf { BoxloreWindowLayout.Unspecified }

/** Contiguous regions are used for pane placement; no region includes an occluding hinge. */
fun BoxloreWindowLayout.foldSafeRegions(): List<BoxloreLayoutBounds> =
    folds.filter { it.isSeparating || it.isOccluding || it.state == BoxloreFoldState.HalfOpened }
        .fold(listOf(BoxloreLayoutBounds(0f, 0f, widthDp, heightDp))) { regions, fold ->
        regions.flatMap { region -> splitAtFold(region, fold) }
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
