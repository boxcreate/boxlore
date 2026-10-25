package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.persistableArtworkUrl
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.SyncEpisodeIdentityFields
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import kotlinx.coroutines.CancellationException

/**
 * Builds the queue row of a pulled queue item this device doesn't queue yet ([QueueSyncResolver]
 * tiers 2-5): the peer's metadata, else this device's catalog row, else its history, else the
 * network, else a placeholder. The row always keeps the pulled item's ID.
 *
 * Changed in P4 PR 11 of the own-episode-IDs plan (plan section 8, cloud sync identity): the
 * catalog and history tiers read the episode under any of its trusted IDs (docs/EPISODE_IDS.md
 * section 5), and the network tier asks `/v2/episode` for the recipe ID the server sent before the
 * stored ID, so a recipe ID never reaches Podcast Index.
 * COMPAT(own-ids): pulled queue items read an episode under any of its IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal class QueueItemHydration(
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository?,
) {
    /** Tiers 2-5 for [dto]. */
    suspend fun hydrate(dto: QueueItemSyncDto): QueueItem {
        // Tier 2: Remote DTO Rich Metadata (if provided by peer device)
        val remoteTitle = dto.title
        if (!remoteTitle.isNullOrBlank()) return hydrateFromPeer(dto, remoteTitle)

        return hydrateFromCatalogOrRss(dto)
            ?: hydrateFromHistory(dto)
            ?: hydrateFromNetwork(dto)
            ?: fallbackQueueItem(dto)
    }

    private suspend fun hydrateFromPeer(dto: QueueItemSyncDto, title: String): QueueItem {
        // A peer's player-only cover link is dropped; this device's catalogue may still have the cover.
        val imageUrl = persistableArtworkUrl(dto.imageUrl)
        val catalog = if (imageUrl == null) hydrateFromCatalogOrRss(dto) else null
        return QueueItem(
            episodeId = dto.episodeId,
            title = title,
            podcastId = dto.podcastId,
            podcastTitle = dto.podcastTitle ?: "",
            imageUrl = imageUrl ?: catalog?.imageUrl?.takeIf { it.isNotBlank() },
            podcastImageUrl = persistableArtworkUrl(dto.podcastImageUrl),
            audioUrl = dto.audioUrl ?: "",
            duration = dto.duration ?: 0,
            pubDate = dto.pubDate ?: 0L,
            description = dto.description,
            position = dto.position,
            contextType = dto.contextType ?: "MANUAL",
            contextSourceId = dto.contextSourceId,
        )
    }

    /** Tier 3: the catalog row of [dto]'s ID, else of another ID of its episode (the owner first). */
    private suspend fun hydrateFromCatalogOrRss(dto: QueueItemSyncDto): QueueItem? {
        for (id in episodeIdsOf(dto.episodeId)) {
            catalogItem(dto, id)?.let { return it }
        }
        return null
    }

    private suspend fun catalogItem(dto: QueueItemSyncDto, catalogId: String): QueueItem? {
        val localCatalogEp = database.localEpisodeCatalogDao().getEpisode(catalogId)
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

        val rssEp = database.rssEpisodeDao().getEpisode(catalogId)
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

    /** Tier 4: the history of [dto]'s episode, under any of its IDs (its projected row). */
    private suspend fun hydrateFromHistory(dto: QueueItemSyncDto): QueueItem? {
        val history = database.episodeListenerStore().projectedHistory(dto.episodeId)
        if (history != null && history.episodeTitle.isNotBlank()) {
            return QueueItem(
                episodeId = dto.episodeId,
                title = history.episodeTitle,
                podcastId = dto.podcastId.ifEmpty { history.podcastId },
                podcastTitle = history.podcastName,
                imageUrl = persistableArtworkUrl(history.episodeImageUrl),
                podcastImageUrl = persistableArtworkUrl(history.podcastImageUrl),
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

    /**
     * Tier 5: the network. The recipe ID the server sent goes to `/v2/episode` with the show
     * (`PodcastRepository.getEpisode`, which never sends a recipe ID to Podcast Index); without one
     * the stored ID is looked up with its show. The item keeps [dto]'s ID either way.
     */
    private suspend fun hydrateFromNetwork(dto: QueueItemSyncDto): QueueItem? {
        val podRepo = podcastRepository ?: return null
        val showId = dto.podcastId.takeIf { V2EpisodeIds.parseShowId(it) != null }
        val lookupId = SyncEpisodeIdentityFields.recipeIdOrNull(dto.recipeId)?.takeIf { showId != null }?.toString() ?: dto.episodeId
        val fetched = fetchEpisode(podRepo, lookupId, showId)
            ?: lookupId.takeIf { it != dto.episodeId }?.let { fetchEpisode(podRepo, dto.episodeId, showId) }
            ?: return null
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

    private suspend fun fetchEpisode(podRepo: PodcastRepository, episodeId: String, showId: String?): Episode? = try {
        podRepo.getEpisode(episodeId, showId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("QueueSyncResolver", "Episode lookup failed for $episodeId", e)
        null
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

    /** [episodeId], then every other trusted ID of its episode (the owner first). */
    private suspend fun episodeIdsOf(episodeId: String): List<String> =
        (listOf(episodeId) + database.episodeListenerStore().memberIds(episodeId)).distinct()
}
