package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodesPaging
import cx.aswin.boxlore.core.rss.EpisodeSupplementListMerge
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.awaitResponse

/**
 * Rows asked for per `/v2/episodes` request while a whole list is read: every page of in-show
 * search and of the whole-show read behind Select all and Mark all, and every page after the first
 * in the continuation window's search for its episode. The most the route returns, so a long show
 * costs a few requests. These pages are never kept in the shared page cache.
 */
internal const val SHOW_LIST_SCAN_PAGE = V2EpisodesPaging.LIMIT_MAX

/** Continuation searches that run at once: one per lock, so two callers asking for one window share a read. */
private val windowScanLocks = Array(WINDOW_SCAN_LOCKS) { Mutex() }

private fun windowScanLock(key: String): Mutex = windowScanLocks[(key.hashCode() and Int.MAX_VALUE) % windowScanLocks.size]

/**
 * The continuation window around [anchorId] from the server's list of the show: `/v2/episodes` read
 * in [sort] until the episode turns up ([ShowEpisodeWindowSearch]), then the episode and up to
 * [LOCAL_CATALOG_WINDOW_BOUND] - 1 after it. The search starts near the row where the last episode
 * of the show was found in that sort ([PodcastRepository.windowScanHints], remembered for hours),
 * so the next episode of a long show costs one request however long the last one played. When the
 * episode isn't within a page or two of that row, the search starts again from the start of the
 * list. Empty when the list doesn't
 * hold it up to the route's last offset (kept, like a found window) or a page fails (not kept, so
 * the next playback start tries again), so continuation never jumps ahead. Two callers asking for
 * one window at once share a read. v1 `/episodes` only when `/v2` can't answer its first page, and
 * then only when its newest window holds the episode.
 *
 * Added for same-show continuation from an episode older than the newest few hundred.
 */
internal suspend fun PodcastRepository.serverListWindowAround(feedId: String, anchorId: String, sort: String): List<Episode> {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    PodcastRepository.rememberResolvedFeedId(feedId, resolvedId)
    val listSort = showListSort(sort)
    val cacheKey = "$resolvedId|v2around|$listSort|$anchorId"
    PodcastRepository.episodesCache[cacheKey]?.let { return it.episodes }
    return windowScanLock(cacheKey).withLock {
        PodcastRepository.episodesCache[cacheKey]?.let { return@withLock it.episodes }
        val search = WindowScan(resolvedId, anchorId, listSort)
        val found =
            hintedWindow(search) ?: run {
                // The first page is the show page's own (cached); without it, v1's newest window, not kept.
                val first =
                    v2EpisodesPage(resolvedId, LOCAL_CATALOG_WINDOW_BOUND, 0, listSort)
                        ?: return@withLock ShowEpisodeWindowSearch.inList(anchorId, getNetworkEpisodeWindow(feedId), LOCAL_CATALOG_WINDOW_BOUND)
                fullWindow(search, first) ?: return@withLock emptyList()
            }
        val window = withNewerExtras(search, found)
        PodcastRepository.episodesCache[cacheKey] = PodcastRepository.EpisodePage(window, hasMore = false, sourceCount = window.size)
        window
    }
}

/** One continuation search: the show, the episode looked for, the list's sort and the row it was found at. */
private class WindowScan(val resolvedId: String, val anchorId: String, val sort: String) {
    val hintKey = "$resolvedId|$sort"
    val newestFirst = sort == NEWEST_SORT

    /** The episode's row in the list once found; -1 before. */
    var anchorRow = -1
}

/**
 * Oldest first, a window cut short because the list ends after it, with the cached feed extras
 * published after its last episode (newer than anything the list holds), so catching up to the
 * newest episode the server lists still offers the feed's newer ones. Newest first, the extras are
 * already in the first page; a window the route's last offset cut short gets none, so the extras
 * never skip the rows past it.
 */
private suspend fun PodcastRepository.withNewerExtras(search: WindowScan, window: List<Episode>): List<Episode> {
    if (search.newestFirst || search.anchorRow < 0) return window
    // Full, or cut short by the route's last offset rather than the list's end.
    val shortAtListEnd = window.size < LOCAL_CATALOG_WINDOW_BOUND && search.anchorRow + window.size < V2EpisodesPaging.OFFSET_MAX + SHOW_LIST_SCAN_PAGE
    if (window.isEmpty() || !shortAtListEnd) return window
    val last = window.last().publishedDate
    val extras = loadCachedSupplements(search.resolvedId).filter { it.publishedDate > last }
    if (extras.isEmpty()) return window
    return EpisodeSupplementListMerge.merge(window, extras, EpisodeSupplementListMerge.Sort.OLDEST).take(LOCAL_CATALOG_WINDOW_BOUND)
}

/**
 * The window read from just before the row the last search of this show found ([WindowScan.hintKey]),
 * or null when there is no such row, the episode isn't in the two pages from there, or the read
 * there can't give a whole window; the caller then reads from the start. Only two pages, so an
 * episode far from that row (one picked on the show page) never reads the rest of the list first.
 */
private suspend fun PodcastRepository.hintedWindow(search: WindowScan): List<Episode>? {
    val hint = PodcastRepository.windowScanHints[search.hintKey] ?: return null
    val start = (hint - 2 * LOCAL_CATALOG_WINDOW_BOUND).coerceAtLeast(0)
    if (start == 0) return null
    val first = scanPageOrEnd(search, start) ?: return null
    var row = -1
    val window =
        ShowEpisodeWindowSearch.around(
            anchorId = search.anchorId,
            newestFirst = search.newestFirst,
            bound = LOCAL_CATALOG_WINDOW_BOUND,
            firstPage = first,
            startOffset = start,
            onFound = {
                row = it
                search.anchorRow = it
            },
        ) { offset ->
            // Not found in the two pages from the start: end here; once found, the next page may follow.
            if (row < 0 && offset > start + SHOW_LIST_SCAN_PAGE) END_OF_SCAN else scanPageOrEnd(search, offset)
        }
    // Newest first, the episodes after it are the rows before it: those above the start weren't read.
    val shortOfNewer = search.newestFirst && row - start < LOCAL_CATALOG_WINDOW_BOUND - 1
    if (window.isNullOrEmpty() || shortOfNewer) return null
    PodcastRepository.windowScanHints[search.hintKey] = row
    return window
}

/**
 * The window read from the start of the list, [first] being its first page: null when a page
 * fails, empty when the list doesn't hold the episode. Cached feed extras join the first page when
 * it holds the newest episodes (newest first, or the whole list); oldest first they follow the
 * list's end instead ([withNewerExtras]).
 */
private suspend fun PodcastRepository.fullWindow(search: WindowScan, first: PodcastRepository.EpisodePage): List<Episode>? {
    val withExtras = search.newestFirst || !first.hasMore
    val firstPage =
        ShowEpisodeWindowSearch.Page(
            episodes = if (withExtras) mergeCachedSupplementsIntoPage(search.resolvedId, first, 0, search.sort).episodes else first.episodes,
            hasMore = first.hasMore,
            sourceCount = first.sourceCount,
        )
    return ShowEpisodeWindowSearch.around(
        anchorId = search.anchorId,
        newestFirst = search.newestFirst,
        bound = LOCAL_CATALOG_WINDOW_BOUND,
        firstPage = firstPage,
        onFound = { row ->
            search.anchorRow = row
            PodcastRepository.windowScanHints[search.hintKey] = row
        },
    ) { offset -> scanPageOrEnd(search, offset) }
}

/**
 * [v2ScanPage] for the continuation search, which reads as far as the route serves: an offset just
 * past its last offset reads the page at that offset without the rows already read, and past that
 * page an empty last page ends the search as "not found", kept. A page that fails stays null.
 */
private suspend fun PodcastRepository.scanPageOrEnd(search: WindowScan, offset: Int): ShowEpisodeWindowSearch.Page? {
    if (offset >= V2EpisodesPaging.OFFSET_MAX + SHOW_LIST_SCAN_PAGE) return END_OF_SCAN
    val readAt = offset.coerceAtMost(V2EpisodesPaging.OFFSET_MAX)
    return v2ScanPage(search.resolvedId, readAt, search.sort, skipRows = offset - readAt)
}

/** An empty last page: the continuation search ends there as "not found". */
private val END_OF_SCAN = ShowEpisodeWindowSearch.Page(emptyList(), hasMore = false, sourceCount = 0)

/**
 * One [SHOW_LIST_SCAN_PAGE]-row `/v2/episodes` page under local owners, never cached, without its
 * first [skipRows] rows (already read); null when the route can't answer or [offset] is past its last.
 */
internal suspend fun PodcastRepository.v2ScanPage(resolvedId: String, offset: Int, sort: String, skipRows: Int = 0): ShowEpisodeWindowSearch.Page? {
    val showId = V2EpisodeIds.parseShowId(resolvedId)?.takeIf { V2EpisodesPaging.isValid(it, SHOW_LIST_SCAN_PAGE, offset) } ?: return null
    val body = v2Answer("episodes") { apiV2.getEpisodes(showId, SHOW_LIST_SCAN_PAGE, offset, sort).awaitResponse() } ?: return null
    val items = body.items.drop(skipRows)
    return ShowEpisodeWindowSearch.Page(
        episodes = discoveryEpisodeIds.toLocalOwners(items.mapNotNull(::mapToEpisode)),
        hasMore = body.hasMore,
        sourceCount = items.size,
    )
}

/**
 * One page of the whole-show read ([PodcastRepository.getWholeShowPage]) from the server's list:
 * [SHOW_LIST_SCAN_PAGE] rows of `/v2/episodes` under local owners, never cached, with cached feed
 * extras on the first page. Throws [IOException] when the page can't be read, and
 * [ShowListTooLongException] when the list runs past the route's last offset, so the read never
 * stops early or mixes in v1 pages. A show `/v2`
 * doesn't list at all reads v1 `/episodes` for the whole read, with the same rule.
 */
internal suspend fun PodcastRepository.wholeShowServerPage(
    feedId: String,
    offset: Int,
    sort: String,
    list: ShowListSchemes = serverListSchemes,
): PodcastRepository.EpisodePage {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    PodcastRepository.rememberResolvedFeedId(feedId, resolvedId)
    val listSort = showListSort(sort)
    val listOnV1 = list.keptFor(resolvedId, showPage = true, offset = offset, strict = true) == ServerListScheme.V1
    if (listOnV1 || V2EpisodeIds.parseShowId(resolvedId)?.let(V2EpisodeIds::isEpisodeShowId) != true) {
        return getNetworkEpisodesPaginated(feedId, MAX_SAFE_PAGE_LIMIT, offset, listSort, mergeSupplements = true, strict = true)
    }
    if (offset > V2EpisodesPaging.OFFSET_MAX) throw ShowListTooLongException()
    val page = v2ScanPage(resolvedId, offset, listSort) ?: throw IOException("/v2/episodes failed at offset $offset")
    return maybeMergeSupplements(
        resolvedId,
        PodcastRepository.EpisodePage(page.episodes, page.hasMore, page.sourceCount),
        offset,
        listSort,
        mergeSupplements = true,
    )
}

private const val WINDOW_SCAN_LOCKS = 8
