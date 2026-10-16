package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit

internal suspend fun PodcastRepository.mergeCachedSupplementsIntoPage(
    podcastId: String,
    page: PodcastRepository.EpisodePage,
    offset: Int,
    sort: String,
): PodcastRepository.EpisodePage {
    val merged =
        PodcastEpisodeSupplementMerge.mergePage(
            podcastId = podcastId,
            piEpisodes = page.episodes,
            supplements = loadCachedSupplements(podcastId),
            sort = PodcastEpisodeSupplementMerge.sortFromQuery(sort),
            injectExtras = offset == 0,
        )
    return page.copy(episodes = merged)
}

internal suspend fun PodcastRepository.mergeCachedSupplementsNewest(podcastId: String, piEpisodes: List<Episode>,): List<Episode> = PodcastEpisodeSupplementMerge.mergePage(
    podcastId = podcastId,
    piEpisodes = piEpisodes,
    supplements = loadCachedSupplements(podcastId),
    sort = PodcastEpisodeSupplementMerge.sortFromQuery("newest"),
    injectExtras = true,
)

internal suspend fun PodcastRepository.unionCachedSupplementSearch(
    podcastId: String,
    query: String,
    networkResults: List<Episode>,
): List<Episode> {
    val matches =
        try {
            episodeSupplementRepository?.search(podcastId, query).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Supplement search failed for $podcastId", e)
            emptyList()
        }
    return PodcastEpisodeSupplementMerge.unionSearch(podcastId, networkResults, matches)
}

internal suspend fun PodcastRepository.loadCachedSupplements(podcastId: String): List<Episode> {
    if (podcastId.startsWith("rss:")) return emptyList()
    return try {
        val local = rssRepository.getPodcast(podcastId)
        episodeSupplementRepository
            ?.getEpisodesForPodcast(
                podcastIndexId = podcastId,
                podcastTitle = local?.title,
                podcastImageUrl = local?.imageUrl,
                podcastGenre = local?.genre,
                podcastArtist = local?.author,
            ).orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "Supplement list failed for $podcastId", e)
        emptyList()
    }
}

internal suspend fun PodcastRepository.loadOptedInPodcastIds(): Set<String> = try {
    episodeSupplementRepository?.listOptedInPodcastIds().orEmpty()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "Failed to load supplement opt-ins", e)
    emptySet()
}

/** Ids per `/sync` call. The API serves only the first 40 ids of a request. */
internal const val PI_SYNC_CHUNK_SIZE = 20

/** `/sync` calls in flight at once for one library sync. */
internal const val PI_SYNC_PARALLELISM = 2

/**
 * Latest PI episode per show. Whole libraries (Smart Downloads, backup restore) go out in chunks of
 * [PI_SYNC_CHUNK_SIZE], at most [PI_SYNC_PARALLELISM] at a time, so every show is synced; a failed
 * chunk leaves only its own shows without a tip.
 */
internal suspend fun PodcastRepository.fetchPiSyncTips(podcastIndexIds: List<String>): Map<String, Episode> {
    val ids = podcastIndexIds.distinct()
    if (ids.isEmpty()) return emptyMap()
    return kotlinx.coroutines.coroutineScope {
        val limit = kotlinx.coroutines.sync.Semaphore(PI_SYNC_PARALLELISM)
        ids.chunked(PI_SYNC_CHUNK_SIZE).map { chunk ->
            async { limit.withPermit { fetchPiSyncChunk(chunk) } }
        }.awaitAll().fold(emptyMap()) { tips, chunk -> tips + chunk }
    }
}

private fun PodcastRepository.fetchPiSyncChunk(podcastIndexIds: List<String>): Map<String, Episode> = try {
    val request =
        cx.aswin.boxlore.core.network.model
            .SyncRequest(podcastIndexIds)
    val response = api.syncSubscriptions(publicKey, request).execute()
    val body = response.body()
    if (!response.isSuccessful || body == null) {
        emptyMap()
    } else {
        body.items
            .mapNotNull { item ->
                val ep =
                    item.latestEpisode?.let { mapToEpisode(it) }?.copy(
                        podcastId = item.id,
                    )
                if (ep != null) item.id to ep else null
            }.toMap()
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.w("PodcastRepository", "PI sync chunk of ${podcastIndexIds.size} failed", e)
    emptyMap()
}

internal suspend fun PodcastRepository.loadCachedFeedTips(podcastIndexIds: List<String>): Map<String, Episode> {
    if (podcastIndexIds.isEmpty()) return emptyMap()
    return podcastIndexIds
        .mapNotNull { id ->
            val tip = loadCachedSupplements(id).maxByOrNull { it.publishedDate } ?: return@mapNotNull null
            id to tip
        }.toMap()
}

/** Isolate each saved RSS feed; PI sync can run while publisher requests are in flight. */
internal suspend fun PodcastRepository.loadRssSyncTips(feedIds: List<String>, runPostPersistCallback: Boolean): Map<String, Episode> =
    kotlinx.coroutines.coroutineScope {
        val limit = kotlinx.coroutines.sync.Semaphore(2)
        feedIds.filter { it.startsWith("rss:") }.distinct().map { id ->
            async {
                limit.withPermit { loadRssSyncTip(id, runPostPersistCallback) }
            }
        }.awaitAll().filterNotNull().toMap()
    }

private suspend fun PodcastRepository.loadRssSyncTip(id: String, runPostPersistCallback: Boolean): Pair<String, Episode>? = try {
    val row = rssRepository.getPodcast(id)
    rssRepository.episodeCatalog.refresh(
        LocalEpisodeCatalogPort.RefreshRequest(id, row?.feedUrl.orEmpty(), runPostPersistCallback = runPostPersistCallback),
    )
    rssRepository.getPodcast(id)?.latestEpisode?.let { id to it }
} catch (error: kotlinx.coroutines.CancellationException) {
    throw error
} catch (_: Exception) {
    null
}
