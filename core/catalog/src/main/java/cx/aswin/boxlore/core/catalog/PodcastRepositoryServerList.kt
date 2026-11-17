package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodesPaging
import retrofit2.awaitResponse

/**
 * A page of the server's list of the show (`/v2/episodes`), its recipe IDs mapped to the stored IDs
 * that own them on this phone, merged with cached feed extras; v1 `/episodes` only when `/v2` fails
 * for the first page. Each page says which route it came from ([PodcastRepository.EpisodePage.scheme]).
 *
 * One list never mixes the two ID schemes. A caller paging a list passes its first page's [scheme]
 * back for the later pages, which then read only that route: a page it can't read comes back
 * [PodcastRepository.EpisodePage.failed] (or throws when [strict]) instead of switching routes or
 * ending the list. The show page ([showPage]) also keeps the scheme of its list's first page for
 * the show in its own [list]; its later pages and its whole-show read follow it, and only its own
 * first pages change it, so a first page another screen (or another show page for the same show)
 * reads, and that falls back to v1, can't switch the list on screen. Past the route's last offset
 * the list ends.
 *
 * [strict] is range selection's whole-show read: it follows the scheme of the list on screen even
 * for its first page, waits for room in [PodcastRepository.routeBudget] before each request, waits
 * out a 429 once, and throws for any page it can't read.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, rollback: `feed_first` off reads
 * `/v2/episodes`; decision D1).
 * COMPAT(own-ids): the server's list in recipe IDs. Permanent: never remove (MIGRATION.md §5).
 */
@Suppress("LongParameterList")
internal suspend fun PodcastRepository.getServerListPage(
    feedId: String,
    limit: Int,
    offset: Int,
    sort: String,
    mergeSupplements: Boolean,
    strict: Boolean = false,
    showPage: Boolean = false,
    scheme: ServerListScheme? = null,
    list: ShowListSchemes = serverListSchemes,
): PodcastRepository.EpisodePage {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    PodcastRepository.rememberResolvedFeedId(feedId, resolvedId)
    val showId = V2EpisodeIds.parseShowId(resolvedId)?.takeIf(V2EpisodeIds::isEpisodeShowId)
    val kept = scheme ?: list.keptFor(resolvedId, showPage, offset, strict)
    val startsShowList = startsShowList(showPage, scheme, offset, strict)
    if (kept == ServerListScheme.V2 && showId == null) return serverListFailure(offset, strict)
    if (showId != null && kept != ServerListScheme.V1) {
        val read = v2EpisodesPageRead(resolvedId, showId, limit, offset, sort, paced = strict)
        // Only a first page that no list has fixed may fall back to v1.
        if (read != V2PageRead.Failed || offset > 0 || kept != null) {
            return v2ListPage(read, resolvedId, offset, sort, mergeSupplements, strict).also { page ->
                if (startsShowList) list.keep(resolvedId, page)
            }
        }
    }
    return v1ListPage(feedId, limit, offset, sort, mergeSupplements, strict).also { page -> if (startsShowList) list.keep(resolvedId, page) }
}

/** Only the show page's own first page, not tied to a list yet, sets the scheme its list keeps. */
private fun startsShowList(showPage: Boolean, scheme: ServerListScheme?, offset: Int, strict: Boolean): Boolean = showPage && scheme == null && offset == 0 && !strict

/** A v1 `/episodes` page of the list ([ServerListScheme.V1]); failed (thrown when [strict]) when it can't be read. */
@Suppress("LongParameterList")
private suspend fun PodcastRepository.v1ListPage(
    feedId: String,
    limit: Int,
    offset: Int,
    sort: String,
    mergeSupplements: Boolean,
    strict: Boolean,
): PodcastRepository.EpisodePage {
    val page = getNetworkEpisodesPaginated(feedId, limit, offset, sort, mergeSupplements)
    if (page.failed) return if (strict) throw java.io.IOException("v1 episodes page at $offset failed") else page
    return page.copy(scheme = ServerListScheme.V1)
}

/** A page a list fixed to one route can't be read from: failed, or thrown when [strict]. */
private fun serverListFailure(offset: Int, strict: Boolean): PodcastRepository.EpisodePage {
    if (strict) throw java.io.IOException("Episodes page at $offset can't be read from the list's route")
    return PodcastRepository.EpisodePage(emptyList(), hasMore = false, sourceCount = 0, failed = true)
}

/** The page a `/v2/episodes` [read] gives the list: the page, the list's end past the route's last offset, or a failure (thrown when [strict]). */
@Suppress("LongParameterList")
private suspend fun PodcastRepository.v2ListPage(
    read: V2PageRead,
    resolvedId: String,
    offset: Int,
    sort: String,
    mergeSupplements: Boolean,
    strict: Boolean,
): PodcastRepository.EpisodePage = when (read) {
    is V2PageRead.Page -> maybeMergeSupplements(resolvedId, read.page, offset, sort, mergeSupplements).copy(scheme = ServerListScheme.V2)
    V2PageRead.PastEnd -> {
        if (strict) throw java.io.IOException("Offset $offset is past the server list")
        PodcastRepository.EpisodePage(emptyList(), hasMore = false, sourceCount = 0, scheme = ServerListScheme.V2)
    }
    V2PageRead.Failed -> serverListFailure(offset, strict)
}

/**
 * Which route a list of the server's was read from, so its later pages read the same one: `/v2`
 * pages carry the stored IDs that own each episode on this phone, v1 pages the served IDs.
 */
enum class ServerListScheme { V1, V2 }

/**
 * The scheme one show page's list keeps per show (resolved ID), set only by that page's own first
 * pages ([getServerListPage]). Each show page holds its own, so a second page open for the same show
 * (further back on the back stack) can't switch this one's list to the other route.
 */
class ShowListSchemes {
    private val schemes = java.util.concurrent.ConcurrentHashMap<String, ServerListScheme>()

    /** The route a show page's later page or whole-show read follows; none for other callers' reads or a first page. */
    internal fun keptFor(resolvedId: String, showPage: Boolean, offset: Int, strict: Boolean): ServerListScheme? =
        schemes[resolvedId].takeIf { showPage && (offset > 0 || strict) }

    /** Keeps the route of [page], a list's first page that was read; a failed one changes nothing. */
    internal fun keep(resolvedId: String, page: PodcastRepository.EpisodePage) {
        if (!page.failed) page.scheme?.let { schemes[resolvedId] = it }
    }

    internal fun clear() = schemes.clear()
}

/** The [ShowListSchemes] of show-page reads that bring none of their own. */
internal val serverListSchemes = ShowListSchemes()

internal sealed interface V2PageRead {
    data class Page(val page: PodcastRepository.EpisodePage) : V2PageRead

    /** The offset is past what the route serves. */
    data object PastEnd : V2PageRead

    data object Failed : V2PageRead
}

@Suppress("LongParameterList")
internal suspend fun PodcastRepository.v2EpisodesPageRead(resolvedId: String, showId: Long, limit: Int, offset: Int, sort: String, paced: Boolean,): V2PageRead {
    if (!V2EpisodesPaging.isValid(showId, limit, offset)) return if (offset > V2EpisodesPaging.OFFSET_MAX) V2PageRead.PastEnd else V2PageRead.Failed
    val cacheKey = "$resolvedId|v2|$limit|$offset|$sort"
    PodcastRepository.episodesCache[cacheKey]?.let { return V2PageRead.Page(it) }
    // A paced read waits out one 429 (the budget holds the route until Retry-After), then gives up.
    repeat(if (paced) PACED_ATTEMPTS else 1) {
        if (paced) routeBudget.acquire(ApiRouteBudget.V2_EPISODES)
        var limited = false
        val body =
            v2Answer("episodes", onRateLimited = { retryAfter ->
                limited = true
                routeBudget.rateLimited(ApiRouteBudget.V2_EPISODES, retryAfter)
            }) { apiV2.getEpisodes(showId, limit, offset, sort).awaitResponse() }
        if (body != null) {
            val page =
                PodcastRepository.EpisodePage(
                    episodes = discoveryEpisodeIds.toLocalOwners(body.items.mapNotNull(::mapToEpisode)),
                    hasMore = body.hasMore,
                    sourceCount = body.items.size,
                )
            PodcastRepository.episodesCache[cacheKey] = page
            return V2PageRead.Page(page)
        }
        if (!limited) return V2PageRead.Failed
    }
    return V2PageRead.Failed
}

private const val PACED_ATTEMPTS = 2
