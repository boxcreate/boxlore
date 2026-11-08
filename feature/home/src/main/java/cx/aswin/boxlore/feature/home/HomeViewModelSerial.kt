package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.listenerShowIds
import cx.aswin.boxlore.core.playback.completedEpisodeIds
import cx.aswin.boxlore.core.playback.episodeGroupTokens
import cx.aswin.boxlore.feature.home.logic.HomeSerialLogic
import cx.aswin.boxlore.feature.home.logic.HomeShowEpisodesLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun HomeViewModel.startOldestSortResolution() {
    viewModelScope.launch(Dispatchers.Default) {
        // Wait for usable local Home content instead of racing a fixed startup timer.
        uiState.first { !it.isLoading }
        val resolutionLimiter = HomeSerialResolutionLimiter()

        combine(
            subscriptionRepository.subscribedPodcasts,
            allHomeHistory,
            playbackRepository.completedEpisodeIds,
        ) { subs, allHistory, completedEpisodeIds ->
            Triple(subs, allHistory, completedEpisodeIds)
        }.collect { (subs, allHistory, completedEpisodeIds) ->
            val progress = HomeSerialLogic.progressIds(allHistory, completedEpisodeIds)
            val serialPodsToResolve = findPendingSerialPodcasts(subs, allHistory, completedEpisodeIds)

            if (serialPodsToResolve.isNotEmpty()) {
                serialPodsToResolve.forEach { inFlightResolutions.add(it.id) }

                launch(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        val deferredList =
                            serialPodsToResolve.map { pod ->
                                async {
                                    resolutionLimiter.resolve {
                                        try {
                                            // A show switched to its feed reads its old show's listening.
                                            val place = HomeShowEpisodesLogic.placeHistoryId(allHistory, pod.listenerShowIds)
                                            android.util.Log.d(
                                                "HomeViewModelResolve",
                                                "Resolving pod=${pod.title} id=${pod.id} place=$place",
                                            )
                                            val loaded = seriesEpisodesLoader(pod.id).load(place, progress.completed)
                                            // History keeps an episode under its owner ID; find it under the ID the list uses.
                                            val tokens =
                                                playbackRepository.episodeGroupTokens(
                                                    loaded.episodes.map { it.id } + progress.inProgress,
                                                )
                                            val nextEp =
                                                HomeShowEpisodesLogic.nextUp(
                                                    episodes = loaded.episodes,
                                                    placeId = loaded.placeId,
                                                    completedIds = progress.completed,
                                                    inProgressIds = HomeSerialLogic.idsInList(progress.inProgress, loaded.episodes, tokens),
                                                )
                                            SerialResolution(pod.id, nextEp, HomeSerialLogic.resolveKey(pod, allHistory))
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            android.util.Log.e(
                                                "HomeViewModel",
                                                "Failed to resolve next episode for serial pod ${pod.id}",
                                                e,
                                            )
                                            null
                                        }
                                    }
                                }
                            }

                        val results = deferredList.awaitAll().filterNotNull()
                        results.forEach { result ->
                            if (result.next == null) serialCaughtUpKeys[result.podcastId] = result.key else serialCaughtUpKeys.remove(result.podcastId)
                        }
                        if (results.isNotEmpty()) {
                            // A show with nothing left drops its old suggestion; Daily mix then falls back to its newest episode.
                            _resolvedSerialEpisodes.update { current ->
                                results.fold(current) { acc, result ->
                                    if (result.next != null) acc + (result.podcastId to result.next) else acc - result.podcastId
                                }
                            }
                        }
                    } finally {
                        serialPodsToResolve.forEach { inFlightResolutions.remove(it.id) }
                    }
                }
            }
        }
    }
}

internal fun HomeViewModel.findPendingSerialPodcasts(
    subs: List<Podcast>,
    allHistory: List<HomeListeningHistoryItem>,
    completedEpisodeIds: Set<String>,
): List<Podcast> = HomeSerialLogic.findPendingSerialPodcasts(
    subs = subs,
    allHistory = allHistory,
    completedEpisodeIds = completedEpisodeIds,
    resolvedSerial = _resolvedSerialEpisodes.value,
    inFlightResolutions = inFlightResolutions,
    caughtUpKeys = serialCaughtUpKeys,
)

/** One show's resolution: its next episode, or null when it has nothing left, at the [key] it was checked at. */
private data class SerialResolution(val podcastId: String, val next: Episode?, val key: String)
