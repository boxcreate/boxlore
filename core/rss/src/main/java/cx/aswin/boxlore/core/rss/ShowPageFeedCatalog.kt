package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadOutcome
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadRequest
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * [ShowPageFeedPort] on the local episode catalog: a show page's reads go into the same
 * `local_episodes` rows a followed show's refresh writes, through the same write
 * ([persistParsed]: sticky IDs, recipe IDs for new items, aliases, the collision guard), under the
 * same per-show lock. What differs is recorded in the feed row ([FeedRowPolicy]): a read of the
 * newest items leaves the archive incomplete, and a show-page read never makes an unfollowed copy
 * the followed live list. No post-write work runs (auto-download, delivery).
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page; decision D10).
 * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
 */
internal class ShowPageFeedCatalog(
    private val deps: LocalCatalogRefreshDeps,
    private val lockFor: (String) -> Mutex,
) : ShowPageFeedPort {
    override suspend fun cached(podcastId: String): ShowPageFeedPort.CachedShow? = withContext(Dispatchers.IO) {
        val feed = deps.dao.getFeed(podcastId) ?: return@withContext null
        val count = deps.dao.count(podcastId)
        if (count <= 0) return@withContext null
        ShowPageFeedPort.CachedShow(
            podcastId = podcastId,
            feedUrl = feed.feedUrl,
            fetchedAt = feed.fetchedAt,
            itemCount = count,
            archiveComplete = feed.archiveComplete,
            oldestFirst = feed.feedOrder == LocalFeedOrder.OLDEST_FIRST,
        )
    }

    override suspend fun read(request: ReadRequest): ReadOutcome = withContext(Dispatchers.IO) {
        if (!isFeedShow(request.podcastId)) return@withContext ReadOutcome.Failed(OTHER)
        val url = LocalEpisodeCatalogRepository.resolveHttps(request.feedUrl, null) ?: return@withContext ReadOutcome.Failed(OTHER)
        lockFor(request.podcastId).withLock {
            failSafe {
                val existing = deps.dao.getFeed(request.podcastId)
                val wholeFeed = ShowPageFeedRules.readsWholeFeed(request.scope, existing)
                val fetched = fetch(url, wholeFeed, existing) ?: return@failSafe notModified(request.podcastId, existing)
                persist(request, existing, fetched, url)
            }
        }
    }

    override suspend fun readTransport(
        request: ReadRequest,
        fetch: suspend (ifNoneMatch: String?) -> ShowPageFeedPort.Transported,
    ): ReadOutcome = withContext(Dispatchers.IO) {
        if (!isFeedShow(request.podcastId)) return@withContext ReadOutcome.Failed(OTHER)
        var direct: String? = null
        val outcome =
            lockFor(request.podcastId).withLock {
                failSafe {
                    val existing = deps.dao.getFeed(request.podcastId)
                    // Transport documents are the whole feed: the ETag goes out only for a whole copy.
                    val etag = ShowPageFeedRules.validators(wholeFeed = true, existing)?.first
                    when (val answer = deps.megaGetGate.withPermit { fetch(etag) }) {
                        is ShowPageFeedPort.Transported.Document -> storeTransported(request, existing, answer)
                        ShowPageFeedPort.Transported.NotModified ->
                            if (etag != null) notModified(request.podcastId, existing) else ReadOutcome.Failed(OTHER)
                        is ShowPageFeedPort.Transported.ReadDirect -> {
                            direct = answer.url
                            ReadOutcome.Failed(OTHER)
                        }
                        is ShowPageFeedPort.Transported.Failed -> ReadOutcome.Failed(answer.reportStatus)
                    }
                }
            }
        // Read outside the lock: [read] takes it again.
        direct?.let { url -> read(request.copy(feedUrl = url)) } ?: outcome
    }

    override suspend fun search(podcastId: String, query: String, limit: Int, meta: PodcastMeta,): List<Episode> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || limit <= 0) return@withContext emptyList()
        deps.dao.searchLimited(podcastId, trimmed.escapeForSqlLike(), limit).map { it.toCatalogEpisode(meta) }
    }

    /**
     * A transport document. The phone never mints IDs from an http address ([RssIdGenerator]
     * refuses them), so no older ID of the show can be under its publisher address; its namespace
     * is one fixed address per show ([transportNamespace]), which never changes with the API's
     * host. The row keeps the address it had, if any: the transport address needs the API session
     * and is never recorded.
     */
    private suspend fun storeTransported(
        request: ReadRequest,
        existing: LocalEpisodeFeedEntity?,
        document: ShowPageFeedPort.Transported.Document,
    ): ReadOutcome {
        val result = RssFetchResult(transportNamespace(request.podcastId), document.etag, null, document.bytes)
        return persist(request, existing, Fetched(result, complete = true), url = null, recordedAddress = existing?.feedUrl.orEmpty())
    }

    override suspend fun markOpened(podcastId: String, atMillis: Long) = withContext(Dispatchers.IO) {
        deps.dao.setLastOpenedAt(podcastId, atMillis)
    }

    override suspend fun cleanUp(nowMillis: Long, keepPodcastId: String?): Int = withContext(Dispatchers.IO) {
        val victims = ShowPageFeedRules.cleanupVictims(deps.dao.listUnfollowedCopies(), nowMillis, keepPodcastId)
        victims.count { podcastId -> lockFor(podcastId).withLock { deps.dao.deleteCatalogIfUnfollowed(podcastId) } }
    }

    override suspend fun unfollowedCount(): Int = withContext(Dispatchers.IO) { deps.dao.listUnfollowedCopies().size }

    override suspend fun clearUnfollowed(): Int = withContext(Dispatchers.IO) {
        // Each delete checks again that the show is unfollowed, so a show followed meanwhile keeps its copy.
        deps.dao.listUnfollowedCopies().count { copy -> lockFor(copy.podcastId).withLock { deps.dao.deleteCatalogIfUnfollowed(copy.podcastId) } }
    }

    private class Fetched(val result: RssFetchResult, val complete: Boolean)

    /** The read, or null for a 304 that answers it. */
    private suspend fun fetch(url: String, wholeFeed: Boolean, existing: LocalEpisodeFeedEntity?): Fetched? {
        val validators = ShowPageFeedRules.validators(wholeFeed, existing)
        if (!wholeFeed) {
            val prefix = deps.feedClient.fetchNewest(url, ShowPageFeedRules.NEWEST_ITEMS, validators?.first, validators?.second) ?: return null
            return Fetched(prefix.result, prefix.complete)
        }
        val conditional = deps.megaGetGate.withPermit { deps.feedClient.fetchConditional(url, validators?.first, validators?.second) }
        if (conditional != null) return Fetched(conditional, complete = true)
        if (ShowPageFeedRules.acceptsNotModified(wholeFeed = true, cachedArchiveComplete = existing?.archiveComplete == true)) return null
        // A 304 that covers only the stored newest items is not the archive: read it whole.
        return Fetched(deps.megaGetGate.withPermit { deps.feedClient.fetch(url) }, complete = true)
    }

    private suspend fun notModified(podcastId: String, existing: LocalEpisodeFeedEntity?): ReadOutcome {
        existing?.let { deps.dao.upsertFeed(it.copy(fetchedAt = deps.nowMillis())) }
        deps.dao.setLastOpenedAt(podcastId, deps.nowMillis())
        return ReadOutcome.NotModified
    }

    private suspend fun persist(
        request: ReadRequest,
        existing: LocalEpisodeFeedEntity?,
        first: Fetched,
        url: String?,
        recordedAddress: String? = null,
    ): ReadOutcome {
        var fetched = first
        var parsed = parseOrNull(fetched)
        if (parsed == null && !fetched.complete && url != null) {
            // The cut-short document didn't parse (an unusual root): read the whole feed instead.
            fetched = Fetched(deps.megaGetGate.withPermit { deps.feedClient.fetch(url) }, complete = true)
            parsed = parseOrNull(fetched)
        }
        if (parsed == null) return ReadOutcome.Failed(FeedReadFailure.PARSE)
        var order = documentOrder(parsed)
        if (!fetched.complete && order == LocalFeedOrder.OLDEST_FIRST && url != null) {
            // The start of an oldest-first feed holds its oldest items, not its newest.
            fetched = Fetched(deps.megaGetGate.withPermit { deps.feedClient.fetch(url) }, complete = true)
            parsed = parseOrNull(fetched) ?: return ReadOutcome.Failed(FeedReadFailure.PARSE)
            order = documentOrder(parsed)
        }
        val refresh =
            RefreshRequest(
                podcastIndexId = request.podcastId,
                feedUrl = url ?: fetched.result.finalUrl,
                meta = request.meta,
                runPostPersistCallback = false,
                serverEpNs = request.serverEpNs,
            )
        val policy =
            FeedRowPolicy(
                complete = fetched.complete,
                showPage = true,
                documentOrder = order,
                openedAt = deps.nowMillis(),
                recordedAddress = recordedAddress,
            )
        val namespace = RssIdGenerator.podcastId(fetched.result.finalUrl)
        return when (val outcome = persistParsed(deps, refresh, existing, fetched.result, namespace, parsed, policy)) {
            is RefreshOutcome.Success -> ReadOutcome.Stored(outcome.itemCount, fetched.complete)
            is RefreshOutcome.Unchanged -> ReadOutcome.NotModified
            is RefreshOutcome.Failure -> ReadOutcome.Failed(outcome.reportStatus ?: OTHER)
        }
    }

    private suspend fun parseOrNull(fetched: Fetched): ParsedRssFeed? = try {
        deps.feedClient.parse(fetched.result.finalUrl, fetched.result.body, RssIdGenerator.podcastId(fetched.result.finalUrl))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun failSafe(block: suspend () -> ReadOutcome): ReadOutcome = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ReadOutcome.Failed(FeedReadFailure.reportStatus(error))
    }

    private companion object {
        const val OTHER = "other"

        /** The parse and namespace address of [podcastId]'s transport documents. */
        fun transportNamespace(podcastId: String): String = "https://transport.boxlore.invalid/feed/$podcastId"

        /** Show pages store copies for Podcast Index shows; `rss:` shows have their own table. */
        fun isFeedShow(podcastId: String): Boolean = podcastId.isNotBlank() && !podcastId.startsWith("rss:")

        /** The feed's order as its document lists the items, not as the parser sorted them. */
        fun documentOrder(parsed: ParsedRssFeed): String =
            FeedOrderLogic.classify(parsed.items.sortedBy(ParsedFeedItem::documentIndex).map { it.episode.publishedDate })
    }
}
