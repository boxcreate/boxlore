package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto

/**
 * LWW (Last-Write-Wins) conflict resolver for listening history.
 * Guarantees active playing episode protection, progress LWW, independent like status LWW,
 * and preservation of local cached rich metadata.
 */
class HistorySyncResolver(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val activePlaybackSyncPort: ActivePlaybackSyncPort? = null,
) {
    suspend fun resolveHistoryItem(remote: ListeningHistorySyncDto, syncedAt: Long) {
        val activePlayingEpisodeId = activePlaybackSyncPort?.getActivePlayingEpisodeId()
        val local = listeningHistoryDao.getHistoryItem(remote.episodeId)

        if (local == null) {
            // New history row from cloud
            val newEntity = ListeningHistoryEntity(
                episodeId = remote.episodeId,
                podcastId = remote.podcastId,
                episodeTitle = "",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "",
                podcastName = "",
                progressMs = remote.progressMs,
                durationMs = remote.durationMs,
                isCompleted = remote.isCompleted,
                isLiked = remote.isLiked,
                likedAt = remote.likedAt,
                lastPlayedAt = remote.lastPlayedAt,
                isDirty = false,
                syncedAt = syncedAt,
            )
            listeningHistoryDao.upsert(newEntity)
            return
        }

        val isActive = remote.episodeId == activePlayingEpisodeId

        // 1. Independent like status LWW
        val (finalIsLiked, finalLikedAt) = if (remote.likedAt > local.likedAt) {
            remote.isLiked to remote.likedAt
        } else {
            local.isLiked to local.likedAt
        }

        // 2. Playback progress and completion merge
        if (isActive) {
            // Active playing episode shield: never overwrite local playback progress
            val updated = local.copy(
                isLiked = finalIsLiked,
                likedAt = finalLikedAt,
                syncedAt = syncedAt,
            )
            listeningHistoryDao.upsert(updated)
            return
        }

        val updated = if (remote.lastPlayedAt > local.lastPlayedAt) {
            // Remote was listened to more recently
            local.copy(
                progressMs = remote.progressMs,
                durationMs = if (remote.durationMs > 0) remote.durationMs else local.durationMs,
                isCompleted = remote.isCompleted,
                lastPlayedAt = remote.lastPlayedAt,
                isLiked = finalIsLiked,
                likedAt = finalLikedAt,
                isDirty = false,
                syncedAt = syncedAt,
            )
        } else {
            // Local is newer or equal
            local.copy(
                isLiked = finalIsLiked,
                likedAt = finalLikedAt,
                syncedAt = syncedAt,
            )
        }
        listeningHistoryDao.upsert(updated)
    }
}
