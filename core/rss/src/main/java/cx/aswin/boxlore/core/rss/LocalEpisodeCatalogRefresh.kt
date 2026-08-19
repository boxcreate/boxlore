package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
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

internal suspend fun refreshLocalCatalogLocked(
    deps: LocalCatalogRefreshDeps,
    request: RefreshRequest,
): RefreshOutcome {
    if (request.podcastIndexId.isBlank() || request.podcastIndexId.startsWith("rss:")) {
        return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    }
    val existing = deps.dao.getFeed(request.podcastIndexId)
    val url =
        LocalEpisodeCatalogRepository.resolveHttps(request.feedUrl, existing?.feedUrl)
            ?: return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    if (shouldSkipQuiet(existing)) {
        return RefreshOutcome.Unchanged(
            deps.dao.getNewest(request.podcastIndexId)?.toCatalogEpisode(request.meta),
        )
    }
    if (existing != null &&
        publisherFeedUnchanged(deps, request.podcastIndexId, url)
    ) {
        deps.dao.upsertFeed(existing.copy(fetchedAt = System.currentTimeMillis()))
        return RefreshOutcome.Unchanged(
            deps.dao.getNewest(request.podcastIndexId)?.toCatalogEpisode(request.meta),
        )
    }
    return fetchAndPersist(deps, request, url, existing)
}

private suspend fun publisherFeedUnchanged(
    deps: LocalCatalogRefreshDeps,
    podcastId: String,
    url: String,
): Boolean {
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

internal fun shouldLoadPiBaseline(existing: LocalEpisodeFeedEntity?): Boolean =
    existing == null || !LocalCatalogReadyLogic.isReady(existing)

internal fun shouldSkipQuiet(existing: LocalEpisodeFeedEntity?): Boolean {
    if (existing == null) return false
    if (!LocalCatalogReadyLogic.isReady(existing)) return false
    if (existing.needsFullBackfill) return false
    if (!existing.feedEtag.isNullOrBlank() || !existing.feedLastModified.isNullOrBlank()) {
        return false
    }
    if (existing.fetchedAt <= 0L) return false
    return System.currentTimeMillis() - existing.fetchedAt <
        LocalEpisodeCatalogRepository.QUIET_INTERVAL_MS
}

private suspend fun fetchAndPersist(
    deps: LocalCatalogRefreshDeps,
    request: RefreshRequest,
    url: String,
    existing: LocalEpisodeFeedEntity?,
): RefreshOutcome =
    try {
        deps.megaGetGate.withPermit {
            val fetched = deps.feedClient.fetch(url)
            val rssNamespaceId = RssIdGenerator.podcastId(fetched.finalUrl)
            val parsed =
                deps.feedClient.parse(
                    feedUrl = fetched.finalUrl,
                    bytes = fetched.body,
                    podcastId = rssNamespaceId,
                )
            val baseline =
                if (shouldLoadPiBaseline(existing)) {
                    request.loadPiBaseline?.invoke()
                } else {
                    null
                }
            persistParsed(deps, request, existing, fetched, rssNamespaceId, parsed, baseline)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
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
        return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    }
    val copied = existing?.copiedExtrasCount ?: 0
    val feedOrder =
        existing
            ?.feedOrder
            ?.takeIf { it != LocalFeedOrder.MIXED }
            ?: FeedOrderLogic.classify(parsed.episodes.map { it.publishedDate })
    val newestRow = rows.maxByOrNull { it.publishedDate }
    var storedCount = rows.size
    var ready = false
    deps.runInTransaction {
        deps.dao.upsertEpisodes(rows)
        deps.reconcileListenerState(request.podcastIndexId, rows)
        storedCount = maxOf(deps.dao.count(request.podcastIndexId), rows.size)
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
    return RefreshOutcome.Success(
        newest = newestRow?.toCatalogEpisode(request.meta),
        itemCount = storedCount,
        ready = ready,
    )
}
