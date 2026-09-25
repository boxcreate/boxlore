package cx.aswin.boxlore.core.catalog.ports

import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity

/**
 * Port decoupling queue synchronization in the catalog engine from the playback module.
 */
interface QueueSyncPort {
    suspend fun getQueueSnapshot(): List<QueueItem>
    suspend fun getQueueMetadata(): QueueMetadataEntity?
    suspend fun applyRemoteQueueState(
        items: List<QueueItem>,
        metadata: QueueMetadataEntity,
    )
    suspend fun markQueueSynced(expectedSequence: Long, syncedAt: Long): Boolean
    suspend fun markQueueDirty()
}
