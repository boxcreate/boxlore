package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadOutcome
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.ReadRequest
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

    override suspend fun store(request: ReadRequest, document: ShowPageFeedPort.Document): ReadOutcome = withContext(Dispatchers.IO) {
        if (!isFeedShow(request.podcastId)) return@withContext ReadOutcome.Failed(OTHER)
        lockFor(request.podcastId).withLock {
            failSafe {
                val existing = deps.dao.getFeed(request.podcastId)
                val result = RssFetchResult(document.finalUrl, document.etag, null, document.bytes)
                // The transport address needs the API session: the row keeps the address it had, if any.
                persist(request, existing, Fetched(result, document.complete), url = null, recordedAddress = existing?.feedUrl.orEmpty())
            }
        }
    }

    override suspend fun markOpened(podcastId: String, atMillis: Long) = withContext(Dispatchers.IO) {
        deps.dao.setLastOpenedAt(podcastId, atMillis)
    }

    override suspend fun cleanUp(nowMillis: Long, keepPodcastId: String?): Int = withContext(Dispatchers.IO) {
        val victims = ShowPageFeedRules.cleanupVictims(deps.dao.listUnfollowedCopies(), nowMillis, keepPodcastId)
        victims.count { podcastId -> lockFor(podcastId).withLock { deps.dao.deleteCatalogIfUnfollowed(podcastId) } }
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

        /** Show pages store copies for Podcast Index shows; `rss:` shows have their own table. */
        fun isFeedShow(podcastId: String): Boolean = podcastId.isNotBlank() && !podcastId.startsWith("rss:")

        /** The feed's order as its document lists the items, not as the parser sorted them. */
        fun documentOrder(parsed: ParsedRssFeed): String =
            FeedOrderLogic.classify(parsed.items.sortedBy(ParsedFeedItem::documentIndex).map { it.episode.publishedDate })
    }
}
