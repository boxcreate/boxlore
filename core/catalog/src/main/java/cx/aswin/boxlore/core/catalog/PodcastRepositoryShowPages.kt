package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodesPaging
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import java.util.Locale
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import retrofit2.awaitResponse

/**
 * What [PodcastRepository]'s feed-first show page needs from the app: the stored copies
 * ([ShowPageFeedPort]; null stores nothing), whether the phone is online, the broken-feed report
 * limits shared with the daily address check, and `feed_fetch_result` reporting.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
data class FeedFirstWiring(
    val copies: ShowPageFeedPort? = null,
    val isOnline: () -> Boolean = { true },
    val reportStamps: FeedReportStamps = MemoryFeedReportStamps(),
    val telemetry: FeedTelemetryPort = FeedTelemetryPort.NONE,
)

/** In-show search answers at most this many episodes. */
internal const val MAX_SHOW_SEARCH_RESULTS = 200

private const val SHOW_PAGES_TAG = "PodcastRepository"

/**
 * True when [feedId]'s show page reads its feed first: `feed_first` is on and [feedId] is a Podcast
 * Index show ID (not `rss:`, nor a `url:`, `guid:` or `itunes:` lookup that hasn't resolved yet).
 *
 * LEGACY(own-ids): the staged feed-first rollout. Remove the switch at sunset step S7 (MIGRATION.md §6)
 * once `feed_first` has been at 100% for 30 days; the show-ID check stays.
 */
internal fun PodcastRepository.isFeedFirstShow(feedId: String): Boolean = V2EpisodeIds.parseShowId(feedId) != null && rolloutSwitches.feedFirst()

/**
 * A page of [feedId]'s stored feed copy, or null when the phone has none yet. More pages follow
 * while the copy has more rows, and while it is not the whole archive: the show page then reads
 * the rest of the feed before it pages on.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
internal suspend fun PodcastRepository.feedCopyPage(feedId: String, limit: Int, offset: Int, sort: String,): PodcastRepository.EpisodePage? {
    val cached = feedFirst.cached(feedId) ?: return null
    val catalog = localEpisodeCatalog ?: return null
    val episodes = catalog.getPage(feedId, limit, offset, sort, feedFirst.metaFor(feedId) ?: localCatalogMeta(feedId))
    return PodcastRepository.EpisodePage(
        episodes = episodes,
        hasMore = offset + episodes.size < cached.itemCount || !cached.archiveComplete,
        sourceCount = episodes.size,
    )
}

/**
 * Bounded search over [feedId]'s stored feed copy; null when the phone has none.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, in-show search on the phone).
 */
internal suspend fun PodcastRepository.searchFeedCopy(feedId: String, query: String,): List<Episode>? {
    feedFirst.cached(feedId) ?: return null
    val catalog = localEpisodeCatalog ?: return null
    return catalog.search(feedId, query, feedFirst.metaFor(feedId) ?: localCatalogMeta(feedId)).take(MAX_SHOW_SEARCH_RESULTS)
}

/**
 * A page of the server's list of the show (`/v2/episodes`), its recipe IDs mapped to the stored IDs
 * that own them on this phone, merged with cached feed extras; v1 `/episodes` only when `/v2` fails.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, rollback: `feed_first` off reads
 * `/v2/episodes`; decision D1).
 * COMPAT(own-ids): the server's list in recipe IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal suspend fun PodcastRepository.getServerListPage(
    feedId: String,
    limit: Int,
    offset: Int,
    sort: String,
    mergeSupplements: Boolean,
): PodcastRepository.EpisodePage {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    PodcastRepository.rememberResolvedFeedId(feedId, resolvedId)
    val page = v2EpisodesPage(resolvedId, limit, offset, sort) ?: return getNetworkEpisodesPaginated(feedId, limit, offset, sort, mergeSupplements)
    return maybeMergeSupplements(resolvedId, page, offset, sort, mergeSupplements)
}

/** One `/v2/episodes` page under local owners, cached with the v1 pages; null when the route can't answer. */
private suspend fun PodcastRepository.v2EpisodesPage(resolvedId: String, limit: Int, offset: Int, sort: String,): PodcastRepository.EpisodePage? {
    val showId = V2EpisodeIds.parseShowId(resolvedId)?.takeIf { V2EpisodesPaging.isValid(it, limit, offset) } ?: return null
    val cacheKey = "$resolvedId|v2|$limit|$offset|$sort"
    PodcastRepository.episodesCache[cacheKey]?.let { return it }
    val body = v2Answer("episodes") { apiV2.getEpisodes(showId, limit, offset, sort).awaitResponse() } ?: return null
    return PodcastRepository
        .EpisodePage(
            episodes = discoveryEpisodeIds.toLocalOwners(body.items.mapNotNull(::mapToEpisode)),
            hasMore = body.hasMore,
            sourceCount = body.items.size,
        ).also { PodcastRepository.episodesCache[cacheKey] = it }
}

/**
 * In-show search over the server's list of the show, on the phone: the newest
 * [V2EpisodesPaging.LIMIT_MAX] episodes of `/v2/episodes`, matched by title or description, under
 * local owners, with matching cached feed extras. Empty when `/v2` fails: never v1 `episodes/search`.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (decision D16: search with `feed_first` off).
 * COMPAT(own-ids): no served IDs mix with recipe IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal suspend fun PodcastRepository.searchServerList(feedId: String, query: String,): List<Episode> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    val list = serverSearchList(resolvedId)
    val matches = list.filter { it.matches(trimmed) }.take(MAX_SHOW_SEARCH_RESULTS)
    return unionCachedSupplementSearch(resolvedId, trimmed, matches)
}

private suspend fun PodcastRepository.serverSearchList(resolvedId: String): List<Episode> {
    val showId = V2EpisodeIds.parseShowId(resolvedId)?.takeIf(V2EpisodeIds::isEpisodeShowId) ?: return emptyList()
    val cacheKey = "$resolvedId|v2search"
    PodcastRepository.episodesCache[cacheKey]?.let { return it.episodes }
    val body =
        v2Answer("episodes search") {
            apiV2.getEpisodes(showId, V2EpisodesPaging.LIMIT_MAX, 0, cx.aswin.boxlore.core.network.model.V2EpisodeSort.NEWEST).awaitResponse()
        } ?: return emptyList()
    val episodes = discoveryEpisodeIds.toLocalOwners(body.items.mapNotNull(::mapToEpisode))
    PodcastRepository.episodesCache[cacheKey] = PodcastRepository.EpisodePage(episodes, body.hasMore, body.items.size)
    return episodes
}

private fun Episode.matches(query: String): Boolean {
    val needle = query.lowercase(Locale.ROOT)
    return title.lowercase(Locale.ROOT).contains(needle) || description.lowercase(Locale.ROOT).contains(needle)
}

/** The show's registry entry (`/v2/podcast`): its address, transport and namespace. Null when it can't be read. */
internal suspend fun PodcastRepository.v2ShowAddress(showId: Long): V2PodcastResponse? = v2Answer("podcast") { apiV2.getPodcast(showId).awaitResponse() }

/**
 * An http-only show's feed document through boxlore's transport (`/v2/feed/<id>`), read whole.
 * Null when the route doesn't answer with a document.
 */
internal suspend fun PodcastRepository.v2TransportDocument(showId: Long): ShowPageFeedPort.Document? {
    val response = apiV2.getFeed(showId).awaitResponse()
    val body = response.body()?.takeIf { response.isSuccessful } ?: return null
    val bytes = body.use { it.bytes() }
    return ShowPageFeedPort.Document(bytes, response.raw().request.url.toString(), response.headers()["ETag"], complete = true)
}

/** The body of a successful `/v2` answer, or null; cancellation propagates. */
private inline fun <T> v2Answer(route: String, call: () -> Response<T>): T? = try {
    val response = call()
    response.body().takeIf { response.isSuccessful }.also {
        if (it == null) android.util.Log.w(SHOW_PAGES_TAG, "v2 $route failed: HTTP ${response.code()}")
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    android.util.Log.w(SHOW_PAGES_TAG, "v2 $route failed", error)
    null
}
