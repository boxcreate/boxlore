package cx.aswin.boxlore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    @Query("UPDATE queue_metadata SET isDirty = 1 WHERE id = 1")
    suspend fun markQueueDirty(): Int

    @Query(
        """
        UPDATE queue_metadata 
        SET isDirty = 0, syncedAt = :syncedAt 
        WHERE id = 1 AND queueSequence = :snapshotSequence
        """,
    )
    suspend fun markQueueSyncedIfSequenceMatches(
        snapshotSequence: Long,
        syncedAt: Long,
    ): Int

    @Transaction
    suspend fun bumpQueueVersion(
        updatedAt: Long = System.currentTimeMillis(),
        deviceId: String? = null,
        removedEpisodeId: String? = null,
        removedEpisodeIds: Collection<String>? = null,
        restoredEpisodeIds: Collection<String>? = null,
    ) {
        val current = getQueueMetadata() ?: QueueMetadataEntity(id = 1)
        val existingList = parseRecentRemovedEpisodeIds(current.recentRemovedEpisodeIds)
        val withoutRestored = if (restoredEpisodeIds.isNullOrEmpty()) {
            existingList
        } else {
            val restoredSet = restoredEpisodeIds.toSet()
            existingList.filter { it !in restoredSet }
        }
        val toRemove = buildList {
            if (!removedEpisodeId.isNullOrBlank()) add(removedEpisodeId)
            if (!removedEpisodeIds.isNullOrEmpty()) addAll(removedEpisodeIds.filter { it.isNotBlank() })
        }
        val updatedRemovedList = if (toRemove.isNotEmpty()) {
            val toRemoveSet = toRemove.toSet()
            val filteredExisting = withoutRestored.filter { it !in toRemoveSet }
            (filteredExisting + toRemove.distinct()).takeLast(MAX_RECENT_REMOVED_EPISODES)
        } else {
            withoutRestored
        }
        val updatedRemovedStr = serializeRecentRemovedEpisodeIds(updatedRemovedList)
        val updated = current.copy(
            queueUpdatedAt = updatedAt,
            queueSequence = current.queueSequence + 1L,
            lastModifiedDeviceId = deviceId ?: current.lastModifiedDeviceId,
            isDirty = true,
            recentRemovedEpisodeIds = updatedRemovedStr,
        )
        upsertQueueMetadata(updated)
    }

    companion object {
        private const val MAX_RECENT_REMOVED_EPISODES = 50
        private val gson = Gson()
        private val listStringType = object : TypeToken<List<String>>() {}.type

        fun parseRecentRemovedEpisodeIds(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val trimmed = raw.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val parsed = runCatching {
                    gson.fromJson<List<String>>(trimmed, listStringType)
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        fun serializeRecentRemovedEpisodeIds(ids: List<String>): String? {
            if (ids.isEmpty()) return null
            return gson.toJson(ids)
        }
    }
}
