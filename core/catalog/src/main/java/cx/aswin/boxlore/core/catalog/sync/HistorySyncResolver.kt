package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.persistableArtworkUrl
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto

/**
 * LWW (Last-Write-Wins) conflict resolver for listening history.
 * Guarantees active playing episode protection, progress LWW, independent like status LWW,
 * and preservation of local cached rich metadata.
 *
 * @param episodeIds every stored ID of an episode (`EpisodeListenerStore.memberIds`); just the ID
 *   when it has no other. The playing episode is protected under each of them, because its
 *   progress is written to its owner ID, which may differ from the ID playback started with
 *   (own-episode-IDs P4, plan section 8).
 */
class HistorySyncResolver(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val activePlaybackSyncPort: ActivePlaybackSyncPort? = null,
    private val episodeIds: suspend (String) -> Collection<String> = { listOf(it) },
) {
    suspend fun resolveHistoryItem(remote: ListeningHistorySyncDto, syncedAt: Long) {
        val local = listeningHistoryDao.getHistoryItem(remote.episodeId)
        if (local == null) {
            insertNewRemoteItem(remote, syncedAt)
            return
        }

        val activePlayingEpisodeId = activePlaybackSyncPort?.getActivePlayingEpisodeId()
        val isActive = activePlayingEpisodeId != null &&
            (remote.episodeId == activePlayingEpisodeId || remote.episodeId in episodeIds(activePlayingEpisodeId))
        resolveExistingItem(local, remote, isActive, syncedAt)
    }

    private suspend fun insertNewRemoteItem(remote: ListeningHistorySyncDto, syncedAt: Long) {
        val newEntity = ListeningHistoryEntity(
            episodeId = remote.episodeId,
            podcastId = remote.podcastId,
            episodeTitle = remote.episodeTitle ?: "",
            episodeImageUrl = persistableArtworkUrl(remote.episodeImageUrl),
            podcastImageUrl = persistableArtworkUrl(remote.podcastImageUrl),
            episodeAudioUrl = remote.episodeAudioUrl ?: "",
            podcastName = remote.podcastName ?: "",
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
    }

    private suspend fun resolveExistingItem(
        local: ListeningHistoryEntity,
        remote: ListeningHistorySyncDto,
        isActive: Boolean,
        syncedAt: Long,
    ) {
        val meta = mergeMetadata(local, remote)
        val (finalIsLiked, finalLikedAt) = if (remote.likedAt > local.likedAt) {
            remote.isLiked to remote.likedAt
        } else {
            local.isLiked to local.likedAt
        }

        if (isActive) {
            val updated = local.copy(
                episodeTitle = meta.episodeTitle,
                episodeImageUrl = meta.episodeImageUrl,
                podcastImageUrl = meta.podcastImageUrl,
                podcastName = meta.podcastName,
                episodeAudioUrl = meta.episodeAudioUrl,
                isLiked = finalIsLiked,
                likedAt = finalLikedAt,
                syncedAt = syncedAt,
            )
            listeningHistoryDao.upsert(updated)
            return
        }

        val updated = if (remote.lastPlayedAt > local.lastPlayedAt) {
            local.copy(
                episodeTitle = meta.episodeTitle,
                episodeImageUrl = meta.episodeImageUrl,
                podcastImageUrl = meta.podcastImageUrl,
                podcastName = meta.podcastName,
                episodeAudioUrl = meta.episodeAudioUrl,
                progressMs = remote.progressMs,
                durationMs = if (remote.durationMs > 0) remote.durationMs else local.durationMs,
                isCompleted = remote.isCompleted,
                lastPlayedAt = remote.lastPlayedAt,
                isLiked = finalIsLiked,
                likedAt = finalLikedAt,
                isDirty = local.isDirty && local.likedAt > remote.likedAt,
                syncedAt = syncedAt,
            )
        } else {
            local.copy(
                episodeTitle = meta.episodeTitle,
                episodeImageUrl = meta.episodeImageUrl,
                podcastImageUrl = meta.podcastImageUrl,
                podcastName = meta.podcastName,
                episodeAudioUrl = meta.episodeAudioUrl,
                isLiked = finalIsLiked,
                likedAt = finalLikedAt,
                syncedAt = syncedAt,
            )
        }
        listeningHistoryDao.upsert(updated)
    }

    private fun mergeMetadata(
        local: ListeningHistoryEntity,
        remote: ListeningHistorySyncDto,
    ): HistoryMetadata = HistoryMetadata(
        episodeTitle = remote.episodeTitle?.takeIf { it.isNotBlank() && local.episodeTitle.isBlank() }
            ?: local.episodeTitle,
        podcastName = remote.podcastName?.takeIf { it.isNotBlank() && local.podcastName.isBlank() }
            ?: local.podcastName,
        episodeImageUrl = persistableArtworkUrl(local.episodeImageUrl) ?: persistableArtworkUrl(remote.episodeImageUrl),
        podcastImageUrl = persistableArtworkUrl(local.podcastImageUrl) ?: persistableArtworkUrl(remote.podcastImageUrl),
        episodeAudioUrl = if (local.episodeAudioUrl.isNullOrBlank() && !remote.episodeAudioUrl.isNullOrBlank()) {
            remote.episodeAudioUrl
        } else {
            local.episodeAudioUrl
        },
    )

    private data class HistoryMetadata(
        val episodeTitle: String,
        val podcastName: String,
        val episodeImageUrl: String?,
        val podcastImageUrl: String?,
        val episodeAudioUrl: String?,
    )
}
