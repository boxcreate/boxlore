package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // Column-only updates preserve deletions during asynchronous enrichment.
interface DownloadedEpisodeDao {
    @Query("SELECT * FROM downloaded_episodes ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes")
    suspend fun getAllDownloadsSync(): List<DownloadedEpisodeEntity>

    @Query("SELECT * FROM downloaded_episodes WHERE status = 2 ORDER BY downloadedAt DESC LIMIT :limit")
    suspend fun getCompletedDownloads(limit: Int = 50): List<DownloadedEpisodeEntity>

    @Query("SELECT * FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun getDownload(episodeId: String): DownloadedEpisodeEntity?

    /**
     * Which of these IDs hold a completed download; callers pass at most 500 IDs per call. Used to
     * play an episode from whichever of its stored IDs holds the file ([EpisodeListenerStore]).
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, downloads across aliases).
     */
    @Query("SELECT episodeId FROM downloaded_episodes WHERE episodeId IN (:episodeIds) AND status = 2")
    suspend fun getCompletedDownloadIds(episodeIds: List<String>): List<String>

    @Query("SELECT * FROM downloaded_episodes WHERE podcastId = :podcastId")
    suspend fun getDownloadsForPodcast(podcastId: String): List<DownloadedEpisodeEntity>

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId = :episodeId AND status = 2")
    fun isDownloadedFlow(episodeId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId = :episodeId AND status IN (0, 1)")
    fun isDownloadingFlow(episodeId: String): Flow<Int>

    /**
     * How many of these IDs hold a completed download, watched. An episode stored under several IDs
     * reads as downloaded whichever of them holds the file. Callers pass at most 500 IDs.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, downloads across aliases; decision D8).
     */
    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId IN (:episodeIds) AND status = 2")
    fun countCompletedFlow(episodeIds: List<String>): Flow<Int>

    /**
     * How many of these IDs hold a queued or running download, watched ([countCompletedFlow]).
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, downloads across aliases; decision D8).
     */
    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId IN (:episodeIds) AND status IN (0, 1)")
    fun countActiveFlow(episodeIds: List<String>): Flow<Int>

    /**
     * The download rows of these IDs, in no particular order; callers pass at most 500 IDs.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, downloads across aliases; decision D8).
     */
    @Query("SELECT * FROM downloaded_episodes WHERE episodeId IN (:episodeIds)")
    suspend fun getDownloads(episodeIds: List<String>): List<DownloadedEpisodeEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloaded_episodes")
    fun getTotalSizeBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE podcastId = :podcastId AND episodeId != :excludeEpisodeId")
    suspend fun countOthersByPodcastId(podcastId: String, excludeEpisodeId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedEpisodeEntity)

    @Query("UPDATE downloaded_episodes SET episodeImageUrl = COALESCE(:episodeArt, episodeImageUrl), podcastImageUrl = COALESCE(:podcastArt, podcastImageUrl) WHERE episodeId = :episodeId")
    suspend fun updateArtwork(episodeId: String, episodeArt: String?, podcastArt: String?)

    @Query("UPDATE downloaded_episodes SET chaptersUrl = COALESCE(:chapters, chaptersUrl), transcriptUrl = COALESCE(:transcript, transcriptUrl) WHERE episodeId = :episodeId")
    suspend fun updateOfflineText(episodeId: String, chapters: String?, transcript: String?)

    @Query("UPDATE downloaded_episodes SET status = :status WHERE episodeId = :episodeId AND downloadOrigin = 'auto' AND status != 2")
    suspend fun updateAutoStatus(episodeId: String, status: Int)

    @Query("UPDATE downloaded_episodes SET isSmartDownloaded = 0, downloadOrigin = 'manual' WHERE episodeId = :episodeId")
    suspend fun promoteToManual(episodeId: String)

    @Query("DELETE FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)
}
