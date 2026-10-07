package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
internal fun HomeDiscoveryRail(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: LazyListScope.(Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = HomeFeedSpacing.railCardWidth(maxWidth, compact)
        LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(HomeFeedSpacing.RailItemGap)) {
            content(cardWidth)
        }
    }
}
