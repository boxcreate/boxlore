package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun PodcastRepository.searchRssEpisodes(feedId: String, query: String,): List<Episode> = try {
    rssRepository.searchEpisodes(feedId, query)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "RSS searchEpisodes failed for $feedId", e)
    emptyList()
}

internal suspend fun PodcastRepository.searchNetworkEpisodes(feedId: String, query: String,): List<Episode> = try {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    val response = api.searchEpisodes(publicKey, resolvedId, query).execute()
    val network =
        if (response.isSuccessful && response.body() != null) {
            response.body()!!.items.mapNotNull { mapToEpisode(it) }
        } else {
            emptyList()
        }
    unionCachedSupplementSearch(resolvedId, query, network)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

internal suspend fun PodcastRepository.getEpisodesImpl(feedId: String): List<Episode> = withContext(Dispatchers.IO) {
    if (feedId.startsWith("rss:")) {
        return@withContext getRssEpisodeWindow(feedId)
    }
    if (isLocalCatalogReady(feedId)) {
        return@withContext getLocalCatalogWindow(feedId, sort = "newest", aroundEpisodeId = null)
    }
    getAllNetworkEpisodes(feedId)
}

internal suspend fun PodcastRepository.getRssEpisodeWindow(feedId: String, aroundEpisodeId: String? = null,): List<Episode> = try {
    rssRepository.getEpisodesAround(feedId, LOCAL_CATALOG_WINDOW_BOUND, aroundEpisodeId)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "RSS episode window failed for $feedId", e)
    emptyList()
}

internal suspend fun PodcastRepository.getNetworkEpisodeWindow(feedId: String): List<Episode> = try {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    val response =
        api.getEpisodesPaginated(publicKey, resolvedId, limit = LOCAL_CATALOG_WINDOW_BOUND).execute()
    val piItems =
        if (response.isSuccessful && response.body() != null) {
            response.body()!!.items.mapNotNull { mapToEpisode(it) }
        } else {
            emptyList()
        }
    mergeCachedSupplementsNewest(resolvedId, piItems).take(LOCAL_CATALOG_WINDOW_BOUND)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

internal suspend fun PodcastRepository.getAllRssEpisodes(feedId: String): List<Episode> = try {
    rssRepository.getAllEpisodes(feedId)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "RSS getAllEpisodes failed for $feedId", e)
    emptyList()
}

internal suspend fun PodcastRepository.getAllNetworkEpisodes(feedId: String): List<Episode> = try {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    // Use paginated endpoint with safe limit
    // This avoids the parsing issue with EpisodesResponse vs EpisodesPaginatedResponse
    val response = api.getEpisodesPaginated(publicKey, resolvedId, limit = MAX_SAFE_PAGE_LIMIT).execute()
    val piItems =
        if (response.isSuccessful && response.body() != null) {
            response.body()!!.items.mapNotNull { mapToEpisode(it) }
        } else {
            emptyList()
        }
    mergeCachedSupplementsNewest(resolvedId, piItems)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

internal suspend fun PodcastRepository.getEpisodeImpl(episodeId: String): Episode? = withContext(Dispatchers.IO) {
    getLocalCatalogEpisode(episodeId)?.let { return@withContext it }
    if (episodeId.toLongOrNull()?.let { it < 0L } == true) {
        return@withContext getSupplementEpisode(episodeId) ?: getRssEpisode(episodeId)
    }
    getNetworkEpisode(episodeId)
}

internal suspend fun PodcastRepository.getRssEpisode(episodeId: String): Episode? = try {
    rssRepository.getEpisode(episodeId)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "RSS getEpisode failed for $episodeId", e)
    null
}

internal suspend fun PodcastRepository.getSupplementEpisode(episodeId: String): Episode? = try {
    episodeSupplementRepository?.getEpisode(episodeId)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "Supplement getEpisode failed for $episodeId", e)
    null
}

internal suspend fun PodcastRepository.getNetworkEpisode(episodeId: String): Episode? = try {
    val response = api.getEpisode(publicKey, episodeId).execute()
    if (response.isSuccessful && response.body() != null) {
        response.body()!!.episode?.let { mapToEpisode(it) }
    } else {
        null
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** Resolves url:/guid:/itunes: identifiers to a Podcast Index feed id when needed. */
internal suspend fun PodcastRepository.resolvePodcastIndexFeedId(feedId: String): String = if (
    feedId.startsWith(PodcastRepository.FEED_PREFIX_URL) ||
    feedId.startsWith(PodcastRepository.FEED_PREFIX_GUID) ||
    feedId.startsWith(PodcastRepository.FEED_PREFIX_ITUNES)
) {
    getPodcastDetails(feedId)?.id ?: feedId
} else {
    feedId
}
