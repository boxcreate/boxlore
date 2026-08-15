package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException

internal const val LOCAL_CATALOG_WINDOW_BOUND = 200

internal suspend fun PodcastRepository.isLocalCatalogReady(podcastId: String): Boolean {
    if (podcastId.startsWith("rss:")) return false
    return try {
        localEpisodeCatalog?.isReady(podcastId) == true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "local catalog ready check failed for $podcastId", e)
        false
    }
}

internal suspend fun PodcastRepository.localCatalogMeta(
    podcastId: String,
): LocalEpisodeCatalogPort.PodcastMeta {
    val local = runCatching { rssRepository.getPodcast(podcastId) }.getOrNull()
    return LocalEpisodeCatalogPort.PodcastMeta(
        title = local?.title,
        imageUrl = local?.imageUrl,
        genre = local?.genre,
        artist = local?.author,
    )
}

internal suspend fun PodcastRepository.getLocalCatalogPage(
    podcastId: String,
    limit: Int,
    offset: Int,
    sort: String,
): PodcastRepository.EpisodePage {
    val catalog = localEpisodeCatalog ?: return PodcastRepository.EpisodePage(emptyList(), false)
    val meta = localCatalogMeta(podcastId)
    val episodes = catalog.getPage(podcastId, limit, offset, sort, meta)
    val total = catalog.count(podcastId)
    return PodcastRepository.EpisodePage(
        episodes = episodes,
        hasMore = offset + episodes.size < total,
        sourceCount = episodes.size,
    )
}

internal suspend fun PodcastRepository.searchLocalCatalog(
    podcastId: String,
    query: String,
): List<Episode> {
    val catalog = localEpisodeCatalog ?: return emptyList()
    return catalog.search(podcastId, query, localCatalogMeta(podcastId))
}

internal suspend fun PodcastRepository.loadLocalCatalogTips(
    podcastIndexIds: List<String>,
): Map<String, Episode> {
    val catalog = localEpisodeCatalog ?: return emptyMap()
    if (podcastIndexIds.isEmpty()) return emptyMap()
    return podcastIndexIds.mapNotNull { id ->
        val tip = catalog.newest(id, localCatalogMeta(id)) ?: return@mapNotNull null
        id to tip
    }.toMap()
}

internal suspend fun PodcastRepository.getLocalCatalogWindow(
    podcastId: String,
    sort: String,
    aroundEpisodeId: String?,
): List<Episode> {
    val catalog = localEpisodeCatalog ?: return emptyList()
    return catalog.getWindow(
        podcastId = podcastId,
        sort = sort,
        bound = LOCAL_CATALOG_WINDOW_BOUND,
        aroundEpisodeId = aroundEpisodeId,
        meta = localCatalogMeta(podcastId),
    )
}

internal suspend fun PodcastRepository.getLocalCatalogEpisode(episodeId: String): Episode? =
    try {
        localEpisodeCatalog?.getEpisode(episodeId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "local catalog getEpisode failed for $episodeId", e)
        null
    }
