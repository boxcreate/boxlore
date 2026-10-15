package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class BoxloreNavigationLayout { BottomBar, Rail }

@Immutable
data class BoxlorePaneSpec(
    val navigation: BoxloreNavigationLayout,
    val railWidthDp: Float,
    val primary: BoxloreLayoutBounds,
    val context: BoxloreLayoutBounds?,
    val gapDp: Float,
)

/** The shell supplies each pane's width; features never infer it from the full window. */
val LocalBoxlorePaneWidth = staticCompositionLocalOf { 0f }

fun boxlorePaneSpec(layout: BoxloreWindowLayout, contextOpen: Boolean): BoxlorePaneSpec {
    val fullWindow = BoxloreLayoutBounds(0f, 0f, layout.widthDp, layout.heightDp)
    if (!layout.usesTabletLayout) {
        return BoxlorePaneSpec(BoxloreNavigationLayout.BottomBar, 0f, fullWindow, null, 0f)
    }
    val regions = layout.foldSafeRegions()
    val browsingRegion = if (layout.tabletopFold != null) regions.last() else regions.maxBy { it.width }
    val railWidth = 80f.coerceAtMost(browsingRegion.width)
    val content = browsingRegion.copy(left = browsingRegion.left + railWidth)
    if (!layout.usesSplitLayout || !contextOpen || layout.tabletopFold != null) {
        return BoxlorePaneSpec(BoxloreNavigationLayout.Rail, railWidth, content, null, 0f)
    }
    val verticalFold = layout.folds.any {
        it.orientation == BoxloreFoldOrientation.Vertical &&
            (it.isSeparating || it.isOccluding || it.state == BoxloreFoldState.HalfOpened)
    }
    if (verticalFold && regions.size > 1) {
        val contextRegion = regions.last()
        val primaryRegion = regions.first()
        // A hinge takes precedence over the ideal ratio. Close rather than squeeze either pane.
        if (contextRegion.width < 360f || primaryRegion.width - railWidth < 320f) {
            return BoxlorePaneSpec(BoxloreNavigationLayout.Rail, railWidth, content, null, 0f)
        }
        val width = contextRegion.width.coerceAtMost(520f)
        val left = contextRegion.left + (contextRegion.width - width) / 2f
        return BoxlorePaneSpec(
            BoxloreNavigationLayout.Rail,
            railWidth,
            primaryRegion.copy(left = primaryRegion.left + railWidth),
            contextRegion.copy(left = left, right = left + width),
            0f,
        )
    }
    val gap = 16f
    val contextWidth = ((content.width - gap) * 0.4f).coerceIn(360f, 520f)
    val context = content.copy(left = content.right - contextWidth)
    return BoxlorePaneSpec(BoxloreNavigationLayout.Rail, railWidth, content.copy(right = context.left - gap), context, gap)
}
