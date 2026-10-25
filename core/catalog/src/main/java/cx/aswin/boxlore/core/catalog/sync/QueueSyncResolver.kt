package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncEpisodeIdentityFields

/**
 * Monotonic sequence versioning and 3-way tombstone merge resolver for the playback queue.
 * Preserves the active playing episode at the head of the queue and executes the 5-tier
 * metadata hydration cascade for remote queue items.
 *
 * An episode can be queued under several of its IDs (docs/EPISODE_IDS.md §5). Since P4 PR 11 of
 * the own-episode-IDs plan (plan section 8, cloud sync identity) the merge works per episode
 * ([SyncEpisodeIdentity.tokens]):
 * - A pulled item of an episode this phone already queues keeps the phone's item and ID; the
 *   merged queue holds each episode once, and the playing episode keeps the ID it plays under.
 * - Removals: this phone's unsynced removals also drop the pulled item of the same episode under
 *   another ID, and the server's removals ([QueueSyncDto.recentRemovedEpisodeIds] together with the
 *   rich [QueueSyncDto.recentRemovedEpisodes]) also drop this phone's unsynced item of the same
 *   episode. A removal whose episode the same side still queues under another ID is stale (that side
 *   queued it again) and only matches its own ID. A rich removal counts only while the plain list
 *   still holds its ID: a build that sends no rich removals replaces the plain list, so a rich
 *   removal it no longer lists was queued again there.
 * - Both removal lists are stored, the rich one kept in step with the plain one ([QueueDao.richRemovalsWithin]).
 * - Hydration ([QueueItemHydration]) reads the episode's catalog row and history under any of its
 *   IDs, and asks the network for the recipe ID the server sent (`/v2/episode`) before the stored ID.
 *
 * COMPAT(own-ids): one queue entry per episode across its IDs, devices and older builds. Permanent: never remove (MIGRATION.md §5).
 */
class QueueSyncResolver(
    private val queueSyncPort: QueueSyncPort,
    private val database: BoxLoreDatabase,
    private val activePlaybackSyncPort: ActivePlaybackSyncPort? = null,
    podcastRepository: PodcastRepository? = null,
    private val episodeIdentity: SyncEpisodeIdentity = SyncEpisodeIdentity(database),
) {
    private val hydration = QueueItemHydration(database, podcastRepository)

    suspend fun resolveQueue(remoteQueue: QueueSyncDto, syncedAt: Long) {
        val remoteSeq = remoteQueue.queueSequence
        val localMeta = queueSyncPort.getQueueMetadata() ?: QueueMetadataEntity(id = 1)
        val localSeq = localMeta.queueSequence

        // Case 3: Local is ahead or equal. Keep local queue.
        if (localSeq >= remoteSeq) {
            return
        }

        val localItems = queueSyncPort.getQueueSnapshot()
        val activePlayingEpisodeId = activePlaybackSyncPort?.getActivePlayingEpisodeId()
        val episodes = QueueEpisodes(
            tokens = episodeIdentity.tokens(
                localItems.map { it.episodeId } + remoteQueue.items.map { it.episodeId } + listOfNotNull(activePlayingEpisodeId),
            ),
            localItems = localItems,
            activeId = activePlayingEpisodeId,
        )

        if (!localMeta.isDirty) {
            resolveLinearAdvance(
                remoteQueue = remoteQueue,
                localMeta = localMeta,
                episodes = episodes,
                syncedAt = syncedAt,
            )
        } else {
            resolveDivergedBranches(
                remoteQueue = remoteQueue,
                localMeta = localMeta,
                episodes = episodes,
                syncedAt = syncedAt,
            )
        }
    }

    private suspend fun resolveLinearAdvance(
        remoteQueue: QueueSyncDto,
        localMeta: QueueMetadataEntity,
        episodes: QueueEpisodes,
        syncedAt: Long,
    ) {
        val sortedRemoteItems = remoteQueue.items.sortedBy { it.position }
        val hydrated = episodes.oncePerEpisode(sortedRemoteItems.map { dto -> hydrateQueueItem(dto, episodes) }).toMutableList()

        // Active playing episode guard: retain active episode if missing from remote
        val activeId = episodes.activeId
        if (activeId != null && hydrated.none { episodes.isActive(it.episodeId) }) {
            val activeItem = episodes.localItem(activeId)
                ?: hydrateQueueItem(QueueItemSyncDto(episodeId = activeId, podcastId = "", position = 0), episodes)
            hydrated.add(0, activeItem)
        }

        val reindexed = episodes.playingUnderActiveId(hydrated).mapIndexed { index, item -> item.copy(position = index) }
        val plainRemovals = remoteQueue.recentRemovedEpisodeIds
        val updatedMeta = localMeta.copy(
            queueSequence = remoteQueue.queueSequence,
            queueUpdatedAt = remoteQueue.queueUpdatedAt,
            lastModifiedDeviceId = remoteQueue.lastModifiedDeviceId,
            recentRemovedEpisodeIds = QueueDao.serializeRecentRemovedEpisodeIds(plainRemovals),
            recentRemovedEpisodes = mergedRichRemovals(localMeta, remoteQueue, plainRemovals, reindexed),
            isDirty = false,
            syncedAt = syncedAt,
        )
        queueSyncPort.applyRemoteQueueState(reindexed, updatedMeta)
    }

    private suspend fun resolveDivergedBranches(
        remoteQueue: QueueSyncDto,
        localMeta: QueueMetadataEntity,
        episodes: QueueEpisodes,
        syncedAt: Long,
    ) {
        val localTombstones = QueueDao.parseRecentRemovedEpisodeIds(localMeta.recentRemovedEpisodeIds).toSet()
        val remoteTombstones = remoteQueue.recentRemovedEpisodeIds.toSet()
        val allTombstones = localTombstones + remoteTombstones

        // Removals matched per episode; a removal its own side still queues under another ID is stale.
        val localRemovedEpisodes = episodeIdentity.removalTokens(
            localTombstones.map(::QueueRemovalIdentity) + QueueRemovalIdentity.parseList(localMeta.recentRemovedEpisodes),
        ) - episodes.tokensOf(episodes.localItems.map { it.episodeId })
        val remoteRemovedEpisodes = episodeIdentity.removalTokens(
            remoteTombstones.map(::QueueRemovalIdentity) + validRemoteRichRemovals(remoteQueue),
        ) - episodes.tokensOf(remoteQueue.items.map { it.episodeId })

        val candidateRemote = remoteQueue.items.filter { dto ->
            episodes.isActive(dto.episodeId) ||
                (dto.episodeId !in allTombstones && episodes.tokenOf(dto.episodeId) !in localRemovedEpisodes)
        }.sortedBy { it.position }

        val candidateLocal = episodes.localItems.filter { item ->
            episodes.isActive(item.episodeId) ||
                (item.episodeId !in allTombstones && episodes.tokenOf(item.episodeId) !in remoteRemovedEpisodes)
        }

        // A pulled item takes this phone's item only while the phone keeps it, so a removed ID never comes back.
        val kept = episodes.withLocalItems(candidateLocal)
        val hydratedRemote = kept.oncePerEpisode(candidateRemote.map { dto -> hydrateQueueItem(dto, kept) })

        val mergedList = if (remoteQueue.queueUpdatedAt >= localMeta.queueUpdatedAt) {
            val remoteEpisodes = episodes.tokensOf(hydratedRemote.map { it.episodeId })
            val localAdditions = candidateLocal.filter { episodes.tokenOf(it.episodeId) !in remoteEpisodes }
            episodes.oncePerEpisode(hydratedRemote + localAdditions).toMutableList()
        } else {
            val localEpisodes = episodes.tokensOf(candidateLocal.map { it.episodeId })
            val remoteAdditions = hydratedRemote.filter { episodes.tokenOf(it.episodeId) !in localEpisodes }
            episodes.oncePerEpisode(candidateLocal + remoteAdditions).toMutableList()
        }

        positionActiveEpisodeAtHead(mergedList, episodes)

        val reindexed = episodes.playingUnderActiveId(mergedList).mapIndexed { index, item -> item.copy(position = index) }
        val mergedSequence = maxOf(localMeta.queueSequence, remoteQueue.queueSequence) + 1L
        val mergedTombstones = allTombstones.toList().takeLast(MAX_STORED_REMOVED_IDS)

        val updatedMeta = localMeta.copy(
            queueSequence = mergedSequence,
            queueUpdatedAt = maxOf(localMeta.queueUpdatedAt, remoteQueue.queueUpdatedAt),
            lastModifiedDeviceId = localMeta.lastModifiedDeviceId,
            recentRemovedEpisodeIds = QueueDao.serializeRecentRemovedEpisodeIds(mergedTombstones),
            recentRemovedEpisodes = mergedRichRemovals(localMeta, remoteQueue, mergedTombstones, reindexed),
            isDirty = true,
            syncedAt = syncedAt,
        )
        queueSyncPort.applyRemoteQueueState(reindexed, updatedMeta)
    }

    private suspend fun positionActiveEpisodeAtHead(
        mergedList: MutableList<QueueItem>,
        episodes: QueueEpisodes,
    ) {
        val activeId = episodes.activeId ?: return
        val activeIdx = mergedList.indexOfFirst { episodes.isActive(it.episodeId) }
        if (activeIdx > 0) {
            val activeItem = mergedList.removeAt(activeIdx)
            mergedList.add(0, activeItem)
        } else if (activeIdx == -1) {
            val activeItem = episodes.localItem(activeId)
                ?: hydrateQueueItem(QueueItemSyncDto(episodeId = activeId, podcastId = "", position = 0), episodes)
            mergedList.add(0, activeItem)
        }
    }

    /**
     * The rich removals to store after a merge: this phone's and the server's (only those the
     * server's plain list still holds), one per ID, kept in step with [plainRemovals] and without
     * an episode [queued] queues under any ID ([QueueDao.richRemovalsWithin]).
     */
    private suspend fun mergedRichRemovals(
        localMeta: QueueMetadataEntity,
        remoteQueue: QueueSyncDto,
        plainRemovals: List<String>,
        queued: List<QueueItem>,
    ): String? {
        val local = QueueRemovalIdentity.parseList(localMeta.recentRemovedEpisodes)
        val remote = validRemoteRichRemovals(remoteQueue)
        if (local.isEmpty() && remote.isEmpty()) return localMeta.recentRemovedEpisodes
        val merged = (remote + local).groupBy { it.id }.values.map { same -> same.firstOrNull { it.show != null } ?: same.first() }
        val stored = QueueRemovalIdentity.serializeList(merged)
        return QueueDao.richRemovalsWithin(
            stored = stored,
            plainIds = plainRemovals,
            queued = database.episodeListenerStore().queueIdentities(queued.map { it.episodeId }),
        )
    }

    /** The server's rich removals that its plain list still holds, read safely from the wire. */
    private fun validRemoteRichRemovals(remoteQueue: QueueSyncDto): List<QueueRemovalIdentity> {
        val plain = remoteQueue.recentRemovedEpisodeIds.toSet()
        return remoteQueue.recentRemovedEpisodes.orEmpty()
            .filter { it.id.isNotBlank() && it.id in plain }
            .map { dto ->
                QueueRemovalIdentity(
                    id = dto.id,
                    recipeId = SyncEpisodeIdentityFields.recipeIdOrNull(dto.recipeId),
                    show = dto.show?.takeIf { it.isNotBlank() },
                    catalogKey = SyncEpisodeIdentityFields.catalogKeyOrNull(dto.catalogKey),
                )
            }
    }

    private suspend fun hydrateQueueItem(
        dto: QueueItemSyncDto,
        episodes: QueueEpisodes,
    ): QueueItem {
        // Tier 1: Local Queue Cache (the same ID, else another ID of the same episode, which keeps its own ID)
        val existing = episodes.localItem(dto.episodeId)
        if (existing != null) {
            return existing.copy(
                position = dto.position,
                contextType = dto.contextType ?: existing.contextType,
                contextSourceId = dto.contextSourceId ?: existing.contextSourceId,
            )
        }

        // Tiers 2-5: the peer's metadata, this device's catalogue and history, the network.
        return hydration.hydrate(dto)
    }

    /**
     * One queue merge's view of episodes: a token per ID ([SyncEpisodeIdentity.tokens]), the
     * phone's queued items and the playing ID.
     */
    private class QueueEpisodes(
        private val tokens: Map<String, String>,
        val localItems: List<QueueItem>,
        val activeId: String?,
    ) {
        private val localById = localItems.associateBy { it.episodeId }
        private val localByToken = localItems.asReversed().associateBy { tokenOf(it.episodeId) }
        private val activeToken = activeId?.let(::tokenOf)

        fun tokenOf(id: String): String = tokens[id] ?: id

        fun tokensOf(ids: Collection<String>): Set<String> = ids.mapTo(HashSet(), ::tokenOf)

        /** The phone's queued item of [id], else of another ID of the same episode. */
        fun localItem(id: String): QueueItem? = localById[id] ?: localByToken[tokenOf(id)]

        /** True when [id] is the playing episode under any of its IDs. */
        fun isActive(id: String): Boolean = activeId != null && (id == activeId || tokenOf(id) == activeToken)

        /** This view with only [items] as the phone's queued items. */
        fun withLocalItems(items: List<QueueItem>): QueueEpisodes = QueueEpisodes(tokens, items, activeId)

        /** [items] with each episode once, at its first place. */
        fun oncePerEpisode(items: List<QueueItem>): List<QueueItem> = items.distinctBy { tokenOf(it.episodeId) }

        /** [items] with the playing episode under the ID the player plays it as, so the player doesn't list it twice. */
        fun playingUnderActiveId(items: List<QueueItem>): List<QueueItem> {
            val playing = activeId ?: return items
            return items.map { if (it.episodeId != playing && isActive(it.episodeId)) it.copy(episodeId = playing) else it }
        }
    }

    companion object {
        private const val MAX_STORED_REMOVED_IDS = 50
    }
}
