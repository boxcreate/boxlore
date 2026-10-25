package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.ListenerShows
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.listenerShowIds

/**
 * @property listenerShowIds the shows whose history counts as this show's (`Podcast.listenerShowIds`):
 *   the show, and the Podcast Index show an RSS show replaced (own-episode-IDs P4, plan section 8; decision D8)
 */
data class ScorablePodcast(
    val id: String,
    val subscribedAt: Long,
    val latestEpisode: Episode?,
    val notificationsEnabled: Boolean,
    val autoDownloadEnabled: Boolean,
    val listenerShowIds: Set<String> = setOf(id),
)

object PodcastScoring {
    /** Peak subscribe-recency prior. Decays as 600 / (1 + hours/24); ~150 at 72h. */
    const val SUBSCRIBE_RECENCY_PEAK = 600.0

    /**
     * @param episodeKey maps an episode ID to a key shared by every stored ID of the episode, so the
     *   latest episode is found in history under any of its IDs; [allHistory] should then hold one
     *   row per episode. The default keeps each ID its own episode (own-episode-IDs P4, plan section 8).
     */
    fun calculateScores(
        podcasts: List<ScorablePodcast>,
        allHistory: List<ListeningHistoryEntity>,
        includeAutoDownloadBoost: Boolean = true,
        includeSubscriptionRecency: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
        episodeKey: (String) -> String = { it },
    ): Map<String, Double> {
        val historyByPodcast = allHistory.groupBy { it.podcastId }
        val historyByEpisode = allHistory.associateBy { episodeKey(it.episodeId) }
        val scoredIds = podcasts.mapTo(HashSet()) { it.id }

        return podcasts.associate { pod ->
            // A show the listener switched to its feed also counts the listening its old show holds,
            // unless that show is scored on its own.
            val podHistory = pod.listenerShowIds
                .filter { it == pod.id || it !in scoredIds }
                .flatMap { historyByPodcast[it].orEmpty() }
            val playCount = podHistory.size
            val likeCount = podHistory.count { it.isLiked }
            val playScore = 12.0 * playCount
            val likeScore = 25.0 * likeCount

            val lastPlayTime = podHistory.maxOfOrNull { it.lastPlayedAt }
            val playRecencyScore =
                if (lastPlayTime != null) {
                    val hoursSinceLastPlay = (nowMs - lastPlayTime).toDouble() / (1000.0 * 3600.0)
                    250.0 / (1.0 + hoursSinceLastPlay.coerceAtLeast(0.0) / 24.0)
                } else {
                    0.0
                }

            val latestEp = pod.latestEpisode
            val freshnessScore =
                if (latestEp != null) {
                    val latestEpHistory = historyByEpisode[episodeKey(latestEp.id)]
                    val isUnplayed =
                        latestEpHistory == null ||
                            (latestEpHistory.progressMs == 0L && !latestEpHistory.isCompleted)
                    val releasedAfterSub = latestEp.publishedDate > (pod.subscribedAt / 1000L)
                    if (isUnplayed && releasedAfterSub) {
                        val hoursSinceRelease = (nowMs / 1000.0 - latestEp.publishedDate) / 3600.0
                        (150.0 / (1.0 + hoursSinceRelease.coerceAtLeast(0.0) / 24.0)) + 80.0
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }

            val subRecencyScore =
                if (includeSubscriptionRecency && pod.subscribedAt > 0L) {
                    val hoursSinceSubscribed =
                        (nowMs - pod.subscribedAt).toDouble() / (1000.0 * 3600.0)
                    SUBSCRIBE_RECENCY_PEAK / (1.0 + hoursSinceSubscribed.coerceAtLeast(0.0) / 24.0)
                } else {
                    0.0
                }

            val notificationsBoost = if (pod.notificationsEnabled) 30.0 else 0.0
            val autoDownloadBoost =
                if (includeAutoDownloadBoost && pod.autoDownloadEnabled) 60.0 else 0.0

            pod.id to (
                playScore +
                    likeScore +
                    playRecencyScore +
                    freshnessScore +
                    subRecencyScore +
                    notificationsBoost +
                    autoDownloadBoost
                )
        }
    }
}

fun Podcast.toScorable() = ScorablePodcast(
    id = id,
    subscribedAt = subscribedAt,
    latestEpisode = latestEpisode,
    notificationsEnabled = notificationsEnabled,
    autoDownloadEnabled = autoDownloadEnabled,
    listenerShowIds = listenerShowIds,
)

fun PodcastEntity.toScorable() = ScorablePodcast(
    id = podcastId,
    subscribedAt = subscribedAt,
    latestEpisode = latestEpisode,
    notificationsEnabled = notificationsEnabled,
    autoDownloadEnabled = autoDownloadEnabled,
    listenerShowIds = listenerShowIds,
)

/**
 * The shows whose listener rows count as this show's (`Podcast.listenerShowIds`): the show, and the
 * Podcast Index show an RSS show replaced.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 * COMPAT(own-ids): a switched show reads its old show's listening. Permanent: never remove (MIGRATION.md §5).
 */
val PodcastEntity.listenerShowIds: Set<String>
    get() = ListenerShows.idsOf(podcastId, isRss, linkedPodcastIndexId)

/**
 * [this] shows with the most history rows first ("Most listened"); ties keep their order. A show
 * the listener switched to its feed counts the rows its old Podcast Index show holds
 * ([Podcast.listenerShowIds]).
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 */
fun List<Podcast>.sortedByListening(history: List<ListeningHistoryEntity>): List<Podcast> {
    val counts = history.groupingBy { it.podcastId }.eachCount()
    return sortedByDescending { podcast -> podcast.listenerShowIds.sumOf { counts[it] ?: 0 } }
}
