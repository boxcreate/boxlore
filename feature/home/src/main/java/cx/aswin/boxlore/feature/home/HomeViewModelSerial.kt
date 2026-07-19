package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.logic.HomeSerialLogic
import cx.aswin.boxlore.feature.home.logic.resolveNextSerialEpisode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun HomeViewModel.startOldestSortResolution() {
    viewModelScope.launch {
        // 3-second startup delay
        kotlinx.coroutines.delay(3000)

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
                                    try {
                                        val ongoingId =
                                            HomeSerialLogic.ongoingEpisodeId(allHistory, pod.id)
                                        val lastCompletedId =
                                            HomeSerialLogic.lastCompletedEpisodeId(allHistory, pod.id)

                                        android.util.Log.d(
                                            "HomeViewModelResolve",
                                            "Resolving pod=${pod.title} id=${pod.id} ongoingId=$ongoingId lastCompletedId=$lastCompletedId",
                                        )

                                        val page =
                                            podcastRepository.getEpisodesPaginated(
                                                pod.id,
                                                limit = 200,
                                                offset = 0,
                                                sort = "oldest",
                                            )
                                        val nextEp =
                                            resolveNextSerialEpisode(
                                                allEpisodes = page.episodes,
                                                ongoingId = ongoingId,
                                                lastCompletedId = lastCompletedId,
                                                completedEpIdsForResolve = progress.completed,
                                                inProgressEpIdsForResolve = progress.inProgress,
                                            )
                                        if (nextEp != null) pod.id to nextEp else null
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

                        val results = deferredList.awaitAll().filterNotNull()
                        if (results.isNotEmpty()) {
                            _resolvedSerialEpisodes.update { current -> current + results }
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
): List<Podcast> =
    HomeSerialLogic.findPendingSerialPodcasts(
        subs = subs,
        allHistory = allHistory,
        completedEpisodeIds = completedEpisodeIds,
        resolvedSerial = _resolvedSerialEpisodes.value,
        inFlightResolutions = inFlightResolutions,
    )
