package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.EpisodeListenerProjection
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker

internal data class LocalCatalogListenerReference(
    val episodeId: String,
    val enclosureUrl: String?,
    val title: String?,
    val publishedDate: Long?,
)

/**
 * Finds a single, safe catalog target for listener state whose legacy negative
 * episode id was restored without the old supplement/catalog identity row.
 */
internal object LocalCatalogListenerRemapLogic {
    /** A phone-minted (negative) episode ID: the only IDs the orphan rematch may move. */
    fun isLegacyLocalId(episodeId: String): Boolean = episodeId.toLongOrNull()?.let { it < 0L } == true

    fun mappings(catalogRows: List<LocalEpisodeEntity>, references: List<LocalCatalogListenerReference>,): Map<String, LocalEpisodeEntity> {
        val catalogIds = catalogRows.mapTo(mutableSetOf(), LocalEpisodeEntity::episodeId)
        val candidates =
            catalogRows.map { row ->
                LocalCatalogOrphanRematch.Candidate(
                    episode = row.toCatalogEpisode(LocalEpisodeCatalogPort.PodcastMeta()),
                    guid = row.guid,
                )
            }
        return references
            .asSequence()
            .filter { reference -> isLegacyLocalId(reference.episodeId) && reference.episodeId !in catalogIds }
            .groupBy(LocalCatalogListenerReference::episodeId)
            .mapNotNull { (oldId, grouped) ->
                val targets =
                    grouped
                        .mapNotNull { reference ->
                            LocalCatalogOrphanRematch.rematch(
                                resolved = null,
                                candidates = candidates,
                                guid = null,
                                enclosureUrl = reference.enclosureUrl,
                                title = reference.title,
                                publishedDate = reference.publishedDate,
                            )
                        }.distinctBy { it.id }
                val target = targets.singleOrNull() ?: return@mapNotNull null
                oldId to catalogRows.first { it.episodeId == target.id }
            }.toMap()
    }
}

/**
 * Best-effort, transactional listener-state re-key after a full feed persist: the orphan rematch.
 *
 * Narrowed in P4 of the own-episode-IDs plan (plan section 8, re-key paths; docs/EPISODE_IDS.md §5):
 * - It consults aliases first. Only legacy phone-minted (negative) IDs with no alias or an
 *   unresolved one are rematched; an ID with a trusted alias already resolves through it, and an
 *   ambiguous one is never moved.
 * - The merged like is the latest like decision (with the episode's group settings) instead of
 *   either row's like, so a cleared like can't come back; completion is kept from either row
 *   unless the group settings cleared it later.
 * - The moved ID is recorded as a trusted alias of the catalog row's episode when that row has
 *   one, so a later copy of it (a cloud pull, a restore) counts as the same episode and isn't
 *   rematched again.
 *
 * COMPAT(own-ids): the orphan rematch for unresolved legacy negative IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal class LocalCatalogListenerStateReconciler(
    private val database: BoxLoreDatabase,
    private val downloadCacheRelinker: DownloadCacheRelinker = DownloadCacheRelinker { _, _ -> false },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun reconcile(podcastId: String, catalogRows: List<LocalEpisodeEntity>,) {
        val history = database.listeningHistoryDao().getHistoryForPodcast(podcastId)
        val downloads = database.downloadedEpisodeDao().getDownloadsForPodcast(podcastId)
        val queue = database.queueDao().getAllQueueItemsSync().filter { it.podcastId == podcastId }
        val allReferences =
            history.map { item ->
                LocalCatalogListenerReference(
                    episodeId = item.episodeId,
                    enclosureUrl = item.episodeAudioUrl,
                    title = item.episodeTitle,
                    publishedDate = null,
                )
            } +
                downloads.map { item ->
                    LocalCatalogListenerReference(
                        episodeId = item.episodeId,
                        enclosureUrl = null,
                        title = item.episodeTitle,
                        publishedDate = item.publishedDate,
                    )
                } +
                queue.map { item ->
                    LocalCatalogListenerReference(
                        episodeId = item.episodeId,
                        enclosureUrl = item.audioUrl,
                        title = item.title,
                        publishedDate = item.pubDate,
                    )
                }
        val settled = settledIds(allReferences.map { it.episodeId }.filter { LocalCatalogListenerRemapLogic.isLegacyLocalId(it) })
        val references = allReferences.filter { it.episodeId !in settled }
        val mappings = LocalCatalogListenerRemapLogic.mappings(catalogRows, references)
        for ((oldId, target) in mappings) {
            val targetAlias = targetAlias(target)
            migrateHistory(oldId, target, targetAlias)
            migrateDownload(oldId, target)
            migrateQueue(oldId, target)
            database.listeningSessionDao().reassignEpisodeId(oldId, target.episodeId)
            migrateRollups(oldId, target.episodeId)
            recordMovedAlias(oldId, targetAlias)
        }
    }

    /** IDs among [ids] whose alias is trusted or ambiguous: resolved, or deliberately never moved. */
    private suspend fun settledIds(ids: List<String>): Set<String> = ListenerStateMoves.settledIds(
        ids.distinct().chunked(MAX_IDS_PER_QUERY).flatMap { database.episodeIdentityDao().getAliases(it) },
    )

    /** The catalog row's trusted alias, or null when it has none (its episode has no group yet). */
    private suspend fun targetAlias(target: LocalEpisodeEntity): EpisodeAliasEntity? =
        ListenerStateMoves.trustedTarget(database.episodeIdentityDao().getAlias(target.episodeId), target.podcastId)

    private suspend fun migrateHistory(oldId: String, target: LocalEpisodeEntity, targetAlias: EpisodeAliasEntity?,) {
        val dao = database.listeningHistoryDao()
        val old = dao.getHistoryItem(oldId) ?: return
        val existing = dao.getHistoryItem(target.episodeId)
        val groupState = targetAlias?.let { database.episodeIdentityDao().getGroupState(it.podcastId, it.catalogKey!!) }
        dao.upsert(mergeHistory(old, existing, target, groupState))
        dao.delete(oldId)
    }

    /** Records [oldId] as a trusted alias of [targetAlias]'s episode; nothing when the target has no alias. */
    private suspend fun recordMovedAlias(oldId: String, targetAlias: EpisodeAliasEntity?) {
        ListenerStateMoves.movedAlias(oldId, targetAlias, nowMs())?.let { database.episodeIdentityDao().upsertAlias(it) }
    }

    private suspend fun migrateDownload(oldId: String, target: LocalEpisodeEntity,) {
        val dao = database.downloadedEpisodeDao()
        val old = dao.getDownload(oldId) ?: return
        val existing = dao.getDownload(target.episodeId)
        if (old.status == DownloadedEpisodeEntity.STATUS_COMPLETED &&
            existing != null &&
            existing.status != DownloadedEpisodeEntity.STATUS_COMPLETED
        ) {
            return
        }
        if (old.status == DownloadedEpisodeEntity.STATUS_COMPLETED &&
            existing?.status != DownloadedEpisodeEntity.STATUS_COMPLETED
        ) {
            val relinked = downloadCacheRelinker.relink(oldId, target.episodeId)
            if (!relinked) return
        }
        dao.insert(mergeDownload(old, existing, target))
        dao.delete(oldId)
    }

    private suspend fun migrateQueue(oldId: String, target: LocalEpisodeEntity,) {
        val dao = database.queueDao()
        dao
            .getAllQueueItemsSync()
            .filter { it.episodeId == oldId }
            .forEach { old ->
                dao.updateQueueItem(
                    old.copy(
                        episodeId = target.episodeId,
                        title = target.title,
                        imageUrl = target.imageUrl ?: old.imageUrl,
                        audioUrl = target.audioUrl,
                        duration = target.duration,
                        pubDate = target.publishedDate,
                        description = target.description,
                        chaptersUrl = target.chaptersUrl,
                        transcriptUrl = target.transcriptUrl,
                        episodeType = target.episodeType,
                        seasonNumber = target.seasonNumber,
                        episodeNumber = target.episodeNumber,
                        enclosureType = target.enclosureType,
                    ),
                )
            }
    }

    private suspend fun migrateRollups(oldId: String, newId: String,) {
        val dao = database.listeningRollupDao()
        val oldRows = dao.getRollupsForEpisode(oldId)
        for (old in oldRows) {
            val existing = dao.getRollup(old.localDay, newId)
            dao.upsertRollup(mergeRollup(old, existing, newId))
        }
        dao.deleteRollupsForEpisode(oldId)
    }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
    }
}

/**
 * [old]'s row merged into the catalog row [target]. Progress, length and play time keep the larger
 * value. The like is the latest like decision across both rows and [groupState] (the episode's
 * group settings), so an older liked row can't undo a later unlike. Completion is kept from either
 * row unless [groupState] records a later clear (own-episode-IDs P4, plan section 8).
 */
internal fun mergeHistory(
    old: ListeningHistoryEntity,
    existing: ListeningHistoryEntity?,
    target: LocalEpisodeEntity,
    groupState: EpisodeGroupStateEntity? = null,
): ListeningHistoryEntity {
    val rows = listOfNotNull(old, existing)
    val like = EpisodeListenerProjection.likeDecision(rows, groupState)
    val completed = mergedCompletion(rows, groupState)
    return old.copy(
        episodeId = target.episodeId,
        episodeTitle = target.title,
        episodeImageUrl = target.imageUrl ?: existing?.episodeImageUrl ?: old.episodeImageUrl,
        episodeAudioUrl = target.audioUrl,
        progressMs = maxOf(old.progressMs, existing?.progressMs ?: 0L),
        durationMs = maxOf(old.durationMs, existing?.durationMs ?: 0L),
        isCompleted = completed,
        isLiked = like.value,
        likedAt = maxOf(old.likedAt, existing?.likedAt ?: 0L, like.at),
        lastPlayedAt = maxOf(old.lastPlayedAt, existing?.lastPlayedAt ?: 0L),
        isDirty = true,
        syncedAt = 0L,
        enclosureType = target.enclosureType ?: existing?.enclosureType ?: old.enclosureType,
        isManualCompletion = completed && rows.any { it.isManualCompletion },
        isBulkCompletion = completed && rows.any { it.isBulkCompletion },
        episodeDescription =
        target.description.ifBlank {
            existing?.episodeDescription ?: old.episodeDescription.orEmpty()
        },
    )
}

/** Completed when either row is, unless the group settings hold a decision at least as late as every completed row's. */
private fun mergedCompletion(rows: List<ListeningHistoryEntity>, groupState: EpisodeGroupStateEntity?,): Boolean {
    val group = listOfNotNull(
        groupState?.completedAt?.takeIf { it > 0L }?.let { EpisodeListenerProjection.Decision(it, true) },
        groupState?.completeClearedAt?.takeIf { it > 0L }?.let { EpisodeListenerProjection.Decision(it, false) },
    ).maxByOrNull { it.at }
    val latestCompletedRow = rows.filter { it.isCompleted }.maxOfOrNull { it.lastPlayedAt }
    if (group != null && (latestCompletedRow == null || group.at >= latestCompletedRow)) return group.value
    return latestCompletedRow != null
}

internal fun mergeDownload(
    old: DownloadedEpisodeEntity,
    existing: DownloadedEpisodeEntity?,
    target: LocalEpisodeEntity,
): DownloadedEpisodeEntity {
    val preferred =
        when {
            existing == null -> old
            old.status == DownloadedEpisodeEntity.STATUS_COMPLETED &&
                existing.status != DownloadedEpisodeEntity.STATUS_COMPLETED -> old
            else -> existing
        }
    return preferred.copy(
        episodeId = target.episodeId,
        episodeTitle = target.title,
        episodeDescription = target.description,
        episodeImageUrl = target.imageUrl ?: preferred.episodeImageUrl,
        durationMs =
        target.duration
            .toLong()
            .takeIf { it > 0L }
            ?.times(1_000L)
            ?: preferred.durationMs,
        publishedDate = target.publishedDate,
        downloadedAt = maxOf(old.downloadedAt, existing?.downloadedAt ?: 0L),
        sizeBytes = maxOf(old.sizeBytes, existing?.sizeBytes ?: 0L),
        status = preferredDownloadStatus(old.status, existing?.status),
        isSmartDownloaded = old.isSmartDownloaded || existing?.isSmartDownloaded == true,
    )
}

private fun preferredDownloadStatus(first: Int, second: Int?,): Int = listOfNotNull(first, second)
    .minBy { status ->
        when (status) {
            DownloadedEpisodeEntity.STATUS_COMPLETED -> 0
            DownloadedEpisodeEntity.STATUS_DOWNLOADING -> 1
            DownloadedEpisodeEntity.STATUS_QUEUED -> 2
            else -> 3
        }
    }

internal fun mergeRollup(old: ListeningRollupEntity, existing: ListeningRollupEntity?, newEpisodeId: String,): ListeningRollupEntity = old.copy(
    episodeId = newEpisodeId,
    consumedMs = old.consumedMs + (existing?.consumedMs ?: 0L),
    sessionCount = old.sessionCount + (existing?.sessionCount ?: 0),
    completionCount = old.completionCount + (existing?.completionCount ?: 0),
    lastListenedAt = maxOf(old.lastListenedAt, existing?.lastListenedAt ?: 0L),
    morningMs = old.morningMs + (existing?.morningMs ?: 0L),
    afternoonMs = old.afternoonMs + (existing?.afternoonMs ?: 0L),
    eveningMs = old.eveningMs + (existing?.eveningMs ?: 0L),
    nightMs = old.nightMs + (existing?.nightMs ?: 0L),
)
