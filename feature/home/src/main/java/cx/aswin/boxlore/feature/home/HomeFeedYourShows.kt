package cx.aswin.boxlore.feature.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            androidx.compose.animation.Crossfade(
                targetState = derivedState.viewportReady,
                animationSpec = tween(500),
                label = "your_shows_crossfade",
                modifier = Modifier.padding(bottom = 12.dp),
            ) { ready ->
                YourShowsFeedContent(
                    ready = ready,
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
): Boolean =
    loadingState.isLoading || content.subscribedItems.list.isNotEmpty() || feedState.showImportBanner

@Composable
private fun YourShowsFeedContent(
    ready: Boolean,
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    loadingState: PodcastFeedLoadingState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
) {
    when {
        !ready -> YourShowsSkeleton(subscribedCount = content.subscribedItems.list.size)
        content.subscribedItems.list.isNotEmpty() ->
            YourShowsSection(
                subscribedPodcasts = content.subscribedItems,
                latestEpisodes = content.latestItems,
                selectedPodcastId = feedState.selectedPodcastId,
                selectedPodcastEpisodes = content.selectedPodcastEpisodes,
                isSelectedPodcastLoading = loadingState.isSelectedPodcastLoading,
                isSelectedRssRefreshing = loadingState.isSelectedRssRefreshing,
                episodePlaybackState = playback.episodePlaybackState,
                currentPlayingEpisodeId = playback.player.currentPlayingEpisodeId,
                isPlaying = playback.player.isPlaying,
                onPodcastSelected = callbacks.onPodcastSelected,
                onPodcastClick = { callbacks.onPodcastClick(it, "home_your_shows", null, null) },
                onEpisodeClick = { episode, podcast, entryPoint ->
                    callbacks.onEpisodeClick?.invoke(episode, podcast, entryPoint)
                },
                onPlayMix = callbacks.onPlayMix,
                onPlayEpisode = callbacks.onPlayEpisode,
                downloadedEpisodeIds = playback.player.downloadedEpisodeIds,
                onViewLibrary = { callbacks.onNavigateToLibrary?.invoke() },
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
