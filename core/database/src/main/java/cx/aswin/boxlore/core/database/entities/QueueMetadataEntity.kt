package cx.aswin.boxlore.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue_metadata")
data class QueueMetadataEntity(
    @PrimaryKey
    val id: Int = 1,
    val queueUpdatedAt: Long = 0L,
    val queueSequence: Long = 0L,
    val lastModifiedDeviceId: String? = null,
    val isDirty: Boolean = false,
    val syncedAt: Long = 0L,
    val recentRemovedEpisodeIds: String? = null,
    /**
     * Recent queue removals with their episode identity (JSON list of ID, recipe ID, show and
     * catalog key), so a removal also reaches devices that store the episode under another ID.
     * Added in Room v39 beside [recentRemovedEpisodeIds], which stays for older builds. Nothing
     * reads or writes it yet.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 rich queue tombstones).
     * COMPAT(own-ids): queue removals across an episode's IDs. Permanent: never remove (MIGRATION.md §5).
     */
    val recentRemovedEpisodes: String? = null,
)
