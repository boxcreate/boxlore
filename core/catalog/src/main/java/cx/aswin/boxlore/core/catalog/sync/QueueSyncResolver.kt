package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto

/**
 * Monotonic sequence versioning and 3-way tombstone merge resolver for the playback queue.
 * Preserves the active playing episode at the head of the queue and executes the 5-tier
 * metadata hydration cascade for remote queue items.
 */
class QueueSyncResolver(
    private val queueSyncPort: QueueSyncPort,
    private val database: BoxLoreDatabase,
    private val activePlaybackSyncPort: ActivePlaybackSyncPort? = null,
    private val podcastRepository: PodcastRepository? = null,
) {
    suspend fun resolveQueue(remoteQueue: QueueSyncDto, syncedAt: Long) {
        val remoteSeq = remoteQueue.queueSequence
        val localMeta = queueSyncPort.getQueueMetadata() ?: QueueMetadataEntity(id = 1)
        val localSeq = localMeta.queueSequence

        // Case 3: Local is ahead or equal. Keep local queue.
        if (localSeq >= remoteSeq) {
            return
        }

        val localItems = queueSyncPort.getQueueSnapshot()
        val existingItemsMap = localItems.associateBy { it.episodeId }
        val activePlayingEpisodeId = activePlaybackSyncPort?.getActivePlayingEpisodeId()

        if (!localMeta.isDirty) {
            resolveLinearAdvance(
                remoteQueue = remoteQueue,
                localMeta = localMeta,
                existingItemsMap = existingItemsMap,
                activePlayingEpisodeId = activePlayingEpisodeId,
                syncedAt = syncedAt,
            )
        } else {
            resolveDivergedBranches(
                remoteQueue = remoteQueue,
                localMeta = localMeta,
                localItems = localItems,
                existingItemsMap = existingItemsMap,
                activePlayingEpisodeId = activePlayingEpisodeId,
                syncedAt = syncedAt,
            )
        }
    }

    private suspend fun resolveLinearAdvance(
        remoteQueue: QueueSyncDto,
        localMeta: QueueMetadataEntity,
        existingItemsMap: Map<String, QueueItem>,
        activePlayingEpisodeId: String?,
        syncedAt: Long,
    ) {
        val sortedRemoteItems = remoteQueue.items.sortedBy { it.position }
        val hydrated = sortedRemoteItems.map { dto ->
            hydrateQueueItem(dto, existingItemsMap)
        }.toMutableList()

        // Active playing episode guard: retain active episode if missing from remote
        if (activePlayingEpisodeId != null && hydrated.none { it.episodeId == activePlayingEpisodeId }) {
            val activeItem = existingItemsMap[activePlayingEpisodeId]
            if (activeItem != null) {
                hydrated.add(0, activeItem)
            }
        }

        val reindexed = hydrated.mapIndexed { index, item -> item.copy(position = index) }
        val updatedMeta = localMeta.copy(
            queueSequence = remoteQueue.queueSequence,
            queueUpdatedAt = remoteQueue.queueUpdatedAt,
            lastModifiedDeviceId = remoteQueue.lastModifiedDeviceId,
            recentRemovedEpisodeIds = QueueDao.serializeRecentRemovedEpisodeIds(remoteQueue.recentRemovedEpisodeIds),
            isDirty = false,
            syncedAt = syncedAt,
        )
        queueSyncPort.applyRemoteQueueState(reindexed, updatedMeta)
    }

    private suspend fun resolveDivergedBranches(
        remoteQueue: QueueSyncDto,
        localMeta: QueueMetadataEntity,
        localItems: List<QueueItem>,
        existingItemsMap: Map<String, QueueItem>,
        activePlayingEpisodeId: String?,
        syncedAt: Long,
    ) {
        val localTombstones = QueueDao.parseRecentRemovedEpisodeIds(localMeta.recentRemovedEpisodeIds).toSet()
        val remoteTombstones = remoteQueue.recentRemovedEpisodeIds.toSet()
        val allTombstones = localTombstones + remoteTombstones

        val candidateRemote = remoteQueue.items.filter { dto ->
            dto.episodeId !in allTombstones || dto.episodeId == activePlayingEpisodeId
        }.sortedBy { it.position }

        val candidateLocal = localItems.filter { item ->
            item.episodeId !in allTombstones || item.episodeId == activePlayingEpisodeId
        }

        val hydratedRemote = candidateRemote.map { dto ->
            hydrateQueueItem(dto, existingItemsMap)
        }

        val mergedList = if (remoteQueue.queueUpdatedAt >= localMeta.queueUpdatedAt) {
            val remoteIds = hydratedRemote.map { it.episodeId }.toSet()
            val localAdditions = candidateLocal.filter { it.episodeId !in remoteIds }
            (hydratedRemote + localAdditions).toMutableList()
        } else {
            val localIds = candidateLocal.map { it.episodeId }.toSet()
            val remoteAdditions = hydratedRemote.filter { it.episodeId !in localIds }
            (candidateLocal + remoteAdditions).toMutableList()
        }

        positionActiveEpisodeAtHead(mergedList, existingItemsMap, activePlayingEpisodeId)

        val reindexed = mergedList.mapIndexed { index, item -> item.copy(position = index) }
        val mergedSequence = maxOf(localMeta.queueSequence, remoteQueue.queueSequence) + 1L
        val mergedTombstones = allTombstones.toList().takeLast(MAX_STORED_REMOVED_IDS)

        val updatedMeta = localMeta.copy(
            queueSequence = mergedSequence,
            queueUpdatedAt = maxOf(localMeta.queueUpdatedAt, remoteQueue.queueUpdatedAt),
            lastModifiedDeviceId = localMeta.lastModifiedDeviceId,
            recentRemovedEpisodeIds = QueueDao.serializeRecentRemovedEpisodeIds(mergedTombstones),
            isDirty = true,
            syncedAt = syncedAt,
        )
        queueSyncPort.applyRemoteQueueState(reindexed, updatedMeta)
    }

    private fun positionActiveEpisodeAtHead(
        mergedList: MutableList<QueueItem>,
        existingItemsMap: Map<String, QueueItem>,
        activePlayingEpisodeId: String?,
    ) {
        if (activePlayingEpisodeId == null) return
        val activeIdx = mergedList.indexOfFirst { it.episodeId == activePlayingEpisodeId }
        if (activeIdx > 0) {
            val activeItem = mergedList.removeAt(activeIdx)
            mergedList.add(0, activeItem)
        } else if (activeIdx == -1) {
            val activeItem = existingItemsMap[activePlayingEpisodeId]
            if (activeItem != null) {
                mergedList.add(0, activeItem)
            }
        }
    }

    private suspend fun hydrateQueueItem(
        dto: QueueItemSyncDto,
        existingItemsMap: Map<String, QueueItem>,
    ): QueueItem {
        // Tier 1: Local Queue Cache
        val existing = existingItemsMap[dto.episodeId]
        if (existing != null) {
            return existing.copy(
                position = dto.position,
                contextType = dto.contextType ?: existing.contextType,
                contextSourceId = dto.contextSourceId ?: existing.contextSourceId,
            )
        }

        return hydrateFromCatalogOrRss(dto)
            ?: hydrateFromHistory(dto)
            ?: hydrateFromNetwork(dto)
            ?: fallbackQueueItem(dto)
    }

    private suspend fun hydrateFromCatalogOrRss(dto: QueueItemSyncDto): QueueItem? {
        val localCatalogEp = database.localEpisodeCatalogDao().getEpisode(dto.episodeId)
        if (localCatalogEp != null) {
            return QueueItem(
                episodeId = dto.episodeId,
                title = localCatalogEp.title,
                podcastId = dto.podcastId.ifEmpty { localCatalogEp.podcastId },
                podcastTitle = "",
                imageUrl = localCatalogEp.imageUrl,
                audioUrl = localCatalogEp.audioUrl,
                duration = localCatalogEp.duration,
                pubDate = localCatalogEp.publishedDate,
                description = localCatalogEp.description,
                position = dto.position,
                contextType = dto.contextType ?: "MANUAL",
                contextSourceId = dto.contextSourceId,
            )
        }

        val rssEp = database.rssEpisodeDao().getEpisode(dto.episodeId)
        if (rssEp != null) {
            return QueueItem(
                episodeId = dto.episodeId,
                title = rssEp.title,
                podcastId = dto.podcastId.ifEmpty { rssEp.podcastId },
                podcastTitle = "",
                imageUrl = rssEp.imageUrl,
                audioUrl = rssEp.audioUrl,
                duration = rssEp.duration,
                pubDate = rssEp.publishedDate,
                description = rssEp.description,
                position = dto.position,
                contextType = dto.contextType ?: "MANUAL",
                contextSourceId = dto.contextSourceId,
            )
        }
        return null
    }

    private suspend fun hydrateFromHistory(dto: QueueItemSyncDto): QueueItem? {
        val history = database.listeningHistoryDao().getHistoryItem(dto.episodeId)
        if (history != null && history.episodeTitle.isNotBlank()) {
            return QueueItem(
                episodeId = dto.episodeId,
                title = history.episodeTitle,
                podcastId = dto.podcastId.ifEmpty { history.podcastId },
                podcastTitle = history.podcastName,
                imageUrl = history.episodeImageUrl,
                podcastImageUrl = history.podcastImageUrl,
                audioUrl = history.episodeAudioUrl ?: "",
                duration = (history.durationMs / 1000L).toInt(),
                pubDate = 0L,
                description = history.episodeDescription,
                position = dto.position,
                contextType = dto.contextType ?: "MANUAL",
                contextSourceId = dto.contextSourceId,
            )
        }
        return null
    }

    private suspend fun hydrateFromNetwork(dto: QueueItemSyncDto): QueueItem? {
        val podRepo = podcastRepository ?: return null
        val fetched = runCatching { podRepo.getEpisode(dto.episodeId) }.getOrNull() ?: return null
        return QueueItem(
            episodeId = dto.episodeId,
            title = fetched.title,
            podcastId = dto.podcastId.ifEmpty { fetched.podcastId ?: "" },
            podcastTitle = fetched.podcastTitle ?: "",
            imageUrl = fetched.imageUrl,
            audioUrl = fetched.audioUrl,
            duration = fetched.duration,
            pubDate = fetched.publishedDate,
            description = fetched.description,
            position = dto.position,
            contextType = dto.contextType ?: "MANUAL",
            contextSourceId = dto.contextSourceId,
        )
    }

    private fun fallbackQueueItem(dto: QueueItemSyncDto): QueueItem = QueueItem(
        episodeId = dto.episodeId,
        title = "Episode ${dto.episodeId}",
        podcastId = dto.podcastId,
        podcastTitle = "Podcast ${dto.podcastId}",
        imageUrl = null,
        audioUrl = "",
        duration = 0,
        pubDate = 0L,
        description = null,
        position = dto.position,
        contextType = dto.contextType ?: "MANUAL",
        contextSourceId = dto.contextSourceId,
    )

    companion object {
        private const val MAX_STORED_REMOVED_IDS = 50
    }
}
