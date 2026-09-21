package cx.aswin.boxlore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    fun getAllQueueItems(): Flow<List<QueueItem>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getAllQueueItemsSync(): List<QueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: QueueItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItem>)

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun deleteQueueItem(id: Long)

    @Query("DELETE FROM queue_items WHERE episodeId = :episodeId")
    suspend fun deleteQueueItemByEpisodeId(episodeId: String)

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Update
    suspend fun updateQueueItem(item: QueueItem)

    @Transaction
    suspend fun updateQueuePositions(items: List<QueueItem>) {
        items.forEach { updateQueueItem(it) }
    }

    @Query("SELECT MAX(position) FROM queue_items")
    suspend fun getMaxPosition(): Int?

    @Query("SELECT COUNT(*) FROM queue_items WHERE episodeId = :episodeId")
    suspend fun countEpisode(episodeId: String): Int

    @Query("SELECT * FROM queue_items WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getQueueItemByEpisodeId(episodeId: String): QueueItem?

    @Query("SELECT episodeId FROM queue_items WHERE podcastId = :podcastId")
    suspend fun getEpisodeIdsForPodcast(podcastId: String): List<String>

    @Query("SELECT * FROM queue_metadata WHERE id = 1 LIMIT 1")
    suspend fun getQueueMetadata(): QueueMetadataEntity?

    @Query("SELECT * FROM queue_metadata WHERE id = 1 LIMIT 1")
    fun getQueueMetadataFlow(): Flow<QueueMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueueMetadata(metadata: QueueMetadataEntity)

    @Query(
        """
        INSERT INTO queue_metadata (id, queueUpdatedAt, queueSequence, isDirty, syncedAt)
        VALUES (1, 0, 0, 0, :timestamp)
        ON CONFLICT(id) DO UPDATE SET isDirty = 0, syncedAt = :timestamp
        """,
    )
    suspend fun markQueueSynced(timestamp: Long)

    @Transaction
    suspend fun bumpQueueVersion(
        updatedAt: Long = System.currentTimeMillis(),
        deviceId: String? = null,
        removedEpisodeId: String? = null,
        restoredEpisodeIds: Collection<String>? = null,
    ) {
        val current = getQueueMetadata() ?: QueueMetadataEntity(id = 1)
        val existingList = current.recentRemovedEpisodeIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val withoutRestored = if (restoredEpisodeIds.isNullOrEmpty()) {
            existingList
        } else {
            val restoredSet = restoredEpisodeIds.toSet()
            existingList.filter { it !in restoredSet }
        }
        val updatedRemovedList = if (!removedEpisodeId.isNullOrBlank()) {
            (withoutRestored - removedEpisodeId + removedEpisodeId).takeLast(50)
        } else {
            withoutRestored
        }
        val updatedRemovedStr = if (updatedRemovedList.isEmpty()) null else updatedRemovedList.joinToString(",")
        val updated = current.copy(
            queueUpdatedAt = updatedAt,
            queueSequence = current.queueSequence + 1L,
            lastModifiedDeviceId = deviceId ?: current.lastModifiedDeviceId,
            isDirty = true,
            recentRemovedEpisodeIds = updatedRemovedStr,
        )
        upsertQueueMetadata(updated)
    }
}
