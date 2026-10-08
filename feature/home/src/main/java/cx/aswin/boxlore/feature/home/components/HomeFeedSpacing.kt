package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedPosterSpacing

/** Shared Home discovery spacing — rails, grids, and card feet. */
internal object HomeFeedSpacing {
    /** Phone time rails expose a partial second card to make horizontal scrolling visible. */
    const val RAIL_CARD_WIDTH_FRACTION = 0.52f
    val RailCardMaxWidth = 208.dp
    const val COMPACT_RAIL_CARD_WIDTH_FRACTION = 0.42f
    val CompactRailCardMaxWidth = 176.dp
    val RailItemGap = 16.dp
    val GridGap = FeedPosterSpacing.GridGap
    val SectionGap = 32.dp
    val RelatedRailGap = 20.dp
    val HeaderContentGap = 12.dp

    fun railCardWidth(viewportWidth: Dp, compact: Boolean = false): Dp =
        if (compact) {
            (viewportWidth * COMPACT_RAIL_CARD_WIDTH_FRACTION).coerceAtMost(CompactRailCardMaxWidth)
        } else {
            (viewportWidth * RAIL_CARD_WIDTH_FRACTION).coerceAtMost(RailCardMaxWidth)
        }

    val CardTextPadding = FeedPosterSpacing.CardTextPadding

    /** Hero + two 2×2 body rows (1+4+4). */
    const val ForYouTotalCap = 9
    const val ForYouBodyCount = 8
    const val ExploreGridCap = 6
}
