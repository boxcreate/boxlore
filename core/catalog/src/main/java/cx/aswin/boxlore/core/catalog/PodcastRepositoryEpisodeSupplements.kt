package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException

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

internal suspend fun PodcastRepository.mergeCachedSupplementsNewest(
    podcastId: String,
    piEpisodes: List<Episode>,
): List<Episode> =
    PodcastEpisodeSupplementMerge.mergePage(
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
        episodeSupplementRepository?.getEpisodesForPodcast(podcastId).orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "Supplement list failed for $podcastId", e)
        emptyList()
    }
}

internal suspend fun PodcastRepository.loadOptedInPodcastIds(): Set<String> =
    try {
        episodeSupplementRepository?.listOptedInPodcastIds().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "Failed to load supplement opt-ins", e)
        emptySet()
    }

internal suspend fun PodcastRepository.fetchPiSyncTips(podcastIndexIds: List<String>): Map<String, Episode> {
    if (podcastIndexIds.isEmpty()) return emptyMap()
    val request = cx.aswin.boxlore.core.network.model.SyncRequest(podcastIndexIds)
    val response = api.syncSubscriptions(publicKey, request).execute()
    if (!response.isSuccessful || response.body() == null) return emptyMap()
    return response.body()!!.items.mapNotNull { item ->
        val ep =
            item.latestEpisode?.let { mapToEpisode(it) }?.copy(
                podcastId = item.id,
            )
        if (ep != null) item.id to ep else null
    }.toMap()
}

internal suspend fun PodcastRepository.loadCachedFeedTips(podcastIndexIds: List<String>): Map<String, Episode> {
    if (podcastIndexIds.isEmpty()) return emptyMap()
    return podcastIndexIds.mapNotNull { id ->
        val tip = loadCachedSupplements(id).maxByOrNull { it.publishedDate } ?: return@mapNotNull null
        id to tip
    }.toMap()
}
