package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.home.components.HomeLoadingReveal
import cx.aswin.boxlore.feature.home.components.YourShowsSection
import cx.aswin.boxlore.feature.home.components.YourShowsSkeleton

internal fun LazyStaggeredGridScope.yourShowsItem(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    loadingState: PodcastFeedLoadingState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    derivedState: PodcastFeedDerivedState,
) {
    if (!shouldShowYourShows(content, feedState, loadingState)) return
    item(span = StaggeredGridItemSpan.FullLine, key = "your_shows", contentType = "your_shows") {
        PinnedGridItemContent {
            // Freeze the placeholder geometry for this loading session, including
            // the unknown-count two-row case, until local content can be revealed.
            val skeletonLayoutCount = remember { content.subscribedItems.list.size }
            HomeLoadingReveal(
                ready = derivedState.viewportReady,
                modifier = Modifier.padding(bottom = 12.dp),
                placeholder = { YourShowsSkeleton(subscribedCount = skeletonLayoutCount) },
            ) {
                YourShowsFeedContent(
                    content = content,
                    feedState = feedState,
                    loadingState = loadingState,
                    playback = playback,
                    callbacks = callbacks,
                )
            }
        }
    }
}

private fun shouldShowYourShows(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    loadingState: PodcastFeedLoadingState,
): Boolean = loadingState.isLoading || content.subscribedItems.list.isNotEmpty() || feedState.showImportBanner

@Composable
private fun YourShowsFeedContent(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    loadingState: PodcastFeedLoadingState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
) {
    when {
        content.subscribedItems.list.isNotEmpty() ->
            YourShowsSection(
                subscribedPodcasts = content.subscribedItems,
                latestEpisodes = content.latestItems,
                selectedPodcastId = feedState.selectedPodcastId,
                selectedPodcastEpisodes = content.selectedPodcastEpisodes,
                isSelectedPodcastLoading = loadingState.isSelectedPodcastLoading,
                isSelectedRssRefreshing = loadingState.isSelectedRssRefreshing,
                episodePlaybackState = playback.episodePlaybackState,
                softExpireProgressEpisodeIds = playback.softExpireProgressEpisodeIds,
                currentPlayingEpisodeId = playback.player.currentPlayingEpisodeId,
                isPlaying = playback.player.isPlaying,
                onPodcastSelected = callbacks.onPodcastSelected,
                onPodcastClick = { callbacks.onPodcastClick(it, "home_your_shows", null, null) },
                onEpisodeClick = { episode, podcast, entryPoint ->
                    callbacks.onEpisodeClick?.invoke(episode, podcast, entryPoint)
                },
                onPlayMix = callbacks.onPlayMix,
                onMixModeChanged = callbacks.onMixModeChanged,
                selectedMixMode = playback.player.homeMixMode,
                onPlayEpisode = callbacks.onPlayEpisode,
                downloadedEpisodeIds = playback.player.downloadedEpisodeIds,
                completedDownloads = playback.player.completedDownloads,
                onViewLibrary = { callbacks.onNavigateToLibrary?.invoke() },
                onViewDownloads = { callbacks.onNavigateToDownloads?.invoke() },
                pinnedPodcastIds = feedState.pinnedPodcastIds,
                onTogglePin = callbacks.onToggleHomePin,
            )
        feedState.showImportBanner -> HomeImportBannerContent(callbacks)
    }
}

@Composable
private fun HomeImportBannerContent(callbacks: HomeFeedCallbacks) {
    LaunchedEffect(Unit) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackHomeImportBannerImpression()
    }
    HomeImportBanner(
        onAiOnboardingClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackHomeImportBannerClicked("ai")
            callbacks.onAiOnboardingClick()
        },
        onSearchClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackHomeImportBannerClicked("search")
            callbacks.onNavigateToExplore?.invoke(null, "home_banner", null)
        },
        onImportClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackHomeImportBannerClicked("import")
            callbacks.onImportClick()
        },
        onDismiss = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackHomeImportBannerDismissed()
            callbacks.onDismissImportBanner()
        },
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
