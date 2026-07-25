package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared Home discovery spacing — rails, grids, and card feet. */
internal object HomeFeedSpacing {
    val RailCardWidth = 156.dp
    val RailItemGap = 16.dp
    val GridGap = 16.dp

    /** 18sp line height reserved per title line in equal-height rail feet. */
    val RailTitleLineHeight = 18.dp

    val CardTextPadding = 12.dp

    /** Hero + two 2×2 body rows (1+4+4). */
    const val ForYouTotalCap = 9
    const val ForYouBodyCount = 8
    const val ExploreGridCap = 6

    fun railTextFootHeight(titleMaxLines: Int): Dp =
        RailTitleLineHeight * titleMaxLines.coerceAtLeast(1)
}
