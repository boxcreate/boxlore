package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
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

/** Window-relative physical bounds, supplied by the shell for context and immersive players. */
val LocalBoxlorePaneBounds = staticCompositionLocalOf<BoxloreLayoutBounds?> { null }

/** Feature overlays reserve only chrome that is actually present in their shell. */
val LocalBoxloreNavigationRailVisible = staticCompositionLocalOf { false }
val LocalBoxlorePlayerPaneVisible = staticCompositionLocalOf { false }
val LocalBoxloreMiniPlayerVisible = staticCompositionLocalOf { false }
val LocalBoxloreDockedPlayerBounds = staticCompositionLocalOf<BoxloreLayoutBounds?> { null }

/**
 * Where browsing, a detail and the player go in [layout]. [contextOpen] asks for a context pane;
 * [playerOnly] says the player alone asks for it, with no detail open.
 *
 * - Idle, or with the context closed, pages use the whole window beside the rail, half-open folds
 *   included; dialogs, sheets and menus keep off the hinge on their own.
 * - In tabletop the context takes the upper half and browsing the lower half, unless the upper
 *   half is too short to use.
 * - A vertical hinge with two usable halves puts the context in the second half.
 * - On a flat window the player alone opens beside browsing only when browsing keeps 440dp;
 *   otherwise it stays docked. A detail still splits.
 */
fun boxlorePaneSpec(layout: BoxloreWindowLayout, contextOpen: Boolean, playerOnly: Boolean = false): BoxlorePaneSpec {
    val fullWindow = BoxloreLayoutBounds(0f, 0f, layout.widthDp, layout.heightDp)
    if (!layout.usesTabletLayout) {
        return BoxlorePaneSpec(BoxloreNavigationLayout.BottomBar, 0f, fullWindow, null, 0f)
    }
    val railWidth = RAIL_WIDTH_DP.coerceAtMost(layout.widthDp)
    val idle = BoxlorePaneSpec(BoxloreNavigationLayout.Rail, railWidth, fullWindow.copy(left = railWidth), null, 0f)
    if (!contextOpen) return idle
    val regions = layout.foldSafeRegions()
    if (layout.tabletopFold != null) return tabletopSpec(regions, railWidth) ?: idle
    if (layout.usesHingeSplit) return hingeSplitSpec(regions, railWidth) ?: idle
    if (regions.size > 1 || !layout.usesSplitLayout) return idle
    val content = idle.primary
    val gap = 16f
    val contextWidth = ((content.width - gap) * 0.4f).coerceIn(360f, 520f)
    val context = content.copy(left = content.right - contextWidth)
    val primary = content.copy(right = context.left - gap)
    if (playerOnly && primary.width < PLAYER_CONTEXT_MIN_PRIMARY_DP) return idle
    return BoxlorePaneSpec(BoxloreNavigationLayout.Rail, railWidth, primary, context, gap)
}

/** The context in the upper half and browsing below it, or null when the upper half is too short. */
private fun tabletopSpec(regions: List<BoxloreLayoutBounds>, railWidth: Float): BoxlorePaneSpec? {
    if (regions.size != 2) return null
    val upper = regions.first()
    val lower = regions.last()
    if (upper.height < TABLETOP_CONTEXT_MIN_HEIGHT_DP) return null
    return BoxlorePaneSpec(BoxloreNavigationLayout.Rail, railWidth, lower.copy(left = lower.left + railWidth), upper, 0f)
}

/** Browsing in the first half beside the rail and the context in the second, or null when either would be squeezed. */
private fun hingeSplitSpec(regions: List<BoxloreLayoutBounds>, railWidth: Float): BoxlorePaneSpec? {
    val primaryRegion = regions.first()
    val contextRegion = regions.last()
    if (primaryRegion.width - railWidth < HINGE_SPLIT_MIN_PRIMARY_DP) return null
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

/**
 * True when the docked mini player sits over [pane]. It spans the [dock] (the primary pane), so only
 * panes that overlap it horizontally reserve its height. Without either, it covers the pane.
 */
fun dockedPlayerCoversPane(pane: BoxloreLayoutBounds?, dock: BoxloreLayoutBounds?): Boolean =
    pane == null || dock == null || (pane.left < dock.right && dock.left < pane.right)

/** [dockedPlayerCoversPane] for the pane this content is in, from the bounds the shell supplies. */
@Composable
fun dockedPlayerCoversPane(): Boolean = dockedPlayerCoversPane(LocalBoxlorePaneBounds.current, LocalBoxloreDockedPlayerBounds.current)

internal const val RAIL_WIDTH_DP = 80f
private const val TABLETOP_CONTEXT_MIN_HEIGHT_DP = 240f
private const val HINGE_SPLIT_MIN_PRIMARY_DP = 280f
private const val PLAYER_CONTEXT_MIN_PRIMARY_DP = 440f
