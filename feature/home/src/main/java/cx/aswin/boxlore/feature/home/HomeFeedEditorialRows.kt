package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.feature.home.components.EditorialRowSkeleton
import cx.aswin.boxlore.feature.home.components.HomeChildHeaderTone
import cx.aswin.boxlore.feature.home.components.HomeChildSectionHeader
import cx.aswin.boxlore.feature.home.components.HomeDiscoveryRail
import cx.aswin.boxlore.feature.home.components.HomeFeedSpacing
import cx.aswin.boxlore.feature.home.logic.editorialRowDefinitionsFor

internal fun LazyStaggeredGridScope.editorialFeedItems(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    loadingState: PodcastFeedLoadingState,
    callbacks: HomeFeedCallbacks,
) {
    if (loadingState.isEditorialRowsLoading && content.editorialRows.list.isEmpty()) {
        val definitions = editorialRowDefinitionsFor(feedState.discoveryGreeting.daypart)
        definitions.forEachIndexed { index, definition ->
            item(
                span = StaggeredGridItemSpan.FullLine,
                key = "editorial_${definition.providerId}",
                contentType = "editorial_row",
            ) {
                EditorialRowSkeleton(
                    title = definition.title,
                    icon = definition.icon.toHomeEditorialIcon(),
                    isLast = index == definitions.lastIndex,
                    tone = HomeChildHeaderTone.PRIMARY,
                )
            }
        }
    }
    content.editorialRows.list.forEachIndexed { index, row ->
        item(
            span = StaggeredGridItemSpan.FullLine,
            key = "editorial_${row.providerId}",
            contentType = "editorial_row",
        ) {
            EditorialRow(
                row = row,
                isLast = index == content.editorialRows.list.lastIndex,
                tone = HomeChildHeaderTone.PRIMARY,
                callbacks = callbacks,
            )
        }
    }
}

@Composable
private fun EditorialRow(
    row: HomeEditorialRow,
    isLast: Boolean,
    tone: HomeChildHeaderTone,
    callbacks: HomeFeedCallbacks,
) {
    LaunchedEffect(row.providerId) {
        AnalyticsHelper.trackCuratedBlockImpression(
            blockTitle = row.title,
            vibeIds = listOf(row.providerId),
        )
    }
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else HomeFeedSpacing.RelatedRailGap - HomeFeedSpacing.GridGap),
        verticalArrangement = Arrangement.spacedBy(HomeFeedSpacing.HeaderContentGap),
    ) {
        HomeChildSectionHeader(
            title = row.title,
            icon = row.icon.toHomeEditorialIcon(),
            tone = tone,
        )
        HomeDiscoveryRail { cardWidth ->
            itemsIndexed(
                items = row.podcasts,
                key = { _, podcast ->
                    "${row.providerId}:${podcast.id}:${podcast.latestEpisode?.id.orEmpty()}"
                },
            ) { position, podcast ->
                val episode = podcast.latestEpisode ?: return@itemsIndexed
                CuratedEpisodeCard(
                    podcast = podcast,
                    episode = episode,
                    onClick = {
                        AnalyticsHelper.trackCuratedCardTapped(
                            podcastId = podcast.id,
                            podcastName = podcast.title,
                            vibeId = row.providerId,
                            positionIndex = position,
                        )
                        callbacks.onEpisodeClick?.invoke(
                            episode,
                            podcast,
                            "home_editorial_${row.providerId}",
                        ) ?: callbacks.onPodcastClick(
                            podcast,
                            "home_editorial_${row.providerId}",
                            null,
                            position,
                        )
                    },
                    showSubtitle = false,
                    modifier = Modifier.width(cardWidth),
                    presentation = FeedMediaCardPresentation.ExpressivePoster,
                )
            }
        }
    }
}
