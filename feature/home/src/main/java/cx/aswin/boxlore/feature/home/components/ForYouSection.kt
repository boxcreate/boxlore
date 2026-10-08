package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
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

/**
 * Emits the "For You" section into a [LazyStaggeredGridScope].
 * The first recommendation stays full-line; body cards load one [EqualHeightPosterGrid]
 * row at a time with font-scaled three-line title feet.
 */
fun LazyStaggeredGridScope.forYouItems(
    recommendations: StableEpisodeList,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    discoveryContextTitle: String,
    onBrowseRecommendations: () -> Unit,
    showTasteHeader: Boolean = true,
    isFallback: Boolean = true,
) {
    val items = recommendations.list.take(HomeFeedSpacing.ForYouTotalCap)

    if (showTasteHeader) {
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_header", contentType = "for_you_header") {
            HomePersonalRecommendationsHeader(isFallback, onBrowseRecommendations)
        }
    }

    if (items.isEmpty()) {
        forYouSkeletonItems()
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
    remaining.chunked(2).forEachIndexed { row, rowItems ->
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_body_$row", contentType = "for_you_body") {
            EqualHeightPosterGrid {
                rowItems.forEachIndexed { index, ep ->
                    val originalIndex = row * 2 + index + 1
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

private fun LazyStaggeredGridScope.forYouSkeletonItems() {
    item(span = StaggeredGridItemSpan.FullLine, key = "for_you_hero", contentType = "for_you_hero") {
        HomeMediaCardSkeleton(presentation = FeedMediaCardPresentation.ExpressiveFeatured)
    }
    repeat((HomeFeedSpacing.ForYouBodyCount + 1) / 2) { row ->
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_body_$row", contentType = "for_you_body") {
            EqualHeightPosterGrid {
                repeat(2) { HomeMediaCardSkeleton() }
            }
        }
    }
}
