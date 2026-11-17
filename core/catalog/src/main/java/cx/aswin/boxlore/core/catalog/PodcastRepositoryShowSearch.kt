package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/*
 * Show search and chart pages that tell a failed read from an empty answer: null means the request
 * failed (an HTTP error such as 429 or 5xx, no body, or a network error; a failed `/v2` chart page
 * returns its last good copy first), so a caller never shows the failure as "no shows found" or as
 * the end of the chart. Cancellation propagates.
 */

private inline fun <T, R> bodyOrNull(call: () -> Response<T>, map: (T) -> R,): R? = try {
    val response = call()
    val body = response.body()
    if (response.isSuccessful && body != null) map(body) else null
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

/** v1 `/search` (catalog, Apple and Podcast Index results), or null when it failed. */
internal fun PodcastRepository.hybridShowSearchOrNull(query: String): List<Podcast>? =
    bodyOrNull({ api.search(publicKey, query).execute() }) { body -> body.feeds.mapNotNull(::mapSearchFeedToPodcast) }

/** Meili typeahead, or null when it failed. */
internal fun PodcastRepository.typeaheadShowSearchOrNull(query: String, limit: Int = 20,): List<Podcast>? =
    bodyOrNull({ api.searchTypeahead(publicKey, query, limit).execute() }) { body -> body.feeds.mapNotNull(::mapSearchFeedToPodcast) }

/**
 * One chart page on `/v2` (or v1 while the discovery switch is off), or null when it failed; on `/v2`
 * a failed page returns its last good copy when one exists.
 */
internal suspend fun PodcastRepository.trendingPodcastsOrNull(country: String, limit: Int, category: String?, offset: Int,): List<Podcast>? {
    if (usesV2Discovery()) return v2TrendingPodcasts(country, limit, category, offset)
    return bodyOrNull({ api.getTrending(publicKey, country, limit, category, offset).execute() }) { body -> mapFeedsToPodcasts(body.feeds) }
}
