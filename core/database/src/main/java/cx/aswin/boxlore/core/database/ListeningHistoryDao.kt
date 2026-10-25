package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@Dao
interface ListeningHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ListeningHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(history: ListeningHistoryEntity): Long

    @Query(
        """
        UPDATE listening_history
        SET podcastId = CASE WHEN podcastId = '' THEN :podcastId ELSE podcastId END,
            episodeTitle = CASE WHEN episodeTitle = '' THEN :episodeTitle ELSE episodeTitle END,
            episodeImageUrl = COALESCE(episodeImageUrl, :episodeImageUrl),
            podcastImageUrl = COALESCE(podcastImageUrl, :podcastImageUrl),
            episodeAudioUrl = COALESCE(episodeAudioUrl, :episodeAudioUrl),
            podcastName = CASE WHEN podcastName = '' THEN :podcastName ELSE podcastName END,
            durationMs = CASE WHEN durationMs <= 0 AND :durationMs > 0 THEN :durationMs ELSE durationMs END,
            enclosureType = COALESCE(enclosureType, :enclosureType),
            episodeDescription = COALESCE(episodeDescription, :episodeDescription)
        WHERE episodeId = :episodeId
        """,
    )
    @Suppress("LongParameterList", "kotlin:S107")
    suspend fun enrichMetadataIfMissing(
        episodeId: String,
        podcastId: String,
        episodeTitle: String,
        episodeImageUrl: String?,
        podcastImageUrl: String?,
        episodeAudioUrl: String?,
        podcastName: String,
        durationMs: Long,
        enclosureType: String?,
        episodeDescription: String?,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(historyItems: List<ListeningHistoryEntity>)

    // Fetch Top 7 items for the new Split UI (1st is Hero, 2-7 are Grid)
    @Query(
        """
        SELECT episodeId, podcastId, episodeTitle, episodeImageUrl, podcastImageUrl, 
               episodeAudioUrl, podcastName, progressMs, durationMs, isCompleted, 
               isLiked, likedAt, lastPlayedAt, isDirty, syncedAt, enclosureType, 
               isManualCompletion, isBulkCompletion, NULL as episodeDescription 
        FROM listening_history 
        WHERE isCompleted = 0 AND progressMs > 0 
        ORDER BY lastPlayedAt DESC 
        LIMIT 7
    """,
    )
    fun getResumeItems(): Flow<List<ListeningHistoryEntity>>

    // Suspend version for Android Auto browse tree (non-Flow, one-shot)
    @Query("SELECT * FROM listening_history WHERE isCompleted = 0 AND progressMs > 0 ORDER BY lastPlayedAt DESC LIMIT 20")
    suspend fun getResumeItemsList(): List<ListeningHistoryEntity>

    @Query(
        """
        SELECT episodeId, podcastId, episodeTitle, episodeImageUrl, podcastImageUrl, 
               episodeAudioUrl, podcastName, progressMs, durationMs, isCompleted, 
               isLiked, likedAt, lastPlayedAt, isDirty, syncedAt, enclosureType, 
               isManualCompletion, isBulkCompletion, NULL as episodeDescription 
        FROM listening_history 
        ORDER BY lastPlayedAt DESC
        LIMIT 300
    """,
    )
    fun getAllHistory(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history WHERE isDirty = 1")
    suspend fun getDirtyItems(): List<ListeningHistoryEntity>

    @Query("SELECT COUNT(*) FROM listening_history WHERE isDirty = 1")
    fun getDirtyCountFlow(): Flow<Int>

    @Query("UPDATE listening_history SET isDirty = 0, syncedAt = :timestamp WHERE episodeId IN (:ids)")
    suspend fun markAsSynced(ids: List<String>, timestamp: Long,)

    @Query("DELETE FROM listening_history WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)

    @Query("DELETE FROM listening_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM listening_history WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getHistoryItem(episodeId: String): ListeningHistoryEntity?

    /**
     * History rows of these IDs, in no particular order; callers pass at most 500 IDs per call.
     * Used to read every stored ID of one episode ([EpisodeListenerStore]).
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
     */
    @Query("SELECT * FROM listening_history WHERE episodeId IN (:episodeIds)")
    suspend fun getHistoryItems(episodeIds: List<String>): List<ListeningHistoryEntity>

    /**
     * Writes one like decision to the rows of these IDs (an episode's trusted IDs), marking them for
     * sync. [now] is the decision time, the same for every row. Returns the rows written.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, absolute like writes).
     */
    @Query("UPDATE listening_history SET isLiked = :isLiked, likedAt = :now, isDirty = 1 WHERE episodeId IN (:episodeIds)")
    suspend fun setLikeStatusForIds(episodeIds: List<String>, isLiked: Boolean, now: Long,): Int

    /**
     * Sets the completion of the rows of these IDs (an episode's IDs other than the one playback
     * wrote) and marks changed rows for sync. Clearing also clears how the episode was completed.
     * Progress is left alone: it is read from the episode's owner only. Returns the rows changed.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, absolute completion writes).
     */
    @Query(
        """
        UPDATE listening_history
        SET isCompleted = :isCompleted,
            isManualCompletion = CASE WHEN :isCompleted THEN isManualCompletion ELSE 0 END,
            isBulkCompletion = CASE WHEN :isCompleted THEN isBulkCompletion ELSE 0 END,
            isDirty = 1
        WHERE episodeId IN (:episodeIds) AND isCompleted != :isCompleted
        """,
    )
    suspend fun setCompletionForIds(episodeIds: List<String>, isCompleted: Boolean,): Int

    @Query("SELECT * FROM listening_history WHERE podcastId = :podcastId")
    suspend fun getHistoryForPodcast(podcastId: String): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history WHERE episodeId = :episodeId LIMIT 1")
    fun getHistoryItemFlow(episodeId: String): Flow<ListeningHistoryEntity?>

    // Get the most recent incomplete session (for resume cards)
    @Query("SELECT * FROM listening_history WHERE isCompleted = 0 ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getLastPlayedSession(): ListeningHistoryEntity?

    // Get the most recent session regardless of completion (for miniplayer restore)
    @Query("SELECT * FROM listening_history ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getLastPlayedSessionAny(): ListeningHistoryEntity?

    // Like Feature
    @Query(
        """
        SELECT episodeId, podcastId, episodeTitle, episodeImageUrl, podcastImageUrl, 
               episodeAudioUrl, podcastName, progressMs, durationMs, isCompleted, 
               isLiked, likedAt, lastPlayedAt, isDirty, syncedAt, enclosureType, 
               isManualCompletion, isBulkCompletion, NULL as episodeDescription 
        FROM listening_history 
        WHERE isLiked = 1 
        ORDER BY lastPlayedAt DESC
    """,
    )
    fun getLikedEpisodes(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history WHERE isLiked = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getLikedEpisodesList(limit: Int = 50): List<ListeningHistoryEntity>

    @Query("UPDATE listening_history SET isLiked = :isLiked, likedAt = :now, isDirty = 1 WHERE episodeId = :episodeId")
    suspend fun setLikeStatus(episodeId: String, isLiked: Boolean, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE listening_history
        SET progressMs = :progressMs,
            durationMs = :durationMs,
            lastPlayedAt = :lastPlayedAt,
            isDirty = 1
        WHERE episodeId = :episodeId AND isCompleted = 0
        """,
    )
    suspend fun updateProgress(episodeId: String, progressMs: Long, durationMs: Long, lastPlayedAt: Long,): Int

    @Query(
        """
        UPDATE listening_history
        SET progressMs = :progressMs,
            durationMs = :durationMs,
            isCompleted = 0,
            isManualCompletion = 0,
            isBulkCompletion = 0,
            lastPlayedAt = :lastPlayedAt,
            isDirty = 1
        WHERE episodeId = :episodeId
        """,
    )
    suspend fun reopenProgress(
        episodeId: String,
        progressMs: Long,
        durationMs: Long,
        lastPlayedAt: Long,
    ): Int

    @Query("UPDATE listening_history SET lastPlayedAt = :lastPlayedAt, isDirty = 1 WHERE episodeId = :episodeId")
    suspend fun updateLastPlayedAt(episodeId: String, lastPlayedAt: Long,)

    @Query(
        """
        UPDATE listening_history
        SET progressMs = 0,
            durationMs = :durationMs,
            isCompleted = 1,
            isManualCompletion = :isManualCompletion,
            isBulkCompletion = 0,
            lastPlayedAt = :lastPlayedAt,
            isDirty = 1
        WHERE episodeId = :episodeId
        """,
    )
    suspend fun completeFromPlayback(episodeId: String, durationMs: Long, lastPlayedAt: Long, isManualCompletion: Boolean,)

    @Query("UPDATE listening_history SET isCompleted = :isCompleted, isDirty = 1 WHERE episodeId = :episodeId")
    suspend fun setCompletionStatus(episodeId: String, isCompleted: Boolean,)

    // Get all episode IDs that have been fully played (for "unplayed" filtering in queue).
    // Listeners with tens of thousands of completed episodes (bulk "mark all played") overflowed a
    // single cursor read ("Couldn't read row ... from CursorWindow"), so reads are paged.
    @Query("SELECT episodeId FROM listening_history WHERE isCompleted = 1 ORDER BY episodeId LIMIT :limit OFFSET :offset")
    suspend fun getCompletedEpisodeIdsPage(limit: Int, offset: Int): List<String>

    /** Emits whenever listening_history changes; the value is only a change signal. */
    @Query("SELECT COUNT(*) FROM listening_history WHERE isCompleted = 1")
    fun completedEpisodeCountFlow(): Flow<Int>

    @Transaction
    suspend fun getCompletedEpisodeIds(): List<String> {
        val ids = ArrayList<String>()
        var offset = 0
        while (true) {
            val page = getCompletedEpisodeIdsPage(COMPLETED_IDS_PAGE_SIZE, offset)
            ids.addAll(page)
            if (page.size < COMPLETED_IDS_PAGE_SIZE) return ids
            offset += COMPLETED_IDS_PAGE_SIZE
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCompletedEpisodeIdsFlow(): Flow<List<String>> = completedEpisodeCountFlow().mapLatest { getCompletedEpisodeIds() }

    companion object {
        const val COMPLETED_IDS_PAGE_SIZE = 1_000
    }

    // Get unique podcast IDs played after the given timestamp
    @Query("SELECT DISTINCT podcastId FROM listening_history WHERE lastPlayedAt > :sinceTimestamp")
    suspend fun getRecentlyPlayedPodcasts(sinceTimestamp: Long): List<String>

    @Query("SELECT * FROM listening_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentHistoryList(limit: Int): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history WHERE isDirty = 1")
    suspend fun getDirtyListeningHistory(): List<ListeningHistoryEntity>

    @Query("UPDATE listening_history SET isDirty = 1")
    suspend fun markAllHistoryDirty(): Int

    @Query("UPDATE listening_history SET isDirty = 1 WHERE syncedAt = 0")
    suspend fun markAllUnsyncedHistoryDirty(): Int

    @Query("UPDATE listening_history SET isDirty = 0, syncedAt = :timestamp WHERE episodeId IN (:ids)")
    suspend fun markListeningHistorySynced(ids: List<String>, timestamp: Long)

    @Query(
        """
        UPDATE listening_history 
        SET isDirty = 0, syncedAt = :syncedAt 
        WHERE episodeId = :episodeId 
          AND lastPlayedAt = :snapshotLastPlayedAt 
          AND likedAt = :snapshotLikedAt
          AND progressMs = :snapshotProgressMs
          AND isCompleted = :snapshotIsCompleted
          AND isLiked = :snapshotIsLiked
        """,
    )
    suspend fun markHistorySyncedIfUnchanged(
        episodeId: String,
        snapshotLastPlayedAt: Long,
        snapshotLikedAt: Long,
        snapshotProgressMs: Long,
        snapshotIsCompleted: Boolean,
        snapshotIsLiked: Boolean,
        syncedAt: Long,
    ): Int

    @Query("UPDATE listening_history SET isDirty = 1 WHERE episodeTitle IS NOT NULL AND episodeTitle != ''")
    suspend fun markAllHistoryWithTitlesDirty(): Int

    /**
     * Marks every history row whose ID has a trusted alias with a key under the row's own show for
     * the next cloud push, so the cloud copy learns the episode's recipe ID and catalog key. Rows
     * without such an identity push nothing new and stay as they are. Returns the rows marked.
     *
     * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, the one-time sync catch-up).
     * COMPAT(own-ids): cloud rows of older builds gain their episode identity. Permanent: never remove (MIGRATION.md §5).
     */
    @Query(
        """
        UPDATE listening_history SET isDirty = 1
        WHERE isDirty = 0 AND EXISTS (
            SELECT 1 FROM episode_aliases a
            WHERE a.aliasId = listening_history.episodeId AND a.podcastId = listening_history.podcastId
              AND a.state = 'trusted' AND a.catalogKey IS NOT NULL
        )
        """,
    )
    suspend fun markHistoryWithEpisodeIdentityDirty(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM listening_history WHERE episodeTitle = '' OR episodeTitle IS NULL LIMIT 1)")
    suspend fun hasAnyHistoryWithBlankTitle(): Boolean

    /**
     * Older builds could save the player's Android Auto artwork link (`content://<app>.collage/...`),
     * which dies with its cover. Swaps each for the episode's own cover from the local or RSS
     * catalogue, else the show's, else the row's own show cover, else nothing so the next play or
     * sync fills it in. A row is marked for sync only when every dead link found a real cover, so
     * an emptied field never overwrites a cover another device already uploaded. Returns the rows
     * changed; once none are left it changes nothing.
     */
    @Query(
        """
        UPDATE listening_history SET
            episodeImageUrl = CASE WHEN episodeImageUrl LIKE $PLAYER_ONLY_ART THEN $HISTORY_EPISODE_COVER ELSE episodeImageUrl END,
            podcastImageUrl = CASE WHEN podcastImageUrl LIKE $PLAYER_ONLY_ART THEN $HISTORY_SHOW_COVER ELSE podcastImageUrl END,
            isDirty = CASE WHEN (IFNULL(episodeImageUrl, '') NOT LIKE $PLAYER_ONLY_ART OR $HISTORY_EPISODE_COVER IS NOT NULL)
                AND (IFNULL(podcastImageUrl, '') NOT LIKE $PLAYER_ONLY_ART OR $HISTORY_SHOW_COVER IS NOT NULL)
                THEN 1 ELSE isDirty END
        WHERE episodeImageUrl LIKE $PLAYER_ONLY_ART OR podcastImageUrl LIKE $PLAYER_ONLY_ART
        """,
    )
    suspend fun repairPlaybackArtworkLinks(): Int
}

/** A player-only artwork link (`content://<app>.collage/...`), as a LIKE pattern. */
private const val PLAYER_ONLY_ART = "'content://%.collage/%'"

/** A history row's real episode cover: catalogue episode, then catalogue show, then the row's own show cover. */
private const val HISTORY_EPISODE_COVER = """COALESCE(
    (SELECT imageUrl FROM local_episodes WHERE local_episodes.episodeId = listening_history.episodeId
        AND imageUrl != '' AND imageUrl NOT LIKE $PLAYER_ONLY_ART),
    (SELECT imageUrl FROM rss_episodes WHERE rss_episodes.episodeId = listening_history.episodeId
        AND imageUrl != '' AND imageUrl NOT LIKE $PLAYER_ONLY_ART),
    (SELECT imageUrl FROM podcasts WHERE podcasts.podcastId = listening_history.podcastId
        AND imageUrl != '' AND imageUrl NOT LIKE $PLAYER_ONLY_ART),
    CASE WHEN listening_history.podcastImageUrl != '' AND listening_history.podcastImageUrl NOT LIKE $PLAYER_ONLY_ART
        THEN listening_history.podcastImageUrl END
)"""

/** A history row's real show cover from the catalogue. */
private const val HISTORY_SHOW_COVER = """(SELECT imageUrl FROM podcasts WHERE podcasts.podcastId = listening_history.podcastId
    AND imageUrl != '' AND imageUrl NOT LIKE $PLAYER_ONLY_ART)"""
