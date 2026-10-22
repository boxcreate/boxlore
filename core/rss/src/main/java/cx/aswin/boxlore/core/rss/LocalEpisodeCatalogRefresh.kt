package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshReason
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class LocalCatalogRefreshDeps(
    val dao: LocalEpisodeCatalogDao,
    val feedClient: RssFeedClient,
    val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    val isFeedUnchanged: suspend (url: String, etag: String?, lastModified: String?) -> Boolean,
    val reconcileListenerState: suspend (String, List<LocalEpisodeEntity>) -> Unit,
    val megaGetGate: Semaphore,
)

internal suspend fun refreshLocalCatalogLocked(deps: LocalCatalogRefreshDeps, request: RefreshRequest,): RefreshOutcome {
    if (!request.canProceed()) return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    if (request.podcastIndexId.isBlank() || request.podcastIndexId.startsWith("rss:")) {
        return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    }
    val existing = deps.dao.getFeed(request.podcastIndexId)
    val url =
        LocalEpisodeCatalogRepository.resolveHttps(request.feedUrl, existing?.feedUrl)
            ?: return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    if (url == existing?.feedUrl?.trim() && shouldSkipQuiet(existing, request.reason)) {
        return RefreshOutcome.Unchanged(
            deps.dao.getNewest(request.podcastIndexId)?.toCatalogEpisode(request.meta),
        )
    }
    val forceFetch = request.reason == RefreshReason.NEW_RELEASE || request.reason == RefreshReason.MANUAL
    if (!forceFetch &&
        existing != null &&
        publisherFeedUnchanged(deps, request.podcastIndexId, url)
    ) {
        if (!request.canProceed()) return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        deps.dao.upsertFeed(existing.copy(fetchedAt = System.currentTimeMillis()))
        return RefreshOutcome.Unchanged(
            deps.dao.getNewest(request.podcastIndexId)?.toCatalogEpisode(request.meta),
        )
    }
    return fetchAndPersist(deps, request, url, existing)
}

private suspend fun publisherFeedUnchanged(deps: LocalCatalogRefreshDeps, podcastId: String, url: String,): Boolean {
    val existing = deps.dao.getFeed(podcastId) ?: return false
    if (!LocalCatalogReadyLogic.isReady(existing)) return false
    if (existing.feedEtag.isNullOrBlank() && existing.feedLastModified.isNullOrBlank()) {
        return false
    }
    val resolved =
        LocalEpisodeCatalogRepository.resolveHttps(url, existing.feedUrl) ?: return false
    if (resolved != existing.feedUrl.trim()) {
        return false
    }
    return try {
        deps.isFeedUnchanged(resolved, existing.feedEtag, existing.feedLastModified)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

internal fun shouldLoadPiBaseline(existing: LocalEpisodeFeedEntity?): Boolean = existing == null || !LocalCatalogReadyLogic.isReady(existing)

internal fun shouldSkipQuiet(
    existing: LocalEpisodeFeedEntity?,
    reason: RefreshReason = RefreshReason.NORMAL,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    if (reason == RefreshReason.NEW_RELEASE || reason == RefreshReason.MANUAL) return false
    if (existing == null) return false
    if (!LocalCatalogReadyLogic.isReady(existing)) return false
    if (existing.needsFullBackfill) return false
    if (existing.fetchedAt <= 0L) return false
    return nowMillis - existing.fetchedAt in 0 until LocalEpisodeCatalogRepository.QUIET_INTERVAL_MS
}

private suspend fun fetchAndPersist(
    deps: LocalCatalogRefreshDeps,
    request: RefreshRequest,
    url: String,
    existing: LocalEpisodeFeedEntity?,
): RefreshOutcome = try {
    deps.megaGetGate.withPermit {
        if (!request.canProceed()) return@withPermit RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        val fetched = deps.feedClient.fetch(url)
        if (!request.canProceed()) return@withPermit RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        val rssNamespaceId = RssIdGenerator.podcastId(fetched.finalUrl)
        val parsed =
            deps.feedClient.parse(
                feedUrl = fetched.finalUrl,
                bytes = fetched.body,
                podcastId = rssNamespaceId,
            )
        if (!request.canProceed()) return@withPermit RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        val baseline =
            if (shouldLoadPiBaseline(existing)) {
                request.loadPiBaseline?.invoke()
            } else {
                null
            }
        if (!request.canProceed()) return@withPermit RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        persistParsed(deps, request, existing, fetched, rssNamespaceId, parsed, baseline)
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE, addressGone = FeedReadFailure.isAddressGone(error))
}

private suspend fun persistParsed(
    deps: LocalCatalogRefreshDeps,
    request: RefreshRequest,
    existing: LocalEpisodeFeedEntity?,
    fetched: RssFetchResult,
    rssNamespaceId: String,
    parsed: ParsedRssFeed,
    baseline: List<Episode>?,
): RefreshOutcome {
    val identities = deps.dao.listIdentities(request.podcastIndexId)
    val rows =
        LocalEpisodeCatalogPersist.toLocalEpisodes(
            podcastIndexId = request.podcastIndexId,
            rssNamespaceId = rssNamespaceId,
            parsed = parsed.episodes,
            existing = identities,
            piBaseline = baseline,
            channelImageUrl = parsed.imageUrl,
            showImageUrl = request.meta.imageUrl,
        )
    if (rows.isEmpty()) {
        // The address answered, but with nothing we can read as episodes.
        return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE, addressGone = true)
    }
    val copied = existing?.copiedExtrasCount ?: 0
    val feedOrder =
        existing
            ?.feedOrder
            ?.takeIf { it != LocalFeedOrder.MIXED }
            ?: FeedOrderLogic.classify(parsed.episodes.map { it.publishedDate })
    var written = rows
    var storedCount = rows.size
    var ready = false
    var incomplete = false
    deps.runInTransaction {
        // Another show reading the same feed may already hold these IDs: never replace its rows.
        written = LocalEpisodeCollisionGuard.resolve(rows, rssNamespaceId) { ids -> deps.dao.findOwners(ids) }
        if (written.size < rows.size) {
            // A row found no free ID: write nothing rather than mark a partial catalog complete.
            incomplete = true
            return@runInTransaction
        }
        deps.dao.upsertEpisodes(written)
        deps.reconcileListenerState(request.podcastIndexId, written)
        storedCount = maxOf(deps.dao.count(request.podcastIndexId), written.size)
        ready = LocalCatalogReadyLogic.isReadyToFlip(feedReady = storedCount >= copied)
        deps.dao.upsertFeed(
            LocalEpisodeFeedEntity(
                podcastId = request.podcastIndexId,
                feedUrl = fetched.finalUrl,
                feedEtag = fetched.etag,
                feedLastModified = fetched.lastModified,
                fetchedAt = System.currentTimeMillis(),
                itemCount = storedCount,
                feedOrder = feedOrder,
                ttlExpiresAt = null,
                needsFullBackfill = false,
                copiedExtrasCount = copied,
                ready = ready,
                feedUrlLookupAt = existing?.feedUrlLookupAt ?: 0L,
            ),
        )
    }
    if (incomplete) return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    val newestRow = written.maxByOrNull { it.publishedDate }
    return RefreshOutcome.Success(
        newest = newestRow?.toCatalogEpisode(request.meta),
        itemCount = storedCount,
        ready = ready,
        movedTo = if (request.detectMove) confirmedMove(deps.feedClient, request, fetched, parsed) else null,
    )
}

/**
 * The feed's new permanent HTTPS address, or null (P4a). Before, a moved feed stayed on its old
 * address: OkHttp followed the redirect, but the saved address never changed, so every pass
 * downloaded the whole feed again and the address other devices and notifications use stayed old.
 * Adopted only when the new address is HTTPS, differs from the saved one and serves the same show
 * (same title as [RefreshRequest.meta]), as the server's address checks do: from redirects that
 * were all permanent ([RssFetchResult.movedTo]), or from `itunes:new-feed-url`, read once to
 * confirm. Temporary redirects (302, 307) never move a feed.
 */
internal suspend fun confirmedMove(feedClient: RssFeedClient, request: RefreshRequest, fetched: RssFetchResult, parsed: ParsedRssFeed,): String? {
    val showTitle = request.meta.title?.takeIf(String::isNotBlank) ?: return null
    val saved = request.feedUrl.trim()
    fetched.movedTo
        ?.takeIf { isNewHttpsAddress(it, saved) && sameShow(parsed.title, showTitle) }
        ?.let { return it }
    val declared =
        parsed.newFeedUrl
            ?.trim()
            ?.takeIf { isNewHttpsAddress(it, saved) && isNewHttpsAddress(it, fetched.finalUrl) }
            ?: return null
    return try {
        val next = feedClient.fetch(declared)
        val nextParsed = feedClient.parse(next.finalUrl, next.body, RssIdGenerator.podcastId(next.finalUrl))
        next.finalUrl.takeIf { isNewHttpsAddress(it, saved) && sameShow(nextParsed.title, showTitle) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

private fun isNewHttpsAddress(candidate: String, current: String): Boolean =
    candidate.startsWith("https://", ignoreCase = true) && !candidate.trim().equals(current.trim(), ignoreCase = true)

/** Same show by title, ignoring case, spacing and punctuation. */
internal fun sameShow(feedTitle: String, showTitle: String): Boolean {
    val normalize = { value: String -> value.lowercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit) }
    val a = normalize(feedTitle)
    return a.isNotEmpty() && a == normalize(showTitle)
}
