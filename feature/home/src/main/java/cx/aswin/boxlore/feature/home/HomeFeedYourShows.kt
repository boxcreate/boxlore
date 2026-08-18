package cx.aswin.boxlore.feature.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            // Keep hero + filter chips on the same reveal (`viewportReady` / !isLoading).
            // Skeleton layout is frozen via peak sub count so Room emissions do not remorph
            // 1-row → 2-row while we wait for that coordinated reveal.
            val subCount = content.subscribedItems.list.size
            var skeletonLayoutCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(subCount) {
                if (subCount > skeletonLayoutCount) {
                    skeletonLayoutCount = subCount
                }
            }
            androidx.compose.animation.Crossfade(
                targetState = derivedState.viewportReady,
                animationSpec = tween(500),
                label = "your_shows_crossfade",
                modifier = Modifier.padding(bottom = 12.dp),
            ) { ready ->
                YourShowsFeedContent(
                    ready = ready,
                    skeletonLayoutCount = skeletonLayoutCount,
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
    skeletonLayoutCount: Int,
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    loadingState: PodcastFeedLoadingState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
) {
    when {
        !ready ->
            YourShowsSkeleton(
                // Peak count freezes 1-row vs 2-row for the whole wait; never drop back to 0/5.
                subscribedCount = skeletonLayoutCount,
            )
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
