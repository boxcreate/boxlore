package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshReason
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.model.Episode
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Source adapter for true RSS subscriptions. Never looks up or promotes a catalog show. */
@Suppress("TooManyFunctions") // Source adapter implements the complete read/refresh port.
class RssEpisodeCatalog internal constructor(
    private val database: BoxLoreDatabase,
    private val feedClient: RssFeedClient,
    private val locks: ConcurrentHashMap<String, Mutex>,
) : LocalEpisodeCatalogPort {
    private val podcasts = database.podcastDao()
    private val episodes = database.rssEpisodeDao()
    var onCatalogPersisted: suspend (String) -> Unit = {}

    override suspend fun isReady(podcastId: String): Boolean =
        podcasts.getPodcast(podcastId)?.isRss == true && episodes.count(podcastId) > 0

    override suspend fun getPage(podcastId: String, limit: Int, offset: Int, sort: String, meta: PodcastMeta): List<Episode> {
        val rows = if (sort == "oldest") episodes.getOldestPage(podcastId, limit, offset) else episodes.getNewestPage(podcastId, limit, offset)
        return rows.map { it.toEpisode(meta) }
    }

    override suspend fun getWindow(podcastId: String, sort: String, bound: Int, aroundEpisodeId: String?, meta: PodcastMeta): List<Episode> {
        val limit = bound.coerceIn(1, 1000)
        val anchor = aroundEpisodeId?.let { episodes.getEpisode(it) }?.takeIf { it.podcastId == podcastId }
            ?: return getPage(podcastId, limit, 0, sort, meta)
        // An anchored playback window always continues chronologically, like the PI adapter.
        val following = episodes.getEpisodesAfter(podcastId, anchor.publishedDate, anchor.episodeId, limit - 1)
        return (listOf(anchor) + following).map { it.toEpisode(meta) }
    }

    override suspend fun getEpisode(episodeId: String, meta: PodcastMeta): Episode? = episodes.getEpisode(episodeId)?.toEpisode(meta)

    override suspend fun findByCatalogKey(podcastId: String, guid: String?, enclosureUrl: String?, meta: PodcastMeta): Episode? {
        val byGuid = guid?.trim()?.takeIf { it.isNotEmpty() }?.let { episodes.getByGuid(podcastId, it) }
        val row = byGuid ?: enclosureUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { episodes.getByAudioUrl(podcastId, it) }
        return row?.toEpisode(meta)
    }

    override suspend fun search(podcastId: String, query: String, meta: PodcastMeta): List<Episode> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return episodes.search(podcastId, trimmed.escapeForSqlLike()).map { it.toEpisode(meta) }
    }

    override suspend fun newest(podcastId: String, meta: PodcastMeta): Episode? = episodes.getNewest(podcastId)?.toEpisode(meta)
    override suspend fun count(podcastId: String): Int = episodes.count(podcastId)

    override suspend fun refresh(request: RefreshRequest): RefreshOutcome = withContext(Dispatchers.IO) {
        val id = request.podcastIndexId
        val outcome = locks.getOrPut(id) { Mutex() }.withLock {
            try {
                refreshLocked(request)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
            }
        }
        val shouldNotify = outcome is RefreshOutcome.Success && request.runPostPersistCallback && request.reason != RefreshReason.AUTO_DOWNLOAD
        if (shouldNotify && request.canProceed()) notifyPersisted(id)
        outcome
    }

    private suspend fun notifyPersisted(id: String) {
        try {
            onCatalogPersisted(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Persisted releases remain available for foreground replay.
        }
    }

    private suspend fun refreshLocked(request: RefreshRequest): RefreshOutcome {
        val id = request.podcastIndexId
        val existing = podcasts.getPodcast(id)?.takeIf { it.isRss }
            ?: return failure()
        if (!request.canProceed()) return failure()
        // The saved URL owns private subscription identity; push payloads cannot replace it.
        val url = existing.feedUrl ?: return failure()
        val force = request.reason == RefreshReason.MANUAL || request.reason == RefreshReason.NEW_RELEASE
        val repair = needsRepair(existing)
        if (!force && !isRefreshDue(id, url, System.currentTimeMillis())) return RefreshOutcome.Unchanged(newest(id, request.meta))
        return PublisherFeedRefreshGate.permits.withPermit {
            if (!request.canProceed()) return@withPermit failure()
            fetchAndPersist(request, existing, force || repair)
        }
    }

    private suspend fun fetchAndPersist(request: RefreshRequest, existing: PodcastEntity, fullFetch: Boolean): RefreshOutcome {
        val url = existing.feedUrl ?: return failure()
        val outcome = try {
            val fetched = if (fullFetch) feedClient.fetch(url) else feedClient.fetchConditional(url, existing.feedEtag, existing.feedLastModified)
            if (!request.canProceed()) return failure()
            if (fetched == null) {
                recordUnchanged(request, url)
                RefreshOutcome.Unchanged(newest(existing.podcastId, request.meta))
            } else {
                persistFetched(request, existing, fetched)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failure()
        }
        if (outcome is RefreshOutcome.Failure) recordFailedAttempt(request, existing)
        return outcome
    }

    private suspend fun recordFailedAttempt(request: RefreshRequest, existing: PodcastEntity) = database.withTransaction {
        val current = podcasts.getPodcast(existing.podcastId) ?: return@withTransaction
        if (!canPersist(request, existing, current)) return@withTransaction
        // This marks the current automatic policy, not a successful catalog ingest.
        podcasts.upsert(current.copy(lastRssSyncAt = System.currentTimeMillis(), rssRefreshCapability = PodcastEntity.RSS_REFRESH_AUTOMATIC, rssCatalogStale = true))
    }

    private suspend fun needsRepair(row: PodcastEntity): Boolean =
        row.rssRefreshCapability != PodcastEntity.RSS_REFRESH_AUTOMATIC || row.rssCatalogStale || episodes.count(row.podcastId) == 0

    private suspend fun recordUnchanged(request: RefreshRequest, url: String) = database.withTransaction {
        val current = podcasts.getPodcast(request.podcastIndexId) ?: return@withTransaction
        if (current.feedUrl == url && request.canProceed()) podcasts.upsert(current.copy(lastRssSyncAt = System.currentTimeMillis()))
    }

    private suspend fun persistFetched(request: RefreshRequest, existing: PodcastEntity, fetched: RssFetchResult): RefreshOutcome {
        val id = request.podcastIndexId
        val parsed = feedClient.parse(fetched.finalUrl, fetched.body, id)
        if (parsed.episodes.isEmpty()) return failure()
        val sticky = StickyRssEpisodeRemap.prepare(parsed.episodes, episodes.listIdentities(id))
        var persisted = false
        database.withTransaction {
            // Re-read after I/O so a concurrent unsubscribe/settings change cannot be undone.
            val current = podcasts.getPodcast(id) ?: return@withTransaction
            if (!canPersist(request, existing, current)) return@withTransaction
            episodes.upsertAll(sticky.episodes)
            val meta = PodcastMeta(parsed.title, parsed.imageUrl ?: current.imageUrl, parsed.genre ?: current.genre, parsed.author.ifBlank { current.author })
            val tip = episodes.getNewest(id)?.toEpisode(meta)
            podcasts.upsert(updatedPodcast(current, parsed, fetched, tip))
            persisted = true
        }
        return if (persisted) RefreshOutcome.Success(newest(id, request.meta), episodes.count(id), ready = true) else failure()
    }

    private suspend fun canPersist(request: RefreshRequest, existing: PodcastEntity, current: PodcastEntity): Boolean {
        if (!request.canProceed() || current.feedUrl != existing.feedUrl) return false
        return !existing.isSubscribed || current.isSubscribed
    }

    private fun updatedPodcast(current: PodcastEntity, parsed: ParsedRssFeed, fetched: RssFetchResult, tip: Episode?): PodcastEntity = current.copy(
        title = parsed.title,
        author = parsed.author.ifBlank { current.author },
        imageUrl = parsed.imageUrl ?: current.imageUrl,
        description = parsed.description ?: current.description,
        genre = parsed.genre ?: current.genre,
        type = if (current.preferredSort != null) current.type else parsed.podcastType,
        latestEpisode = tip,
        lastRefreshed = System.currentTimeMillis(),
        feedUrl = fetched.finalUrl,
        feedEtag = fetched.etag,
        feedLastModified = fetched.lastModified,
        feedDeclaredUpdatedAt = parsed.declaredUpdatedAt,
        rssRefreshCapability = PodcastEntity.RSS_REFRESH_AUTOMATIC,
        lastRssSyncAt = System.currentTimeMillis(),
        rssCatalogStale = false,
        rssHasNewEpisodes = current.rssHasNewEpisodes || hasNewRelease(current.latestEpisode, tip),
    )

    private fun hasNewRelease(previous: Episode?, tip: Episode?): Boolean {
        if (previous == null || tip == null) return false
        return tip.id != previous.id && tip.publishedDate >= previous.publishedDate
    }

    private fun failure() = RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)

    override suspend fun isRefreshDue(podcastId: String, feedUrl: String, nowMillis: Long): Boolean {
        val row = podcasts.getPodcast(podcastId) ?: return false
        val firstRepair = needsRepair(row) && !row.rssCatalogStale
        return row.rssRefreshCapability != PodcastEntity.RSS_REFRESH_AUTOMATIC ||
            firstRepair ||
            row.lastRssSyncAt <= 0L ||
            nowMillis - row.lastRssSyncAt !in 0 until LocalEpisodeCatalogRepository.QUIET_INTERVAL_MS
    }

    // Conditional GET in refresh owns validator updates and its quiet interval.
    override suspend fun isPublisherFeedUnchanged(podcastId: String, feedUrl: String): Boolean = false
    override suspend fun markFeedUrlLookup(podcastId: String, atMillis: Long) = Unit
    override suspend fun lastFeedUrlLookupAt(podcastId: String): Long = 0L
    override suspend fun setUnsubscribedTtl(podcastId: String, ttlExpiresAt: Long?) = Unit
    override suspend fun sweepExpired(nowMillis: Long) = Unit

    private fun RssEpisodeEntity.toEpisode(meta: PodcastMeta): Episode = toEpisode(meta.title, meta.imageUrl, meta.genre, meta.artist)
}
