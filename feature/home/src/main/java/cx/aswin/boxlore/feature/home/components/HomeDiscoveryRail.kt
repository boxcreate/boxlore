package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeDiscoveryRail(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: LazyListScope.(Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = HomeFeedSpacing.railCardWidth(maxWidth, compact)
        // Cards are sized from the slot inside the gutter, as before. The rail runs past the leading
        // gutter to the screen edge, so scrolled cards slide off the edge instead of being cut at an
        // invisible line; the trailing gutter stays, so at rest the next card is cut as visibly as before.
        LazyRow(
            modifier = Modifier.bleed(start = HomeFeedSpacing.GridSide, end = 0.dp).testTag("home_discovery_rail"),
            contentPadding = PaddingValues(start = HomeFeedSpacing.GridSide),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(HomeFeedSpacing.RailItemGap),
        ) {
            content(cardWidth)
        }
    }
}
