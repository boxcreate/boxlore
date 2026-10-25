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

    fun buildCatchUpBuckets(
        subs: List<Podcast>,
        allHistory: List<HomeListeningHistoryItem>,
        resolvedSerial: Map<String, Episode>,
    ): CatchUpBuckets {
        val unplayedBucket = mutableListOf<Podcast>()
        val inProgressBucket = mutableListOf<Pair<Podcast, Long>>()
        val completedBucket = mutableListOf<Pair<Podcast, Long>>()

        if (subs.isEmpty()) {
            return CatchUpBuckets(unplayedBucket, inProgressBucket, completedBucket)
        }

        try {
            for (pod in subs) {
                val freshEpisode = resolveFreshEpisode(pod, resolvedSerial) ?: continue
                val freshEpisodeWithContext =
                    freshEpisode.copy(
                        podcastTitle = pod.title,
                        podcastId = pod.id,
                    )
                val history = allHistory.find { it.episodeId == freshEpisodeWithContext.id }

                when {
                    history == null || (history.progressMs == 0L && !history.isCompleted) -> {
                        unplayedBucket.add(
                            pod.copy(
                                latestEpisode = freshEpisodeWithContext,
                                episodeStatus = EpisodeStatus.UNPLAYED,
                            ),
                        )
                    }
                    !history.isCompleted && history.progressMs > 0L -> {
                        val progress =
                            if (history.durationMs > 0) {
                                (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        inProgressBucket.add(
                            pod.copy(
                                latestEpisode = freshEpisodeWithContext,
                                resumeProgress = progress,
                                episodeStatus = EpisodeStatus.IN_PROGRESS,
                            ) to history.lastPlayedAt,
                        )
                    }
                    history.isCompleted -> {
                        completedBucket.add(
                            pod.copy(
                                latestEpisode = freshEpisodeWithContext,
                                resumeProgress = 1f,
                                episodeStatus = EpisodeStatus.COMPLETED,
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
}
