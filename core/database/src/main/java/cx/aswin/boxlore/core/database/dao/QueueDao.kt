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
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
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

    /**
     * Records one queue change: bumps the sequence, marks the queue for sync and keeps both
     * recent-removal lists in step.
     * - [QueueMetadataEntity.recentRemovedEpisodeIds] (plain IDs, read by every build) gains the
     *   removed IDs and drops the restored ones, as before.
     * - [QueueMetadataEntity.recentRemovedEpisodes] (removals with the episode's identity) gains an
     *   entry per removed ID, with the identity from [removedIdentities] when given. An entry is
     *   dropped when the same episode is restored ([QueueRemovalIdentity.sameEpisodeAs]: the same ID,
     *   or the same show and catalog key), and a removal of an episode that stays queued under
     *   another ID is not recorded there, because the server applies a rich removal to every ID of
     *   the episode. Both lists keep the newest [MAX_RECENT_REMOVED_EPISODES].
     *
     * The identity parameters were added in P4 of the own-episode-IDs plan (plan section 8, rich
     * queue tombstones). COMPAT(own-ids): queue removals across an episode's IDs. Permanent: never remove (MIGRATION.md §5).
     */
    @Transaction
    @Suppress("LongParameterList")
    suspend fun bumpQueueVersion(
        updatedAt: Long = System.currentTimeMillis(),
        deviceId: String? = null,
        removedEpisodeId: String? = null,
        removedEpisodeIds: Collection<String>? = null,
        restoredEpisodeIds: Collection<String>? = null,
        removedIdentities: Collection<QueueRemovalIdentity> = emptyList(),
        restoredIdentities: Collection<QueueRemovalIdentity> = emptyList(),
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
            recentRemovedEpisodes = updatedRichRemovals(
                stored = current.recentRemovedEpisodes,
                removedIds = toRemove,
                removedIdentities = removedIdentities,
                restoredIds = restoredEpisodeIds.orEmpty(),
                restoredIdentities = restoredIdentities,
            ),
        )
        upsertQueueMetadata(updated)
    }

    /**
     * Swaps a player-only artwork link (`content://<app>.collage/...`) saved by older builds for the
     * episode's own cover, else the show's, else the row's own show cover, else nothing. Order and
     * positions are untouched. Returns the rows changed; [repairPlaybackArtworkLinksForSync] also
     * marks the queue for the next push.
     */
    @Query(
        """
        UPDATE queue_items SET
            imageUrl = CASE WHEN imageUrl LIKE 'content://%.collage/%' THEN COALESCE(
                (SELECT imageUrl FROM local_episodes WHERE local_episodes.episodeId = queue_items.episodeId
                    AND imageUrl != '' AND imageUrl NOT LIKE 'content://%.collage/%'),
                (SELECT imageUrl FROM rss_episodes WHERE rss_episodes.episodeId = queue_items.episodeId
                    AND imageUrl != '' AND imageUrl NOT LIKE 'content://%.collage/%'),
                (SELECT imageUrl FROM podcasts WHERE podcasts.podcastId = queue_items.podcastId
                    AND imageUrl != '' AND imageUrl NOT LIKE 'content://%.collage/%'),
                CASE WHEN queue_items.podcastImageUrl != '' AND queue_items.podcastImageUrl NOT LIKE 'content://%.collage/%'
                    THEN queue_items.podcastImageUrl END
            ) ELSE imageUrl END,
            podcastImageUrl = CASE WHEN podcastImageUrl LIKE 'content://%.collage/%' THEN
                (SELECT imageUrl FROM podcasts WHERE podcasts.podcastId = queue_items.podcastId
                    AND imageUrl != '' AND imageUrl NOT LIKE 'content://%.collage/%')
            ELSE podcastImageUrl END
        WHERE imageUrl LIKE 'content://%.collage/%' OR podcastImageUrl LIKE 'content://%.collage/%'
        """,
    )
    suspend fun repairPlaybackArtworkLinks(): Int

    /**
     * Repairs player-only artwork links ([repairPlaybackArtworkLinks]) and, when any row changed,
     * marks the queue dirty in the same transaction so the cloud copy drops the dead links too. The
     * queue keeps its sequence: only artwork changed, not the order.
     */
    @Transaction
    suspend fun repairPlaybackArtworkLinksForSync(): Int {
        val changed = repairPlaybackArtworkLinks()
        if (changed > 0) markQueueDirty()
        return changed
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

        /**
         * The rich removal list after one queue change ([bumpQueueVersion]): restored episodes leave
         * it, removed IDs join it (with their identity when known) unless their episode is restored
         * in the same change, and the newest [MAX_RECENT_REMOVED_EPISODES] are kept.
         *
         * Added in P4 of the own-episode-IDs plan (plan section 8, rich queue tombstones).
         */
        fun updatedRichRemovals(
            stored: String?,
            removedIds: Collection<String>,
            removedIdentities: Collection<QueueRemovalIdentity>,
            restoredIds: Collection<String>,
            restoredIdentities: Collection<QueueRemovalIdentity>,
        ): String? {
            val existing = QueueRemovalIdentity.parseList(stored)
            if (existing.isEmpty() && removedIds.isEmpty() && removedIdentities.isEmpty()) return stored
            val restored = withPlainIds(restoredIdentities, restoredIds)
            val removed = withPlainIds(removedIdentities, removedIds)
                .filter { removal -> restored.none { it.sameEpisodeAs(removal) } }
                .distinctBy { it.id }
            val removedIdSet = removed.mapTo(HashSet()) { it.id }
            val kept = existing.filter { entry -> restored.none { it.sameEpisodeAs(entry) } && entry.id !in removedIdSet }
            return QueueRemovalIdentity.serializeList((kept + removed).takeLast(MAX_RECENT_REMOVED_EPISODES))
        }

        /**
         * [stored] rich removals that still hold after a pulled queue state was applied: only
         * entries whose ID is still in [plainIds] (the plain removal list now stored) and whose
         * episode isn't in [queued] (the queue now stored) under any ID. A pull replaces or merges
         * the plain list and may queue an episode again, so the rich list never keeps a removal the
         * plain list dropped or one that would remove a queued episode elsewhere.
         *
         * Added in P4 of the own-episode-IDs plan (plan section 8, rich queue tombstones).
         */
        fun richRemovalsWithin(stored: String?, plainIds: Collection<String>, queued: Collection<QueueRemovalIdentity>,): String? {
            val entries = QueueRemovalIdentity.parseList(stored)
            if (entries.isEmpty()) return stored
            val plain = plainIds.toSet()
            val kept = entries.filter { entry -> entry.id in plain && queued.none { it.sameEpisodeAs(entry) } }
            return if (kept.size == entries.size) stored else QueueRemovalIdentity.serializeList(kept)
        }

        /** [identities] plus an ID-only entry for each of [ids] they don't cover. */
        private fun withPlainIds(identities: Collection<QueueRemovalIdentity>, ids: Collection<String>,): List<QueueRemovalIdentity> {
            val covered = identities.mapTo(HashSet()) { it.id }
            return identities.toList() + ids.filter { it.isNotBlank() && it !in covered }.distinct().map { QueueRemovalIdentity(it) }
        }
    }
}
