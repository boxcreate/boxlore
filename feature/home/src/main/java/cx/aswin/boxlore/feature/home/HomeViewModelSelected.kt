package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.playback.completedEpisodeIds
import cx.aswin.boxlore.feature.home.logic.HomeForegroundSyncLogic
import cx.aswin.boxlore.feature.home.logic.HomeSelectedPodcastLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun HomeViewModel.observeSelectedPodcast() {
    viewModelScope.launch {
        val historySignal =
            combine(
                _selectedPodcastId,
                allHomeHistory,
                subscriptionRepository.subscribedPodcasts,
                _rssRefreshVersion,
            ) { podcastId, allHistory, subs, rssRefreshVersion ->
                HomeSelectedPodcastLogic.buildSignal(
                    podcastId = podcastId,
                    allHistory = allHistory,
                    subs = subs,
                    rssRefreshVersion = rssRefreshVersion,
                )
            }.distinctUntilChanged()

        var previouslyLoadedPodcastId: String? = null
        historySignal.collectLatest { info ->
            if (info == null) {
                android.util.Log.d("HomeViewModelFilteredView", "historySignal collected: null (clearing selected podcast episodes)")
                _selectedPodcastEpisodes.value = emptyList()
                _isSelectedPodcastLoading.value = false
                previouslyLoadedPodcastId = null
            } else {
                val (podcastId, lastPlayedEpisodeId, sort) = info
                android.util.Log.d(
                    "HomeViewModelFilteredView",
                    "historySignal collected: podcastId=$podcastId, lastPlayedEpisodeId=$lastPlayedEpisodeId, sort=$sort",
                )
                val switchingPodcast = podcastId != previouslyLoadedPodcastId
                if (switchingPodcast) {
                    _selectedPodcastEpisodes.value = emptyList()
                    previouslyLoadedPodcastId = podcastId
                }
                val showLoading =
                    HomeSelectedPodcastLogic.shouldShowLoading(
                        switchingPodcast = switchingPodcast,
                        listEmpty = _selectedPodcastEpisodes.value.isEmpty(),
                    )
                _isSelectedPodcastLoading.value = showLoading
                try {
                    if (sort == "oldest") {
                        val page =
                            podcastRepository.getEpisodesPaginated(
                                podcastId,
                                limit = 100,
                                offset = 0,
                                sort = "oldest",
                            )
                        val selectedRaw =
                            HomeSelectedPodcastLogic.oldestSortWindow(
                                allEpisodes = page.episodes,
                                lastPlayedEpisodeId = lastPlayedEpisodeId,
                            )
                        android.util.Log.d(
                            "HomeViewModelFilteredView",
                            "Oldest sort resolution for podcastId=$podcastId: " +
                                "totalEpisodesFetched=${page.episodes.size}, " +
                                "rawEpisodesSelectedCount=${selectedRaw.size}",
                        )
                        _selectedPodcastEpisodes.value = selectedRaw
                    } else {
                        val page =
                            podcastRepository.getEpisodesPaginated(
                                podcastId,
                                limit = 25,
                                offset = 0,
                                sort = "newest",
                            )
                        _selectedPodcastEpisodes.value = page.episodes
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModelFilteredView", "Failed to fetch episodes for filter: $podcastId", e)
                    if (showLoading) {
                        _selectedPodcastEpisodes.value = emptyList()
                    }
                } finally {
                    _isSelectedPodcastLoading.value = false
                }
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
        combine(
            _selectedPodcastId,
            _selectedPodcastEpisodes,
            _isSelectedPodcastLoading,
            userPrefs.hideCompletedInHomeStream,
            playbackRepository.completedEpisodeIds,
        ) { id, eps, loading, hideCompleted, completedIds ->
            val filteredEps =
                HomeSelectedPodcastLogic.filterCompletedIfNeeded(
                    episodes = eps,
                    hideCompleted = hideCompleted,
                    completedIds = completedIds,
                )
            Triple(id, filteredEps, loading)
        }.collect { (id, eps, loading) ->
            _uiState.update {
                it.copy(
                    selectedPodcastId = id,
                    selectedPodcastEpisodes = eps,
                    isSelectedPodcastLoading = loading,
                )
            }
        }
    }
}

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
