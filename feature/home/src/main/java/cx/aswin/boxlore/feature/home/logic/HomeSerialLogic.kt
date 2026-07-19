package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem

internal data class SerialProgressIds(
    val completed: Set<String>,
    val inProgress: Set<String>,
)

internal object HomeSerialLogic {
    fun progressIds(
        allHistory: List<HomeListeningHistoryItem>,
        completedEpisodeIds: Set<String>,
    ): SerialProgressIds {
        val completed =
            allHistory.filter { it.isCompleted }.map { it.episodeId }.toSet() + completedEpisodeIds
        val inProgress =
            allHistory.filter { !it.isCompleted && it.progressMs > 0L }.map { it.episodeId }.toSet()
        return SerialProgressIds(completed = completed, inProgress = inProgress)
    }

    fun ongoingEpisodeId(
        allHistory: List<HomeListeningHistoryItem>,
        podcastId: String,
    ): String? =
        allHistory
            .filter { h -> h.podcastId == podcastId && !h.isCompleted && h.progressMs > 0L }
            .maxByOrNull { it.lastPlayedAt }
            ?.episodeId

    fun lastCompletedEpisodeId(
        allHistory: List<HomeListeningHistoryItem>,
        podcastId: String,
    ): String? =
        allHistory
            .filter { h -> h.podcastId == podcastId && h.isCompleted }
            .maxByOrNull { it.lastPlayedAt }
            ?.episodeId

    fun findPendingSerialPodcasts(
        subs: List<Podcast>,
        allHistory: List<HomeListeningHistoryItem>,
        completedEpisodeIds: Set<String>,
        resolvedSerial: Map<String, Episode>,
        inFlightResolutions: Set<String>,
    ): List<Podcast> {
        val progress = progressIds(allHistory, completedEpisodeIds)
        return subs.filter { (it.preferredSort ?: "newest") == "oldest" }.filter { pod ->
            val currentResolved = resolvedSerial[pod.id]
            val needsResolve =
                currentResolved == null ||
                    currentResolved.id in progress.completed ||
                    currentResolved.id in progress.inProgress
            needsResolve && pod.id !in inFlightResolutions
        }
    }
}
