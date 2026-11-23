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

@Composable
internal fun HomeDiscoveryRail(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: LazyListScope.(Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = HomeFeedSpacing.railCardWidth(maxWidth, compact)
        // The rail runs past the gutter to the screen edge, so cards slide off the edge instead of
        // being cut at an invisible line; at rest the first card still starts on the gutter.
        LazyRow(
            modifier = Modifier.bleed(HomeFeedSpacing.GridSide).testTag("home_discovery_rail"),
            contentPadding = PaddingValues(horizontal = HomeFeedSpacing.GridSide),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(HomeFeedSpacing.RailItemGap),
        ) {
            content(cardWidth)
        }
    }
}
