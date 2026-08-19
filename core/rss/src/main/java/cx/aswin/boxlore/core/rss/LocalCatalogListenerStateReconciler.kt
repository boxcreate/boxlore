package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
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
    fun mappings(
        catalogRows: List<LocalEpisodeEntity>,
        references: List<LocalCatalogListenerReference>,
    ): Map<String, LocalEpisodeEntity> {
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
            .filter { reference ->
                reference.episodeId.toLongOrNull()?.let { it < 0L } == true &&
                    reference.episodeId !in catalogIds
            }.groupBy(LocalCatalogListenerReference::episodeId)
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

/** Best-effort, transactional listener-state re-key after a full feed persist. */
internal class LocalCatalogListenerStateReconciler(
    private val database: BoxLoreDatabase,
    private val downloadCacheRelinker: DownloadCacheRelinker = DownloadCacheRelinker { _, _ -> false },
) {
    suspend fun reconcile(
        podcastId: String,
        catalogRows: List<LocalEpisodeEntity>,
    ) {
        val history = database.listeningHistoryDao().getHistoryForPodcast(podcastId)
        val downloads = database.downloadedEpisodeDao().getDownloadsForPodcast(podcastId)
        val queue = database.queueDao().getAllQueueItemsSync().filter { it.podcastId == podcastId }
        val references =
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
        val mappings = LocalCatalogListenerRemapLogic.mappings(catalogRows, references)
        for ((oldId, target) in mappings) {
            migrateHistory(oldId, target)
            migrateDownload(oldId, target)
            migrateQueue(oldId, target)
            database.listeningSessionDao().reassignEpisodeId(oldId, target.episodeId)
            migrateRollups(oldId, target.episodeId)
        }
    }

    private suspend fun migrateHistory(
        oldId: String,
        target: LocalEpisodeEntity,
    ) {
        val dao = database.listeningHistoryDao()
        val old = dao.getHistoryItem(oldId) ?: return
        val existing = dao.getHistoryItem(target.episodeId)
        dao.upsert(mergeHistory(old, existing, target))
        dao.delete(oldId)
    }

    private suspend fun migrateDownload(
        oldId: String,
        target: LocalEpisodeEntity,
    ) {
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

    private suspend fun migrateQueue(
        oldId: String,
        target: LocalEpisodeEntity,
    ) {
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

    private suspend fun migrateRollups(
        oldId: String,
        newId: String,
    ) {
        val dao = database.listeningRollupDao()
        val oldRows = dao.getRollupsForEpisode(oldId)
        for (old in oldRows) {
            val existing = dao.getRollup(old.localDay, newId)
            dao.upsertRollup(mergeRollup(old, existing, newId))
        }
        dao.deleteRollupsForEpisode(oldId)
    }
}

internal fun mergeHistory(
    old: ListeningHistoryEntity,
    existing: ListeningHistoryEntity?,
    target: LocalEpisodeEntity,
): ListeningHistoryEntity =
    old.copy(
        episodeId = target.episodeId,
        episodeTitle = target.title,
        episodeImageUrl = target.imageUrl ?: existing?.episodeImageUrl ?: old.episodeImageUrl,
        episodeAudioUrl = target.audioUrl,
        progressMs = maxOf(old.progressMs, existing?.progressMs ?: 0L),
        durationMs = maxOf(old.durationMs, existing?.durationMs ?: 0L),
        isCompleted = old.isCompleted || existing?.isCompleted == true,
        isLiked = old.isLiked || existing?.isLiked == true,
        lastPlayedAt = maxOf(old.lastPlayedAt, existing?.lastPlayedAt ?: 0L),
        isDirty = true,
        syncedAt = 0L,
        enclosureType = target.enclosureType ?: existing?.enclosureType ?: old.enclosureType,
        isManualCompletion = old.isManualCompletion || existing?.isManualCompletion == true,
        isBulkCompletion = old.isBulkCompletion || existing?.isBulkCompletion == true,
        episodeDescription =
            target.description.ifBlank {
                existing?.episodeDescription ?: old.episodeDescription.orEmpty()
            },
    )

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

private fun preferredDownloadStatus(
    first: Int,
    second: Int?,
): Int =
    listOfNotNull(first, second)
        .minBy { status ->
            when (status) {
                DownloadedEpisodeEntity.STATUS_COMPLETED -> 0
                DownloadedEpisodeEntity.STATUS_DOWNLOADING -> 1
                DownloadedEpisodeEntity.STATUS_QUEUED -> 2
                else -> 3
            }
        }

internal fun mergeRollup(
    old: ListeningRollupEntity,
    existing: ListeningRollupEntity?,
    newEpisodeId: String,
): ListeningRollupEntity =
    old.copy(
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
