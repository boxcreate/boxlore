package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodeReply
import cx.aswin.boxlore.core.network.model.v2Identity
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
    val page1Response =
        api.getEpisodesPaginated(publicKey, resolvedId, limit = MAX_SAFE_PAGE_LIMIT, offset = 0).execute()
    val page1Body = if (page1Response.isSuccessful) page1Response.body() else null
    val page1Items = page1Body?.items.orEmpty().mapNotNull { mapToEpisode(it) }

    val piItems =
        if (page1Body?.hasMore == true && page1Items.size < LOCAL_CATALOG_WINDOW_BOUND) {
            val page2Response =
                api.getEpisodesPaginated(
                    publicKey,
                    resolvedId,
                    limit = MAX_SAFE_PAGE_LIMIT,
                    offset = page1Items.size,
                ).execute()
            val page2Body = if (page2Response.isSuccessful) page2Response.body() else null
            val page2Items = page2Body?.items.orEmpty().mapNotNull { mapToEpisode(it) }
            (page1Items + page2Items).take(LOCAL_CATALOG_WINDOW_BOUND)
        } else {
            page1Items
        }
    mergeCachedSupplementsNewest(resolvedId, piItems)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

/**
 * One episode by its stored ID: the local catalog first, then phone-only stores for negative IDs,
 * then the network. A recipe-range ID (`[2^52, 2^53 − 1]`: a recipe ID, or a served ID older builds
 * were given) goes to `/v2/episode` ([getV2Episode]) and never to the Podcast Index lookup; other
 * positive IDs are Podcast Index IDs and keep v1 `/episode`.
 *
 * Changed in P4 PR 9 (own-IDs plan section 9: recipe misses never go to Podcast Index).
 */
internal suspend fun PodcastRepository.getEpisodeImpl(episodeId: String, showId: String?,): Episode? = withContext(Dispatchers.IO) {
    getLocalCatalogEpisode(episodeId)?.let { return@withContext it }
    val numeric = episodeId.toLongOrNull()
    if (numeric != null && numeric < 0L) {
        return@withContext getSupplementEpisode(episodeId) ?: getRssEpisode(episodeId)
    }
    if (numeric != null && EpisodeRecipeId.isRecipeId(numeric)) {
        return@withContext getV2Episode(episodeId, showId)
    }
    getNetworkEpisode(episodeId)
}

/**
 * A recipe-range ID looked up on `/v2/episode?id=&show=` ([showId] only when it is a show the route
 * accepts). The episode keeps the ID the caller asked for, with its recipe ID and catalog key
 * attached, so a served ID is never swapped for the recipe ID. A `needsFeed` answer (an episode only
 * the show's feed lists) is not found for now, like a 404 or an error.
 *
 * Added in P4 PR 9 (own-IDs plan section 9, `/v2/episode`).
 * COMPAT(own-ids): recipe-range IDs resolve through the v2 route. Permanent: never remove (MIGRATION.md §5).
 */
internal suspend fun PodcastRepository.getV2Episode(episodeId: String, showId: String?,): Episode? {
    if (!V2EpisodeIds.isLookupEpisodeId(episodeId)) return null
    val show = V2EpisodeIds.parseShowId(showId)?.takeIf(V2EpisodeIds::isEpisodeShowId)
    return try {
        val response = apiV2.getEpisode(episodeId, show).execute()
        when (val reply = response.body()?.takeIf { response.isSuccessful }) {
            is V2EpisodeReply.Found -> {
                val identity = reply.episode.v2Identity()
                mapToEpisode(reply.episode)?.copy(id = episodeId, recipeId = identity?.recipeId, catalogKey = identity?.catalogKey)
            }
            is V2EpisodeReply.NeedsFeed, null -> null
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("PodcastRepository", "v2 episode lookup failed for $episodeId", e)
        null
    }
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
