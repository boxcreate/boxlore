package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.expressivePosterCardHeight
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.model.Episode

/** A single, bounded rail avoids sparse final rows while keeping the featured story first. */
@Composable
internal fun TabletRecommendationRail(
    episodes: List<Episode>,
    card: @Composable (Episode, Int, FeedMediaCardPresentation) -> Unit,
) {
    val keys = remember(episodes) { LazyListKeyPolicy.disambiguateKeys(episodes, "for_you_rail") { it.id } }
    DiscoveryExpressiveTheme {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val posterWidth = if (maxWidth < 840.dp) 160.dp else 184.dp
            val cardHeight = expressivePosterCardHeight(posterWidth)
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(cardHeight).testTag("tablet_recommendation_rail"),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (episodes.isEmpty()) {
                    items(5) { index ->
                        val presentation = if (index == 0) FeedMediaCardPresentation.ExpressiveFeatured else FeedMediaCardPresentation.ExpressivePoster
                        Box(Modifier.width(if (index == 0) posterWidth * 2 + 16.dp else posterWidth).height(cardHeight), propagateMinConstraints = true) {
                            HomeMediaCardSkeleton(presentation = presentation)
                        }
                    }
                } else {
                    itemsIndexed(episodes, key = { index, _ -> keys[index] }) { index, episode ->
                        val presentation = if (index == 0) FeedMediaCardPresentation.ExpressiveFeatured else FeedMediaCardPresentation.ExpressivePoster
                        Box(
                            Modifier.width(if (index == 0) posterWidth * 2 + 16.dp else posterWidth).height(cardHeight).testTag("tablet_recommendation_$index"),
                            propagateMinConstraints = true,
                        ) { card(episode, index, presentation) }
                    }
                }
            }
        }
    }
}
