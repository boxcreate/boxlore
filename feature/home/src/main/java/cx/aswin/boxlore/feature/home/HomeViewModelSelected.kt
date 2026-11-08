package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.playback.completedEpisodeIds
import cx.aswin.boxlore.core.playback.episodeGroupTokens
import cx.aswin.boxlore.feature.home.logic.HomeForegroundSyncLogic
import cx.aswin.boxlore.feature.home.logic.HomeSelectedPodcastLogic
import cx.aswin.boxlore.feature.home.logic.HomeShowEpisodesLogic
import cx.aswin.boxlore.feature.home.logic.HomeYourShowsSignals
import cx.aswin.boxlore.feature.home.logic.SelectedShowLoadState
import cx.aswin.boxlore.feature.home.logic.SelectedShowLoadTransitions
import cx.aswin.boxlore.feature.home.logic.SeriesEpisodesLoader
import cx.aswin.boxlore.feature.home.logic.ShowEpisodesList
import cx.aswin.boxlore.feature.home.logic.ShowEpisodesPool
import cx.aswin.boxlore.feature.home.logic.settledCatalogChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun HomeViewModel.observeSelectedPodcast() {
    viewModelScope.launch {
        val signal =
            combine(
                _selectedPodcastId,
                allHomeHistory,
                subscriptionRepository.subscribedPodcasts,
                _rssRefreshVersion,
                _selectedShowRetryVersion,
            ) { podcastId, allHistory, subs, rssRefreshVersion, retryVersion ->
                HomeSelectedPodcastLogic.buildSignal(
                    podcastId = podcastId,
                    allHistory = allHistory,
                    subs = subs,
                    rssRefreshVersion = rssRefreshVersion,
                    retryVersion = retryVersion,
                )
            }.distinctUntilChanged()

        var previouslyLoadedPodcastId: String? = null
        var state = SelectedShowLoadTransitions.cleared
        fun publish(next: SelectedShowLoadState) {
            state = next
            _selectedShowPool.value = next.pool
            _isSelectedPodcastLoading.value = next.loading
            _selectedShowLoadFailed.value = next.failed
        }
        signal.collectLatest { info ->
            if (info == null) {
                publish(SelectedShowLoadTransitions.cleared)
                previouslyLoadedPodcastId = null
                return@collectLatest
            }
            val switchingPodcast = info.podcastId != previouslyLoadedPodcastId
            previouslyLoadedPodcastId = info.podcastId
            publish(SelectedShowLoadTransitions.started(state, switchingPodcast))
            try {
                publish(SelectedShowLoadTransitions.succeeded(loadSelectedShowPool(info)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModelFilteredView", "Failed to fetch episodes for filter: ${info.podcastId}", e)
                publish(SelectedShowLoadTransitions.failed(state))
            }
        }
    }

    viewModelScope.launch {
        combine(
            _selectedPodcastId,
            rssRepository.refreshingPodcastIds,
        ) { selectedId, refreshingIds ->
            selectedId != null && selectedId in refreshingIds
        }.distinctUntilChanged().collect { refreshing ->
            _isSelectedRssRefreshing.value = refreshing
            _uiState.update { it.copy(isSelectedRssRefreshing = refreshing) }
        }
    }

    viewModelScope.launch {
        val frame =
            combine(
                _selectedPodcastId,
                _selectedShowPool,
                _isSelectedPodcastLoading,
                _selectedShowLoadFailed,
            ) { id, pool, loading, failed ->
                // A pool still held from the previous show never pairs with the new one.
                SelectedShowFrame(id, pool?.takeIf { it.podcastId == id }, loading, failed)
            }
        combine(
            frame,
            userPrefs.hideCompletedInHomeStream,
            playbackRepository.completedEpisodeIds,
        ) { current, hideCompleted, completedIds ->
            val list =
                current.pool?.let { HomeShowEpisodesLogic.build(it, completedIds, hideCompleted) }
                    ?: ShowEpisodesList(emptyList())
            current to list
        }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .collect { (current, list) ->
                _uiState.update {
                    it.copy(
                        selectedPodcastId = current.podcastId,
                        selectedPodcastEpisodes = list.episodes,
                        selectedShowStatus =
                        SelectedShowStatus(
                            caughtUp = list.caughtUp,
                            loadFailed = current.loadFailed && list.episodes.isEmpty(),
                            newestEpisodeIds = list.newestIds,
                        ),
                        isSelectedPodcastLoading = current.loading,
                    )
                }
            }
    }
}

private data class SelectedShowFrame(
    val podcastId: String?,
    val pool: ShowEpisodesPool?,
    val loading: Boolean,
    val loadFailed: Boolean,
)

/** The selected show's episodes to build Home's list from: from the listener's place for a show listened to in order, else its newest. */
private suspend fun HomeViewModel.loadSelectedShowPool(info: SelectedPodcastSignal): ShowEpisodesPool {
    if (!info.inOrder) {
        val newest =
            podcastRepository.getEpisodesPaginated(
                info.podcastId,
                limit = HomeShowEpisodesLogic.NEWEST_POOL_SIZE,
                offset = 0,
                sort = "newest",
            ).episodes
        return ShowEpisodesPool(podcastId = info.podcastId, inOrder = false, episodes = newest)
    }
    val completedIds = playbackRepository.completedEpisodeIds.first()
    val loaded = seriesEpisodesLoader(info.podcastId).load(info.placeHistoryId, completedIds)
    val newest =
        try {
            podcastRepository.getEpisodesPaginated(
                info.podcastId,
                limit = HomeShowEpisodesLogic.NEWEST_FOR_IN_ORDER,
                offset = 0,
                sort = "newest",
            ).episodes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only the NEW tags and the caught-up list use these; the list itself still stands.
            android.util.Log.w("HomeViewModelFilteredView", "Newest episodes failed for ${info.podcastId}", e)
            emptyList()
        }
    return ShowEpisodesPool(
        podcastId = info.podcastId,
        inOrder = true,
        episodes = loaded.episodes,
        placeId = loaded.placeId,
        newest = newest,
    )
}

/** Reads a show listened to in order from the listener's place on; shared by the selected-show list and Daily mix. */
internal fun HomeViewModel.seriesEpisodesLoader(podcastId: String): SeriesEpisodesLoader = SeriesEpisodesLoader(
    window = { aroundId -> podcastRepository.getEpisodeWindow(podcastId, aroundEpisodeId = aroundId, sort = "oldest") },
    oldestPage = { offset ->
        val page =
            podcastRepository.getEpisodesPaginated(
                podcastId,
                limit = SeriesEpisodesLoader.PAGE_SIZE,
                offset = offset,
                sort = "oldest",
            )
        SeriesEpisodesLoader.Page(page.episodes, hasMore = page.hasMore, advance = page.sourceCount)
    },
    groupTokens = { ids -> playbackRepository.episodeGroupTokens(ids) },
)

internal fun HomeViewModel.observeDirectFeedRefresh() {
    viewModelScope.launch {
        val sync = SharedAppDependenciesHolder.instance?.subscriptionForegroundSync ?: return@launch
        sync.directFeedRefreshed.collect { podcastId ->
            if (HomeForegroundSyncLogic.shouldReloadSelectedChip(podcastId, _selectedPodcastId.value)) {
                _rssRefreshVersion.value += 1L
            }
        }
    }
}

/**
 * Re-ranks Your shows and rebuilds Daily Mix once synced data settles: new episodes, resolved
 * serial episodes, or subscriptions arriving from an import or cloud sync. Playback progress never
 * changes the catalog signature, so it never triggers a pass.
 */
internal fun HomeViewModel.observeYourShowsCatalog() {
    viewModelScope.launch(Dispatchers.Default) {
        combine(subscriptionRepository.subscribedPodcasts, _resolvedSerialEpisodes, HomeYourShowsSignals::catalog)
            .settledCatalogChanges()
            .collect { showsOrderRefreshGeneration.update { it + 1L } }
    }
}
