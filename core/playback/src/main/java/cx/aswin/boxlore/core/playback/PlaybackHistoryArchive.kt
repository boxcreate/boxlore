package cx.aswin.boxlore.core.playback

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeListenerStore
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.dismissedAtValue
import cx.aswin.boxlore.core.model.Episode

/**
 * Archives [episodes] (or brings them back, [dismissed] false) in one transaction: the decision
 * reaches every trusted ID of each episode and its group settings ([EpisodeListenerStore.setDismissed]),
 * and an episode with no history row gets its owner's row, carrying only the decision. Progress,
 * completion and likes stay as they are, so an archived episode never counts as listened.
 */
internal suspend fun PlaybackHistoryStore.setEpisodesDismissed(
    episodes: List<Episode>,
    podcastId: String,
    podcastTitle: String,
    podcastImageUrl: String?,
    dismissed: Boolean,
) {
    if (episodes.isEmpty()) return
    val now = System.currentTimeMillis()
    BoxLoreDatabase.getDatabase(player.context).withTransaction {
        val missing = listenerStore.setDismissed(episodes.map { it.id }, dismissed, now).toSet()
        // Bringing back an episode that was never archived needs no row.
        if (!dismissed) return@withTransaction
        for (episode in episodes.filter { it.id in missing }.distinctBy { it.id }) {
            val built =
                ListeningHistoryEntity(
                    episodeId = listenerStore.historyId(episode.id),
                    podcastId = episode.podcastId?.takeIf { it.isNotBlank() } ?: podcastId,
                    episodeTitle = episode.title,
                    episodeImageUrl = episode.imageUrl,
                    podcastImageUrl = podcastImageUrl,
                    episodeAudioUrl = episode.audioUrl,
                    podcastName = podcastTitle,
                    progressMs = 0L,
                    durationMs = episode.duration * 1000L,
                    isCompleted = false,
                    lastPlayedAt = 0L,
                    isDirty = true,
                    enclosureType = episode.enclosureType,
                    episodeDescription = episode.description,
                    dismissedAt = dismissedAtValue(dismissed = true, at = now),
                )
            data.listeningHistoryDao.upsert(listenerStore.ownerRow(built, episode.id))
            // An episode with a trusted alias records the decision for the IDs found later.
            listenerStore.setDismissed(episode.id, dismissed = true, nowMs = now)
        }
    }
}
