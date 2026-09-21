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
)
