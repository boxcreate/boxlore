package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedPosterSpacing

/** Shared Home discovery spacing — rails, grids, and card feet. */
internal object HomeFeedSpacing {
    /** Two complete cards plus a clear preview of card three on every phone width. */
    const val RAIL_CARD_WIDTH_FRACTION = 0.41f
    val RailItemGap = 16.dp
    val GridGap = FeedPosterSpacing.GridGap

    /** 18sp line height reserved per title line in equal-height rail feet. */
    val RailTitleLineHeight = FeedPosterSpacing.TitleLineHeight

    val CardTextPadding = FeedPosterSpacing.CardTextPadding

    /** Hero + two 2×2 body rows (1+4+4). */
    const val ForYouTotalCap = 9
    const val ForYouBodyCount = 8
    const val ExploreGridCap = 6

    fun railTextFootHeight(titleMaxLines: Int): Dp = FeedPosterSpacing.textFootHeight(titleMaxLines)
}
