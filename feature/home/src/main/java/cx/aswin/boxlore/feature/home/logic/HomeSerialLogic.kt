package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.listenerShowIds
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

    /**
     * The latest unfinished episode of a show whose listener rows are under [showIds]
     * (`Podcast.listenerShowIds`: a show switched to its feed also reads its old show's rows).
     */
    fun ongoingEpisodeId(
        allHistory: List<HomeListeningHistoryItem>,
        showIds: Set<String>,
    ): String? = allHistory
        .filter { h -> h.podcastId in showIds && !h.isCompleted && h.progressMs > 0L }
        .maxByOrNull { it.lastPlayedAt }
        ?.episodeId

    /** The latest finished episode of a show whose listener rows are under [showIds], as [ongoingEpisodeId]. */
    fun lastCompletedEpisodeId(
        allHistory: List<HomeListeningHistoryItem>,
        showIds: Set<String>,
    ): String? = allHistory
        .filter { h -> h.podcastId in showIds && h.isCompleted }
        .maxByOrNull { it.lastPlayedAt }
        ?.episodeId

    /**
     * [id] as the ID [episodes] lists for the same episode, found through [tokens] (one token per
     * episode, `episodeGroupTokens`); [id] itself when the list holds it or no other ID of it.
     * History keeps an episode under its owner ID, which may not be the ID the show's list uses.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8; decision D8).
     */
    fun idInList(id: String?, episodes: List<Episode>, tokens: Map<String, String>,): String? {
        id ?: return null
        val token = tokens[id] ?: id
        return episodes.firstOrNull { (tokens[it.id] ?: it.id) == token }?.id ?: id
    }

    /** [ids] plus the IDs [episodes] lists for the same episodes ([idInList]). */
    fun idsInList(ids: Set<String>, episodes: List<Episode>, tokens: Map<String, String>,): Set<String> {
        val wanted = ids.mapTo(HashSet()) { tokens[it] ?: it }
        return ids + episodes.filter { (tokens[it.id] ?: it.id) in wanted }.map { it.id }
    }

    /**
     * What a show's next episode depends on: the listener's place and the show's newest episode. A show
     * found to have nothing left at a key isn't checked again until the key changes, so playback
     * progress can't reload a finished series on every update.
     */
    fun resolveKey(
        pod: Podcast,
        allHistory: List<HomeListeningHistoryItem>,
    ): String = "${HomeShowEpisodesLogic.placeHistoryId(allHistory, pod.listenerShowIds)}|${pod.latestEpisode?.id}"

    fun findPendingSerialPodcasts(
        subs: List<Podcast>,
        allHistory: List<HomeListeningHistoryItem>,
        completedEpisodeIds: Set<String>,
        resolvedSerial: Map<String, Episode>,
        inFlightResolutions: Set<String>,
        caughtUpKeys: Map<String, String> = emptyMap(),
    ): List<Podcast> {
        val progress = progressIds(allHistory, completedEpisodeIds)
        return subs.filter { (it.preferredSort ?: "newest") == "oldest" }.filter { pod ->
            val currentResolved = resolvedSerial[pod.id]
            val needsResolve =
                currentResolved == null ||
                    currentResolved.id in progress.completed ||
                    currentResolved.id in progress.inProgress
            val caughtUpHere = currentResolved == null && caughtUpKeys[pod.id]?.let { it == resolveKey(pod, allHistory) } == true
            needsResolve && !caughtUpHere && pod.id !in inFlightResolutions
        }
    }
}
