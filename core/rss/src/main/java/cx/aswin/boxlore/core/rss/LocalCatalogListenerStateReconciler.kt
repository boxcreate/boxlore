package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.EpisodeListenerProjection
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
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

    fun mappings(catalogRows: List<LocalEpisodeEntity>, references: List<LocalCatalogListenerReference>,): Map<String, LocalEpisodeEntity> =
        rematches(catalogRows, references).mapValues { it.value.target }

    /**
     * [mappings] with how each target was found: [LocalCatalogRematch.exact] when a reference's
     * enclosure address names exactly one catalog row, false for the title and date fallback.
     */
    fun rematches(catalogRows: List<LocalEpisodeEntity>, references: List<LocalCatalogListenerReference>,): Map<String, LocalCatalogRematch> {
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
                val row = catalogRows.first { it.episodeId == target.id }
                val exact = grouped.any { reference -> isUniqueEnclosureOf(reference.enclosureUrl, row, catalogRows) }
                oldId to LocalCatalogRematch(row, exact)
            }.toMap()
    }

    private fun isUniqueEnclosureOf(enclosureUrl: String?, row: LocalEpisodeEntity, catalogRows: List<LocalEpisodeEntity>,): Boolean {
        val enclosure = enclosureUrl?.trim().orEmpty()
        if (enclosure.isEmpty() || row.audioUrl.trim() != enclosure) return false
        return catalogRows.count { it.audioUrl.trim() == enclosure } == 1
    }
}

/**
 * The catalog row an orphaned ID's listener state moves to, and whether the match was exact (its
 * enclosure address names that one row) rather than the title and publish-date fallback.
 */
internal data class LocalCatalogRematch(val target: LocalEpisodeEntity, val exact: Boolean,)

/** Stays under SQLite's bound-parameter limit. */
private const val KNOWN_ID_LOOKUP_CHUNK = 500

/**
 * Every history, download and queue row of [podcastId], as the references the orphan rematch reads:
 * history carries its audio address, downloads their title and date, the queue all three.
 *
 * Shared by the reconciler and, since P4 PR 9, by the catalog read that lets a new item keep an ID
 * these rows already hold ([ListenerEvidence]).
 */
internal suspend fun BoxLoreDatabase.listenerReferences(podcastId: String): List<LocalCatalogListenerReference> {
    val history = listeningHistoryDao().getHistoryForPodcast(podcastId)
    val downloads = downloadedEpisodeDao().getDownloadsForPodcast(podcastId)
    val queue = queueDao().getAllQueueItemsSync().filter { it.podcastId == podcastId }
    return history.map { item ->
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
}

/**
 * Best-effort, transactional listener-state re-key after a full feed persist: the orphan rematch.
 *
 * Narrowed in P4 of the own-episode-IDs plan (plan section 8, re-key paths; decision D8;
 * docs/EPISODE_IDS.md §5) to unresolved legacy negative IDs, after consulting aliases:
 * - **Known episodes are not orphans** (P4 PR 9). A negative listener ID that a catalog row of any
 *   show still holds, or whose alias is trusted or ambiguous, is another episode (an older duplicate
 *   row, a changed GUID, an item the feed dropped, an evicted row's ID) and never moves. An orphan
 *   the read could prove a new item owns (its own mint, or a unique listener match) is already that
 *   item's ID ([ListenerIdAdoption]).
 * - **Trusted orphans join by key.** When a catalog row now holds a trusted orphan's (show, key)
 *   under another ID, that row's ID joins the episode as a trusted alias (`catalog_key_match`), so
 *   both IDs read as one episode and nothing moves.
 * - **Only phone-minted IDs with no alias or an unresolved one are rematched and moved.** A moved
 *   ID's history merges into the row the catalog row's episode reads (its owner when the episode is
 *   stored under several IDs). The merged like is the latest like decision (with the episode's group
 *   settings), so a cleared like can't come back; completion is kept from either row unless the
 *   group settings cleared it later.
 * - When the match was exact (the enclosure address names one catalog row) and the catalog row has
 *   a trusted alias, the moved ID is recorded as a trusted alias of that episode (`orphan_rematch`),
 *   so a later copy of it (a cloud pull, a restore) counts as the same episode and isn't rematched
 *   again. A title and date match records no alias: it is not proof.
 *
 * COMPAT(own-ids): the orphan rematch for unresolved legacy negative IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal class LocalCatalogListenerStateReconciler(
    private val database: BoxLoreDatabase,
    private val downloadCacheRelinker: DownloadCacheRelinker = DownloadCacheRelinker { _, _ -> false },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun reconcile(podcastId: String, catalogRows: List<LocalEpisodeEntity>,) {
        val allReferences = database.listenerReferences(podcastId)
        val written = catalogRows.mapTo(HashSet(), LocalEpisodeEntity::episodeId)
        val orphanIds =
            allReferences
                .map { it.episodeId }
                .filter { it !in written && LocalCatalogListenerRemapLogic.isLegacyLocalId(it) }
                .distinct()
        val orphanAliases = aliasesOf(orphanIds)
        joinTrustedOrphans(catalogRows, orphanAliases)
        val known = ListenerStateMoves.settledIds(orphanAliases) + catalogHeldIds(orphanIds)
        val references = allReferences.filter { it.episodeId !in known }
        val rematches = LocalCatalogListenerRemapLogic.rematches(catalogRows, references)
        for ((oldId, rematch) in rematches) {
            val target = rematch.target
            val targetAlias = targetAlias(target)
            migrateHistory(oldId, target, targetAlias)
            migrateDownload(oldId, target)
            migrateQueue(oldId, target)
            database.listeningSessionDao().reassignEpisodeId(oldId, target.episodeId)
            migrateRollups(oldId, target.episodeId)
            if (rematch.exact) recordMovedAlias(oldId, targetAlias)
        }
    }

    private suspend fun aliasesOf(ids: List<String>): List<EpisodeAliasEntity> =
        ids.distinct().chunked(KNOWN_ID_LOOKUP_CHUNK).flatMap { database.episodeIdentityDao().getAliases(it) }

    /**
     * Which of [ids] a catalog row of any show still holds: such an ID is a known episode, not an
     * orphan, and re-keying its listener state onto another row would merge two episodes.
     *
     * Added in P4 PR 9 (own-IDs plan section 4: a changed GUID is a new episode; nothing is
     * re-keyed). New episodes now take recipe IDs, so a phone mint left by an older row no longer
     * equals the new row's ID.
     * COMPAT(own-ids): known episodes keep their listener state. Permanent: never remove (MIGRATION.md §5).
     */
    private suspend fun catalogHeldIds(ids: List<String>): Set<String> =
        ids.chunked(KNOWN_ID_LOOKUP_CHUNK).flatMapTo(HashSet()) { chunk ->
            database.localEpisodeCatalogDao().findOwners(chunk).map { it.episodeId }
        }

    /**
     * For each trusted orphan whose (show, key) a catalog row now holds under another ID, records
     * that row's ID as a trusted alias of the same episode ([ListenerStateMoves.joinedCatalogAlias]),
     * so the two IDs read as one episode and no state moves.
     */
    private suspend fun joinTrustedOrphans(catalogRows: List<LocalEpisodeEntity>, orphanAliases: List<EpisodeAliasEntity>,) {
        val dao = database.episodeIdentityDao()
        val catalogAliases = aliasesOf(catalogRows.map { it.episodeId }).associateBy { it.aliasId }
        for (orphan in orphanAliases) {
            val row = ListenerStateMoves.catalogRowOfKey(orphan, catalogRows) ?: continue
            ListenerStateMoves.joinedCatalogAlias(orphan, row, catalogAliases[row.episodeId], nowMs())?.let { dao.upsertAlias(it) }
        }
    }

    /** The catalog row's trusted alias, or null when it has none (its episode has no group yet). */
    private suspend fun targetAlias(target: LocalEpisodeEntity): EpisodeAliasEntity? =
        ListenerStateMoves.trustedTarget(database.episodeIdentityDao().getAlias(target.episodeId), target.podcastId)

    /**
     * Merges [oldId]'s row into the row [target]'s episode reads: [target]'s own, or its episode's
     * owner when the episode is stored under several IDs, so the moved progress isn't hidden.
     */
    private suspend fun migrateHistory(oldId: String, target: LocalEpisodeEntity, targetAlias: EpisodeAliasEntity?,) {
        val dao = database.listeningHistoryDao()
        val old = dao.getHistoryItem(oldId) ?: return
        val intoId = if (targetAlias != null) database.episodeListenerStore().historyId(target.episodeId) else target.episodeId
        val existing = dao.getHistoryItem(intoId)
        val groupState = targetAlias?.let { database.episodeIdentityDao().getGroupState(it.podcastId, it.catalogKey!!) }
        dao.upsert(mergeHistory(old, existing, target, groupState).copy(episodeId = intoId))
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
    val completed = ListenerStateMoves.mergedCompletion(rows, groupState)
    return old.copy(
        episodeId = target.episodeId,
        episodeTitle = target.title,
        episodeImageUrl = listOfNotNull(target.imageUrl, existing?.episodeImageUrl, old.episodeImageUrl).firstOrNull(),
        episodeAudioUrl = target.audioUrl,
        progressMs = maxOf(old.progressMs, existing?.progressMs ?: 0L),
        durationMs = maxOf(old.durationMs, existing?.durationMs ?: 0L),
        isCompleted = completed,
        isLiked = like.value,
        likedAt = maxOf(old.likedAt, existing?.likedAt ?: 0L, like.at),
        lastPlayedAt = maxOf(old.lastPlayedAt, existing?.lastPlayedAt ?: 0L),
        isDirty = true,
        syncedAt = 0L,
        enclosureType = listOfNotNull(target.enclosureType, existing?.enclosureType, old.enclosureType).firstOrNull(),
        isManualCompletion = completed && rows.any { it.isManualCompletion },
        isBulkCompletion = completed && rows.any { it.isBulkCompletion },
        episodeDescription =
        target.description.ifBlank {
            existing?.episodeDescription ?: old.episodeDescription.orEmpty()
        },
    )
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
