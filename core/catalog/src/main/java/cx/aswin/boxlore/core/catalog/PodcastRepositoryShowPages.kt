package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodesPaging
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import retrofit2.awaitResponse

/**
 * What [PodcastRepository]'s feed-first show page needs from the app: the stored copies
 * ([ShowPageFeedPort]; null stores nothing), whether the phone is online, the app's one broken-feed
 * report limiter (shared with the daily address check, [FeedAddressUpkeep.reportLimiter]),
 * `feed_fetch_result` reporting, and whether the listener follows a show ([isFollowed]: followed
 * shows are reported by their own refresh, not by the show page).
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
data class FeedFirstWiring(
    val copies: ShowPageFeedPort? = null,
    val isOnline: () -> Boolean = { true },
    val reportLimiter: FeedReportLimiter = FeedReportLimiter(MemoryFeedReportStamps()),
    val telemetry: FeedTelemetryPort = FeedTelemetryPort.NONE,
    val isFollowed: suspend (String) -> Boolean = { false },
)

/** In-show search answers at most this many episodes. */
internal const val MAX_SHOW_SEARCH_RESULTS = 200

internal const val SHOW_PAGES_TAG = "PodcastRepository"
internal const val OLDEST_SORT = "oldest"
internal const val NEWEST_SORT = "newest"

/** The `/v2/episodes` sort for a caller's sort: oldest first only when asked, else newest first. */
internal fun showListSort(sort: String): String = if (sort == OLDEST_SORT) OLDEST_SORT else NEWEST_SORT

/**
 * True when [feedId]'s show page reads its feed first: `feed_first` is on and [feedId] is a Podcast
 * Index show ID (not `rss:`, nor a `url:`, `guid:` or `itunes:` lookup that hasn't resolved yet).
 *
 * LEGACY(own-ids): the staged feed-first rollout. Remove the switch at sunset step S7 (MIGRATION.md §6)
 * once `feed_first` has been at 100% for 30 days; the show-ID check stays.
 */
internal fun PodcastRepository.isFeedFirstShow(feedId: String): Boolean = V2EpisodeIds.parseShowId(feedId) != null && rolloutSwitches.feedFirst()

/**
 * A page of [feedId]'s stored feed copy, or null when the phone has none yet, or when an oldest-first
 * page is asked of a copy that holds only the newest items (its oldest rows aren't the show's first
 * episodes). More pages follow while the copy has more rows, and while it is not the whole archive:
 * the show page then reads the rest of the feed before it pages on.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 */
internal suspend fun PodcastRepository.feedCopyPage(feedId: String, limit: Int, offset: Int, sort: String,): PodcastRepository.EpisodePage? {
    val cached = feedFirst.cached(feedId) ?: return null
    if (sort == OLDEST_SORT && !cached.archiveComplete) return null
    val catalog = localEpisodeCatalog ?: return null
    val episodes = catalog.getPage(feedId, limit, offset, sort, feedFirst.metaFor(feedId) ?: localCatalogMeta(feedId))
    return PodcastRepository.EpisodePage(
        episodes = episodes,
        hasMore = offset + episodes.size < cached.itemCount || !cached.archiveComplete,
        sourceCount = episodes.size,
    )
}

/**
 * Bounded search over [feedId]'s stored feed copy ([MAX_SHOW_SEARCH_RESULTS] rows, limited in the
 * query); null when the phone has none.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, in-show search on the phone).
 */
internal suspend fun PodcastRepository.searchFeedCopy(feedId: String, query: String,): List<Episode>? =
    feedFirst.search(feedId, query, feedFirst.metaFor(feedId) ?: localCatalogMeta(feedId), MAX_SHOW_SEARCH_RESULTS)

/**
 * The bounded episode window (Smart Queue, same-show continuation, Android Auto) of a Podcast Index
 * show whose catalog isn't ready, under the same IDs its show page lists:
 * - around an episode the feed-first copy holds ([aroundEpisodeId]), the copy's window, so playback
 *   that started on a feed-first page continues under the copy's IDs;
 * - around any other episode, that episode and the ones after it, found by reading `/v2/episodes`
 *   in [sort] until it turns up ([serverListWindowAround]); empty when the list doesn't hold it, so
 *   continuation offers nothing rather than an episode far from the listener's place;
 * - without an episode, the first [LOCAL_CATALOG_WINDOW_BOUND] of `/v2/episodes` in [sort], mapped
 *   to the IDs this phone stores, as the show page's server list is;
 * - v1 `/episodes` only when `/v2/episodes` fails.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page; decision D1).
 */
internal suspend fun PodcastRepository.getShowEpisodeWindow(feedId: String, aroundEpisodeId: String?, sort: String,): List<Episode> {
    if (aroundEpisodeId != null && isFeedFirstShow(feedId) && feedFirst.cached(feedId) != null) {
        val catalog = localEpisodeCatalog
        val window = catalog?.getWindow(feedId, sort, LOCAL_CATALOG_WINDOW_BOUND, aroundEpisodeId, feedFirst.metaFor(feedId) ?: localCatalogMeta(feedId))
        if (window != null && window.any { it.id == aroundEpisodeId }) return window
    }
    if (aroundEpisodeId != null) return serverListWindowAround(feedId, aroundEpisodeId, sort)
    return serverListWindow(feedId, sort) ?: getNetworkEpisodeWindow(feedId)
}

/**
 * The newest [LOCAL_CATALOG_WINDOW_BOUND] episodes of the show for callers that list a whole show
 * ([PodcastRepository.getEpisodes]): `/v2/episodes` mapped to the IDs this phone stores, then v1.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11; decision D1).
 */
internal suspend fun PodcastRepository.getShowEpisodesNewest(feedId: String): List<Episode> = serverListWindow(feedId, NEWEST_SORT) ?: getAllNetworkEpisodes(feedId)

/** The first [LOCAL_CATALOG_WINDOW_BOUND] of `/v2/episodes` in [sort] under local owners, with cached feed extras; null when `/v2` can't answer. */
private suspend fun PodcastRepository.serverListWindow(feedId: String, sort: String): List<Episode>? {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    PodcastRepository.rememberResolvedFeedId(feedId, resolvedId)
    val listSort = showListSort(sort)
    val page = v2EpisodesPage(resolvedId, LOCAL_CATALOG_WINDOW_BOUND, 0, listSort) ?: return null
    return mergeCachedSupplementsIntoPage(resolvedId, page, 0, listSort).episodes.take(LOCAL_CATALOG_WINDOW_BOUND)
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
internal suspend fun PodcastRepository.v2EpisodesPage(resolvedId: String, limit: Int, offset: Int, sort: String,): PodcastRepository.EpisodePage? {
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
 * In-show search over the server's list of the show, on the phone: every episode `/v2/episodes`
 * lists, read [SHOW_LIST_SCAN_PAGE] at a time up to the route's last offset ([serverSearchScan]),
 * matched by title or notes, under local owners, with matching cached feed extras. Says how many
 * of the newest were searched when the list runs past that offset. Null when the list can't be
 * read, so a failed search never reads as "no matches"; never v1 `episodes/search`.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (decision D16: search with `feed_first` off).
 * COMPAT(own-ids): no served IDs mix with recipe IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal suspend fun PodcastRepository.searchServerList(feedId: String, query: String,): ShowEpisodeSearch? {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return ShowEpisodeSearch(emptyList())
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    val scan = serverSearchScan(resolvedId, trimmed) ?: return null
    val matches = scan.matches(trimmed, MAX_SHOW_SEARCH_RESULTS)
    val older = slimMatchEpisodes(resolvedId, matches.slim) ?: return null
    return ShowEpisodeSearch(
        episodes = unionCachedSupplementSearch(resolvedId, trimmed, matches.full + older),
        searchedNewest = scan.rowCount.takeIf { scan.cutShort },
    )
}

/** One read of a show's list for search at a time, so a search that replaces another carries on from its pages. */
private val searchScanLock = Mutex()

/**
 * The server's list of the show for search, newest first, read until its end or the route's last
 * offset, or until it holds [MAX_SHOW_SEARCH_RESULTS] matches of [query]: matches are newest first,
 * so older pages can't change them, and a later query carries on reading from there. Every page
 * read is kept at once in its own small cache ([PodcastRepository.showSearchCache], never the
 * shared page cache), so a search cancelled by the next keystroke, or stopped by a failed page,
 * carries on from there. An older page's rows that match [query] are kept whole as it is read
 * ([rememberSearchMatches]), so this search reads no page twice. Null when a page fails or the
 * show isn't a show ID `/v2` lists.
 */
private suspend fun PodcastRepository.serverSearchScan(resolvedId: String, query: String): ShowSearchScan? {
    V2EpisodeIds.parseShowId(resolvedId)?.takeIf(V2EpisodeIds::isEpisodeShowId) ?: return null
    return searchScanLock.withLock {
        var scan = PodcastRepository.showSearchCache[resolvedId] ?: ShowSearchScan()
        while (!scan.done && !scan.answers(query, MAX_SHOW_SEARCH_RESULTS)) {
            val offset = scan.nextOffset
            val page = v2ScanPage(resolvedId, offset, NEWEST_SORT) ?: return@withLock null
            scan = scan.withPage(offset, page)
            PodcastRepository.showSearchCache[resolvedId] = scan
            val matchedHere = scan.matches(query, MAX_SHOW_SEARCH_RESULTS).slim.filter { it.pageOffset == offset }.mapTo(HashSet()) { it.id }
            rememberSearchMatches(resolvedId, page.episodes.filter { it.id in matchedHere })
        }
        scan
    }
}

/**
 * The full episodes of slim search matches, newest first: those an earlier search read
 * ([PodcastRepository.showSearchMatches]) at once, and each page holding one not yet read read
 * again. Every page's matches are kept as soon as it arrives, so a search the next keystroke
 * cancels keeps them, and typing on (whose matches are a few of these) reads nothing. A match the
 * list no longer holds at that page (newer episodes moved it on) is left out. Null when a page
 * can't be read.
 */
private suspend fun PodcastRepository.slimMatchEpisodes(resolvedId: String, rows: List<ShowSearchScan.SlimRow>): List<Episode>? {
    if (rows.isEmpty()) return emptyList()
    val byId = HashMap(PodcastRepository.showSearchMatches[resolvedId].orEmpty())
    val wanted = rows.mapTo(HashSet()) { it.id }
    for (offset in rows.filterNot { it.id in byId }.map { it.pageOffset }.distinct()) {
        val page = v2ScanPage(resolvedId, offset, NEWEST_SORT) ?: return null
        val found = page.episodes.filter { it.id in wanted && it.id !in byId }
        found.forEach { byId[it.id] = it }
        rememberSearchMatches(resolvedId, found)
    }
    return rows.mapNotNull { byId[it.id] }.also { rememberSearchMatches(resolvedId, it) }
}

/** The show's registry entry (`/v2/podcast`): its address, transport and namespace. Null when it can't be read. */
internal suspend fun PodcastRepository.v2ShowAddress(showId: Long): V2PodcastResponse? = v2Answer("podcast") { apiV2.getPodcast(showId).awaitResponse() }

/** The body of a successful `/v2` answer, or null; cancellation propagates. */
internal inline fun <T> v2Answer(route: String, call: () -> Response<T>): T? = try {
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
