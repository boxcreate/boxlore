package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem

internal data class CatchUpBuckets(
    val unplayed: List<Podcast>,
    val inProgress: List<Pair<Podcast, Long>>,
    val completed: List<Pair<Podcast, Long>>,
)

internal object HomeCatchUpLogic {
    fun resolveFreshEpisode(
        pod: Podcast,
        resolvedSerial: Map<String, Episode>,
    ): Episode? {
        val sort = pod.preferredSort ?: "newest"
        return if (sort == "oldest") {
            resolvedSerial[pod.id] ?: pod.latestEpisode
        } else {
            pod.latestEpisode
        }
    }

    /**
     * [allHistory] (one row per episode, under its owner's ID) with a copy of each row under every
     * other ID of its episode ([episodeAliases]), for lookups by the ID a show lists: a show switched
     * to its feed lists an episode under its feed ID while history keeps it under the old one. Not for
     * counting. Unchanged when no episode has several IDs.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8; decision D8).
     */
    fun underEveryId(allHistory: List<HomeListeningHistoryItem>, episodeAliases: Map<String, Set<String>>,): List<HomeListeningHistoryItem> {
        if (episodeAliases.isEmpty()) return allHistory
        val stored = allHistory.mapTo(HashSet()) { it.episodeId }
        val copies = allHistory.flatMap { row ->
            episodeAliases[row.episodeId].orEmpty().filter { it !in stored }.map { row.copy(episodeId = it) }
        }
        return if (copies.isEmpty()) allHistory else allHistory + copies
    }

    /**
     * Sorts each show's fresh episode into unplayed, in progress or finished. [completedEpisodeIds]
     * holds every completed episode, so one finished long ago, or among many marked played at
     * once, counts as finished even when [allHistory] (recent history) no longer holds its row.
     * An episode in [dismissedEpisodeIds] (archived) is left out of every bucket.
     */
    fun buildCatchUpBuckets(
        subs: List<Podcast>,
        allHistory: List<HomeListeningHistoryItem>,
        resolvedSerial: Map<String, Episode>,
        completedEpisodeIds: Set<String> = emptySet(),
        dismissedEpisodeIds: Set<String> = emptySet(),
    ): CatchUpBuckets {
        val unplayedBucket = mutableListOf<Podcast>()
        val inProgressBucket = mutableListOf<Pair<Podcast, Long>>()
        val completedBucket = mutableListOf<Pair<Podcast, Long>>()

        if (subs.isEmpty()) {
            return CatchUpBuckets(unplayedBucket, inProgressBucket, completedBucket)
        }

        try {
            for (pod in subs) {
                // An archived episode is in no bucket: it is neither new nor played.
                val freshEpisode = resolveFreshEpisode(pod, resolvedSerial)?.takeIf { it.id !in dismissedEpisodeIds } ?: continue
                val freshEpisodeWithContext =
                    freshEpisode.copy(
                        podcastTitle = pod.title,
                        podcastId = pod.id,
                    )
                val history = allHistory.find { it.episodeId == freshEpisodeWithContext.id }
                val isCompleted = freshEpisodeWithContext.id in completedEpisodeIds || history?.isCompleted == true

                when {
                    isCompleted -> {
                        completedBucket.add(
                            pod.copy(
                                latestEpisode = freshEpisodeWithContext,
                                resumeProgress = 1f,
                                episodeStatus = EpisodeStatus.COMPLETED,
                            ) to (history?.lastPlayedAt ?: 0L),
                        )
                    }
                    history == null || history.progressMs == 0L -> {
                        unplayedBucket.add(
                            pod.copy(
                                latestEpisode = freshEpisodeWithContext,
                                episodeStatus = EpisodeStatus.UNPLAYED,
                            ),
                        )
                    }
                    else -> {
                        inProgressBucket.add(
                            pod.copy(
                                latestEpisode = freshEpisodeWithContext,
                                resumeProgress = progressOf(history),
                                episodeStatus = EpisodeStatus.IN_PROGRESS,
                            ) to history.lastPlayedAt,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return CatchUpBuckets(
            unplayed = unplayedBucket,
            inProgress = inProgressBucket,
            completed = completedBucket,
        )
    }

    private fun progressOf(history: HomeListeningHistoryItem): Float =
        if (history.durationMs > 0) (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f) else 0f
}
