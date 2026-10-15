package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.EqualHeightPosterGrid
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.StableEpisodeList

@Immutable
data class HomeRecommendationLayout(val columns: Int = 2, val tablet: Boolean = false)

/**
 * Emits the "For You" section into a [LazyStaggeredGridScope].
 * Phones retain the featured card and poster grid; tablets use one continuous recommendation rail.
 */
fun LazyStaggeredGridScope.forYouItems(
    recommendations: StableEpisodeList,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    discoveryContextTitle: String,
    onBrowseRecommendations: () -> Unit,
    showTasteHeader: Boolean = true,
    isFallback: Boolean = true,
    layout: HomeRecommendationLayout = HomeRecommendationLayout(),
) {
    if (layout.tablet && layout.columns >= 3) {
        tabletForYouItems(recommendations, onEpisodeClick, discoveryContextTitle, onBrowseRecommendations, showTasteHeader, isFallback)
        return
    }
    compactForYouItems(recommendations, onEpisodeClick, discoveryContextTitle, onBrowseRecommendations, showTasteHeader, isFallback, layout.columns)
}

private fun LazyStaggeredGridScope.compactForYouItems(
    recommendations: StableEpisodeList,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    discoveryContextTitle: String,
    onBrowseRecommendations: () -> Unit,
    showTasteHeader: Boolean,
    isFallback: Boolean,
    columns: Int,
) {
    val items = recommendations.list.take(HomeFeedSpacing.ForYouTotalCap)

    if (showTasteHeader) {
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_header", contentType = "for_you_header") {
            HomePersonalRecommendationsHeader(isFallback, onBrowseRecommendations)
        }
    }

    if (items.isEmpty()) {
        forYouSkeletonItems(columns)
        return
    }

    item(span = StaggeredGridItemSpan.FullLine, key = "for_you_hero", contentType = "for_you_hero") {
        LaunchedEffect(recommendations.list, discoveryContextTitle) {
            AnalyticsHelper.trackHomeRecommendationsImpression(
                recommendationsCount = recommendations.list.size,
                episodeIds = recommendations.list.map { it.id },
                timeBlockTitle = discoveryContextTitle,
            )
        }
        val ep = items[0]
        val parentPodcast =
            Podcast(
                id = ep.podcastId ?: "",
                title = ep.podcastTitle ?: "Podcast",
                artist = "",
                imageUrl = ep.podcastImageUrl?.takeIf { it.isNotBlank() } ?: ep.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                description = "",
                genre = ep.podcastGenre ?: "Podcast",
            )
        CuratedEpisodeCard(
            episode = ep,
            podcast = parentPodcast,
            showSubtitle = false,
            presentation = FeedMediaCardPresentation.ExpressiveFeatured,
            onClick = {
                AnalyticsHelper.trackHomeRecommendationCardTapped(
                    episodeId = ep.id,
                    episodeTitle = ep.title,
                    podcastId = parentPodcast.id,
                    podcastName = parentPodcast.title,
                    positionIndex = 0,
                    timeBlockTitle = discoveryContextTitle,
                )
                onEpisodeClick(ep, parentPodcast)
            },
        )
    }

    val remaining = items.drop(1)
    remaining.chunked(columns).forEachIndexed { row, rowItems ->
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_body_$row", contentType = "for_you_body") {
            EqualHeightPosterGrid(columns = columns) {
                rowItems.forEachIndexed { index, ep ->
                    val originalIndex = row * columns + index + 1
                    val parentPodcast =
                        Podcast(
                            id = ep.podcastId ?: "",
                            title = ep.podcastTitle ?: "Podcast",
                            artist = "",
                            imageUrl =
                            ep.podcastImageUrl?.takeIf { it.isNotBlank() }
                                ?: ep.imageUrl?.takeIf { it.isNotBlank() }
                                ?: "",
                            description = "",
                            genre = ep.podcastGenre ?: "Podcast",
                        )
                    CuratedEpisodeCard(
                        podcast = parentPodcast,
                        episode = ep,
                        onClick = {
                            AnalyticsHelper.trackHomeRecommendationCardTapped(
                                episodeId = ep.id,
                                episodeTitle = ep.title,
                                podcastId = parentPodcast.id,
                                podcastName = parentPodcast.title,
                                positionIndex = originalIndex,
                                timeBlockTitle = discoveryContextTitle,
                            )
                            onEpisodeClick(ep, parentPodcast)
                        },
                        showSubtitle = false,
                        presentation = FeedMediaCardPresentation.ExpressivePoster,
                    )
                }
            }
        }
    }
}

/** A personal chapter keeps its identity even when show-based recommendations are absent. */
@Composable
internal fun HomePersonalRecommendationsHeader(
    isFallback: Boolean,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeTopLevelSectionHeader(
        title = stringResource(if (isFallback) R.string.home_popular_region else R.string.home_picked_for_you),
        seeAllContentDescription = stringResource(R.string.home_recommendations_see_all),
        onSeeAllClick = onBrowse,
        chapter = HomeDiscoveryChapter.PERSONAL,
        modifier = modifier,
    )
}

private fun LazyStaggeredGridScope.forYouSkeletonItems(columns: Int) {
    item(span = StaggeredGridItemSpan.FullLine, key = "for_you_hero", contentType = "for_you_hero") {
        HomeMediaCardSkeleton(presentation = FeedMediaCardPresentation.ExpressiveFeatured)
    }
    repeat((HomeFeedSpacing.ForYouBodyCount + columns - 1) / columns) { row ->
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_body_$row", contentType = "for_you_body") {
            EqualHeightPosterGrid(columns = columns) {
                repeat((HomeFeedSpacing.ForYouBodyCount - row * columns).coerceAtMost(columns)) { HomeMediaCardSkeleton() }
            }
        }
    }
}

private fun LazyStaggeredGridScope.tabletForYouItems(
    recommendations: StableEpisodeList,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    discoveryContextTitle: String,
    onBrowse: () -> Unit,
    showTasteHeader: Boolean,
    isFallback: Boolean,
) {
    if (showTasteHeader) {
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_header", contentType = "for_you_header") {
            HomePersonalRecommendationsHeader(isFallback, onBrowse)
        }
    }
    item(span = StaggeredGridItemSpan.FullLine, key = "for_you_hero", contentType = "for_you_hero") {
        if (recommendations.list.isNotEmpty()) {
            LaunchedEffect(recommendations.list, discoveryContextTitle) {
                AnalyticsHelper.trackHomeRecommendationsImpression(recommendations.list.size, recommendations.list.map { it.id }, discoveryContextTitle)
            }
        }
        TabletRecommendationRail(recommendations.list.take(HomeFeedSpacing.ForYouTotalCap)) { episode, index, presentation ->
            TabletRecommendationCard(episode, index, discoveryContextTitle, presentation, onEpisodeClick)
        }
    }
}

@Composable
private fun TabletRecommendationCard(episode: Episode, index: Int, contextTitle: String, presentation: FeedMediaCardPresentation, onClick: (Episode, Podcast) -> Unit) {
    val podcast = Podcast(id = episode.podcastId ?: "", title = episode.podcastTitle ?: "Podcast", artist = "", imageUrl = episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: episode.imageUrl?.takeIf { it.isNotBlank() } ?: "", description = "", genre = episode.podcastGenre ?: "Podcast")
    CuratedEpisodeCard(episode = episode, podcast = podcast, showSubtitle = false, presentation = presentation, onClick = {
        AnalyticsHelper.trackHomeRecommendationCardTapped(episode.id, episode.title, podcast.id, podcast.title, index, contextTitle)
        onClick(episode, podcast)
    })
}
