package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
@Suppress("TooManyFunctions") // Separate statements keep activation/removal transactions explicit.
interface AutoDownloadDao {
    @Query("SELECT * FROM auto_download_shows WHERE podcastId = :podcastId")
    suspend fun getShow(podcastId: String): AutoDownloadShowEntity?

    @Query("SELECT * FROM auto_download_shows")
    suspend fun getShows(): List<AutoDownloadShowEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShow(show: AutoDownloadShowEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelease(release: AutoDownloadReleaseEntity): Long

    @Query("SELECT * FROM auto_download_releases WHERE episodeId = :episodeId")
    suspend fun getRelease(episodeId: String): AutoDownloadReleaseEntity?

    @Query("SELECT * FROM auto_download_releases WHERE podcastId = :podcastId AND state = 'pending'")
    suspend fun pending(podcastId: String): List<AutoDownloadReleaseEntity>

    @Query("UPDATE auto_download_releases SET state = :state WHERE episodeId = :episodeId AND state = 'pending'")
    suspend fun finish(episodeId: String, state: String = AutoDownloadReleaseEntity.HANDLED)

    @Query("UPDATE auto_download_releases SET state = 'removed' WHERE episodeId = :episodeId")
    suspend fun updateRemoved(episodeId: String)

    @Query("INSERT OR IGNORE INTO auto_download_releases(episodeId, podcastId, state) SELECT episodeId, podcastId, 'removed' FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun seedRemoved(episodeId: String)

    @Transaction
    suspend fun markRemoved(episodeId: String) {
        seedRemoved(episodeId)
        updateRemoved(episodeId)
    }

    @Query("DELETE FROM auto_download_shows WHERE podcastId = :podcastId")
    suspend fun deleteShow(podcastId: String)

    @Query("UPDATE auto_download_releases SET state = 'handled' WHERE podcastId = :podcastId AND state = 'pending'")
    suspend fun finishPending(podcastId: String)

    @Query(
        """
        INSERT OR IGNORE INTO auto_download_releases(episodeId, podcastId, state)
        SELECT episodeId, podcastId, 'handled' FROM local_episodes WHERE podcastId = :podcastId
    """
    )
    suspend fun seedExisting(podcastId: String)

    @Query("UPDATE podcasts SET autoDownloadEnabled = :enabled, isDirty = 1 WHERE podcastId = :podcastId")
    suspend fun updateSetting(podcastId: String, enabled: Boolean)

    @Transaction
    suspend fun activate(podcastId: String, nowSeconds: Long) {
        if (insertShow(AutoDownloadShowEntity(podcastId, nowSeconds)) != -1L) seedExisting(podcastId)
    }

    @Transaction
    suspend fun deactivate(podcastId: String) {
        finishPending(podcastId)
        deleteShow(podcastId)
    }

    @Transaction
    suspend fun changeSetting(podcastId: String, enabled: Boolean, nowSeconds: Long) {
        if (enabled) activate(podcastId, nowSeconds) else deactivate(podcastId)
        updateSetting(podcastId, enabled)
    }
}
