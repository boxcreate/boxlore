package cx.aswin.boxlore.core.catalog.ports

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Narrow history seam for [cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager].
 *
 * Stays in `:core:data` (not `:core:domain`) because it exposes Room
 * [ListeningHistoryEntity]. Production: [cx.aswin.boxlore.core.playback.PlaybackRepository]
 * in `:core:playback`. Keeps `:core:data` free of a dependency on `:core:playback`.
 */
interface ListeningHistoryBackupPort {
    /** Recent history for display and ranking; an episode stored under several IDs may appear once. */
    fun getAllHistory(): Flow<List<ListeningHistoryEntity>>

    /**
     * Every history row exactly as stored, one per stored episode ID, for library export: nothing
     * is merged or dropped, and nothing is left out for age (the screens show only the latest 300).
     * Defaults to [getAllHistory] for ports that store each episode once.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
     */
    suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = getAllHistory().first()

    suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity)

    suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?,)
}
