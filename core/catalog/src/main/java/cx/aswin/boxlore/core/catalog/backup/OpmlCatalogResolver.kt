package cx.aswin.boxlore.core.catalog.backup

import android.util.Log
import cx.aswin.boxlore.core.catalog.ApiRouteBudget
import cx.aswin.boxlore.core.catalog.ExactPodcastLookupKey
import cx.aswin.boxlore.core.catalog.ExactPodcastLookupResult
import cx.aswin.boxlore.core.catalog.ExactPodcastLookupType
import cx.aswin.boxlore.core.catalog.PodcastIndexSearchResult
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.rss.FeedHttpException
import cx.aswin.boxlore.core.rss.RssFeedClient

/**
 * Finds an OPML outline's show in the catalog ([OpmlImportLogic]): the outline's address and its
 * spellings, the title search (none for an outline without a title), then the feed's own address
 * and GUID. Every API request first asks [pace] for room on its route ([ApiRouteBudget]); a refusal,
 * a 429 or a failed lookup of the outline's address leaves the outline unanswered at once, so a busy
 * route costs one request per outline instead of every spelling. An outline left unanswered by a
 * refusal or a 429 is [OpmlCatalogDecision.Limited] (asked again once there's room), by any other
 * failure [OpmlCatalogDecision.Deferred]. Once the API has answered, a feed that can't be read is
 * [OpmlCatalogDecision.Unresolvable]: asking again wouldn't add it. One outline at a time.
 */
internal class OpmlCatalogResolver(
    private val podcastRepository: PodcastRepository,
    private val rssFeedClient: RssFeedClient,
    private val pace: suspend (route: String) -> Boolean,
) {
    /** Whether the outline being resolved met a refusal or a 429. */
    private var limited = false

    suspend fun resolve(feed: OpmlFeed): OpmlCatalogDecision {
        limited = false
        val decision = decide(feed)
        return if (decision == OpmlCatalogDecision.Deferred && limited) OpmlCatalogDecision.Limited else decision
    }

    private suspend fun decide(feed: OpmlFeed): OpmlCatalogDecision {
        val initialUrlLookup = lookupByUrl(feed.xmlUrl)
        when (initialUrlLookup) {
            is ExactPodcastLookupResult.Found -> return OpmlCatalogDecision.Found(initialUrlLookup.podcast)
            ExactPodcastLookupResult.Failed -> return OpmlCatalogDecision.Deferred
            ExactPodcastLookupResult.NotFound -> Unit
        }
        val titleSearch = searchTitle(feed.title)
        if (titleSearch is PodcastIndexSearchResult.Success) {
            OpmlImportLogic
                .catalogMatch(
                    opmlTitle = feed.title,
                    opmlXmlUrl = feed.xmlUrl,
                    urlLookup = null,
                    titleSearch = titleSearch.podcasts,
                )?.let { return OpmlCatalogDecision.Found(it) }
        }

        // The catalog answered (no match) but the feed itself can't be read: a dead or non-web
        // address. An unanswered search, or a read that may work later (a timeout, no connection, a
        // server error), leaves room for a later try to find it.
        val peeked =
            when (val peek = peek(feed.xmlUrl)) {
                is FeedPeek.Read -> peek.feed
                is FeedPeek.Failed ->
                    return if (titleSearch is PodcastIndexSearchResult.Success && !peek.transient) OpmlCatalogDecision.Unresolvable else OpmlCatalogDecision.Deferred
            }
        val redirectedUrlLookup =
            if (OpmlImportLogic.canonicalFeedUrl(peeked.finalUrl) == OpmlImportLogic.canonicalFeedUrl(feed.xmlUrl)) {
                // The feed didn't move: its address was just asked under every spelling.
                ExactPodcastLookupResult.NotFound
            } else {
                lookupByUrl(peeked.finalUrl)
            }
        val urlLookup = OpmlImportLogic.preferLookup(initialUrlLookup, redirectedUrlLookup)
        val guidLookup =
            peeked.guid
                ?.let { lookupByGuid(it) }
                ?: ExactPodcastLookupResult.NotFound
        return OpmlImportLogic.finalCatalogDecision(
            opmlTitle = peeked.title.ifBlank { feed.title },
            opmlXmlUrl = peeked.finalUrl,
            urlLookup = urlLookup,
            guidLookup = guidLookup,
            titleSearch = titleSearch,
        )
    }

    private suspend fun lookupByUrl(xmlUrl: String): ExactPodcastLookupResult = OpmlImportLogic.firstExactLookup(OpmlImportLogic.urlLookupCandidates(xmlUrl)) { candidate ->
        lookup(ExactPodcastLookupKey(type = ExactPodcastLookupType.FEED_URL, value = candidate))
    }

    private suspend fun lookupByGuid(guid: String): ExactPodcastLookupResult {
        val value = guid.trim()
        if (value.isEmpty()) return ExactPodcastLookupResult.NotFound
        return lookup(ExactPodcastLookupKey(type = ExactPodcastLookupType.PODCAST_GUID, value = value))
    }

    private suspend fun lookup(key: ExactPodcastLookupKey): ExactPodcastLookupResult {
        if (!paced(ApiRouteBudget.PODCAST)) return ExactPodcastLookupResult.Failed
        return podcastRepository.lookupExactPodcastIndex(key).also { if (it == ExactPodcastLookupResult.Failed) noteRateLimit(ApiRouteBudget.PODCAST) }
    }

    /** The title search; an outline without a title has nothing to search for, which counts as an answer with no shows. */
    private suspend fun searchTitle(title: String): PodcastIndexSearchResult {
        if (title.isBlank()) return PodcastIndexSearchResult.Success(emptyList())
        if (!paced(ApiRouteBudget.SEARCH)) return PodcastIndexSearchResult.Failed
        return podcastRepository.searchPodcastIndexForOpml(title).also { if (it == PodcastIndexSearchResult.Failed) noteRateLimit(ApiRouteBudget.SEARCH) }
    }

    private suspend fun paced(route: String): Boolean = pace(route).also { if (!it) limited = true }

    /** After a failed request: a 429 holds [route] ([ApiRouteBudget.rateLimited]), so the outline was limited, not refused. */
    private suspend fun noteRateLimit(route: String) {
        if (podcastRepository.routeBudget.isPaused(route)) limited = true
    }

    private suspend fun peek(xmlUrl: String): FeedPeek {
        val httpsUrl = OpmlImportLogic.httpsFeedUrl(xmlUrl) ?: return FeedPeek.Failed(transient = false)
        return try {
            val fetched = rssFeedClient.fetch(httpsUrl)
            val parsed = rssFeedClient.parse(fetched.finalUrl, fetched.body)
            FeedPeek.Read(PeekedOpmlFeed(finalUrl = fetched.finalUrl, title = parsed.title, guid = parsed.podcastGuid))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("OPML_IMPORT", "Feed peek failed for $xmlUrl", error)
            FeedPeek.Failed(transient = isTransient(error))
        }
    }

    /** A read that may work later: no connection, a timeout, or a server that is busy or down. An HTTPS handshake failure is not. */
    private fun isTransient(error: Exception): Boolean = when (error) {
        is FeedHttpException -> error.code >= SERVER_ERROR || error.code == REQUEST_TIMEOUT || error.code == TOO_MANY_REQUESTS
        is javax.net.ssl.SSLException -> false
        is java.io.IOException -> true
        else -> false
    }

    private sealed interface FeedPeek {
        data class Read(val feed: PeekedOpmlFeed) : FeedPeek

        data class Failed(val transient: Boolean) : FeedPeek
    }

    private data class PeekedOpmlFeed(val finalUrl: String, val title: String, val guid: String?,)
}

private const val SERVER_ERROR = 500
private const val REQUEST_TIMEOUT = 408
private const val TOO_MANY_REQUESTS = 429
