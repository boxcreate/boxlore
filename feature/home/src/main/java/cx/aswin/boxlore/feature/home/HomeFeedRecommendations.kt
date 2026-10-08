package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.core.designsystem.components.EqualHeightPosterGrid
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardDensity
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.feature.home.components.BecauseYouLikeSection
import cx.aswin.boxlore.feature.home.components.HomeDiscoveryChapter
import cx.aswin.boxlore.feature.home.components.HomeFeedSpacing
import cx.aswin.boxlore.feature.home.components.HomeMediaCardSkeleton
import cx.aswin.boxlore.feature.home.components.HomePersonalRecommendationsHeader
import cx.aswin.boxlore.feature.home.components.HomeTopLevelSectionHeader
import cx.aswin.boxlore.feature.home.components.PodcastCard
import cx.aswin.boxlore.feature.home.components.forYouItems

internal fun LazyStaggeredGridScope.curatedForYouItems(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    derivedState: PodcastFeedDerivedState,
) {
    if (!derivedState.hasBecauseYouLike && !derivedState.hasRecommendations) return
    if (!derivedState.hasBecauseYouLike) {
        curatedHeaderItem(callbacks, recommendationState.isRecommendationsFallback)
    }
    becauseYouLikeItem(feedState, recommendationState, playback, callbacks, derivedState)
    if (derivedState.hasRecommendations) {
        forYouItems(
            recommendations = content.recommendations,
            onEpisodeClick = { episode, podcast ->
                callbacks.onEpisodeClick?.invoke(episode, podcast, "home_for_you")
            },
            discoveryContextTitle = feedState.discoveryGreeting.title,
            onBrowseRecommendations = {
                callbacks.onNavigateToExplore?.invoke(null, "home_for_you_see_all", "foryou")
            },
            showTasteHeader = derivedState.hasBecauseYouLike,
            isFallback = recommendationState.isRecommendationsFallback,
        )
    }
}

private fun LazyStaggeredGridScope.curatedHeaderItem(
    callbacks: HomeFeedCallbacks,
    isFallback: Boolean,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "curated_header", contentType = "section_header") {
        val onBrowse: () -> Unit = { callbacks.onNavigateToExplore?.invoke(null, "home_for_you_see_all", "foryou") }
        // The unchanged upper cards already reserve 12dp after their content.
        val modifier = Modifier.padding(top = HomeFeedSpacing.SectionGap - HomeFeedSpacing.GridGap - 12.dp)
        HomePersonalRecommendationsHeader(isFallback, onBrowse, modifier)
    }
}

private fun LazyStaggeredGridScope.becauseYouLikeItem(
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    derivedState: PodcastFeedDerivedState,
) {
    val podcast = feedState.seemsToLikePodcast ?: return
    if (!derivedState.hasBecauseYouLike) return
    item(span = StaggeredGridItemSpan.FullLine, key = "because_you_like", contentType = "because_you_like") {
        PinnedGridItemContent {
            BecauseYouLikeSection(
                podcast = podcast,
                recommendations = recommendationState.becauseYouLikeRecommendations,
                suggestedPodcasts = recommendationState.becauseYouLikePodcasts,
                currentPlayingEpisodeId = playback.player.currentPlayingEpisodeId,
                isPlaying = playback.player.isPlaying,
                onEpisodeClick = { episode, episodePodcast ->
                    callbacks.onEpisodeClick?.invoke(episode, episodePodcast, "home_because_you_like")
                },
                onPlayEpisode = { ep, pod -> callbacks.onPlayEpisode(ep, pod, PlaybackEntryPoint.GENERIC) },
                onPodcastClick = { clickedPodcast ->
                    callbacks.onPodcastClick(clickedPodcast, "home_because_you_like", null, null)
                },
                onChangePodcastClick = recommendationState.onChangePodcastClick,
                modifier = Modifier.padding(
                    top = HomeFeedSpacing.SectionGap - HomeFeedSpacing.GridGap - 12.dp,
                    bottom = if (derivedState.hasRecommendations) HomeFeedSpacing.SectionGap - HomeFeedSpacing.GridGap else 0.dp,
                ),
            )
        }
    }
}

internal fun LazyStaggeredGridScope.discoveryGreetingItem(
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    item(
        span = StaggeredGridItemSpan.FullLine,
        key = "discovery_greeting",
        contentType = "discovery_greeting",
    ) {
        DiscoveryGreetingHeader(
            greeting = feedState.discoveryGreeting,
            onSeeAllClick = {
                callbacks.onNavigateToExplore?.invoke(null, "home_discovery_greeting", "foryou")
            },
            modifier = Modifier.padding(top = HomeFeedSpacing.SectionGap - HomeFeedSpacing.GridGap),
        )
    }
}

internal fun LazyStaggeredGridScope.discoverFeedItems(
    feedState: PodcastFeedUiState,
    derivedState: PodcastFeedDerivedState,
    callbacks: HomeFeedCallbacks,
) {
    discoverHeaderItem(feedState, callbacks)
    if (derivedState.showDiscoverContent) {
        discoverPodcastItems(derivedState, feedState, callbacks)
        discoverViewMoreItem(feedState, callbacks)
    } else if (derivedState.showDiscoverSkeleton) {
        repeat((HomeFeedSpacing.ExploreGridCap + 1) / 2) { row ->
            item(span = StaggeredGridItemSpan.FullLine, key = "discover_grid_$row", contentType = "discover_grid") {
                EqualHeightPosterGrid {
                    repeat(2) {
                        HomeMediaCardSkeleton()
                    }
                }
            }
        }
    }
}

private fun LazyStaggeredGridScope.discoverHeaderItem(
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "discover_header", contentType = "section_header") {
        cx.aswin.boxlore.feature.home.components.DiscoverSection(
            selectedCategory = feedState.selectedCategory,
            onCategorySelected = callbacks.onSelectCategory,
            onHeaderClick = { callbacks.onNavigateToExplore?.invoke(feedState.selectedCategory ?: "All", "home_discover_header", null) },
            modifier = Modifier.padding(top = HomeFeedSpacing.SectionGap - HomeFeedSpacing.GridGap),
        )
    }
}

private fun LazyStaggeredGridScope.discoverPodcastItems(
    derivedState: PodcastFeedDerivedState,
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    derivedState.discoverItems.chunked(2).forEachIndexed { row, rowItems ->
        item(span = StaggeredGridItemSpan.FullLine, key = "discover_grid_$row", contentType = "discover_grid") {
            EqualHeightPosterGrid {
                rowItems.forEachIndexed { index, podcast ->
                    PodcastCard(
                        podcast = podcast,
                        showGenreChip = false,
                        showSubtitle = false,
                        density = FeedMediaCardDensity.Grid,
                        presentation = FeedMediaCardPresentation.ExpressivePoster,
                        onClick = {
                            callbacks.onPodcastClick(
                                podcast,
                                "home_discover_grid",
                                feedState.selectedCategory,
                                row * 2 + index,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun LazyStaggeredGridScope.discoverViewMoreItem(
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "discover_view_more", contentType = "discover_view_more") {
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            DiscoveryExpressiveTheme {
                androidx.compose.material3.FilledTonalButton(
                    onClick = { callbacks.onNavigateToExplore?.invoke(feedState.selectedCategory ?: "All", "home_discover_view_all_button", null) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        feedState.selectedCategory?.let { stringResource(R.string.home_view_more_in, it) }
                            ?: stringResource(R.string.home_view_more_shows),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryGreetingHeader(
    greeting: DiscoveryGreeting,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeTopLevelSectionHeader(
        title = greeting.title,
        seeAllContentDescription = stringResource(R.string.home_discovery_see_all),
        onSeeAllClick = onSeeAllClick,
        chapter = HomeDiscoveryChapter.MOMENT,
        daypart = greeting.daypart,
        modifier = modifier,
    )
}
