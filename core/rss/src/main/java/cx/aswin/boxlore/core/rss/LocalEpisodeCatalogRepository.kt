package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * First-class local episode catalog for subscribed Podcast Index shows.
 *
 * Sticky upsert by guid. Never remints. Never treats a prefix parse as the archive.
 */
class LocalEpisodeCatalogRepository internal constructor(
    private val dao: LocalEpisodeCatalogDao,
    private val feedClient: RssFeedClient,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val isFeedUnchanged: suspend (
        url: String,
        etag: String?,
        lastModified: String?,
    ) -> Boolean,
    private val loadListenerEpisodeIds: suspend (String) -> Set<String>,
    private val loadKnownTip: suspend (String) -> Pair<String, Long>?,
    private val megaGetGate: Semaphore,
) : LocalEpisodeCatalogPort {
    constructor(
        database: BoxLoreDatabase,
        feedClient: RssFeedClient = RssFeedClient(),
        loadListenerEpisodeIds: suspend (String) -> Set<String> = { emptySet() },
        loadKnownTip: suspend (String) -> Pair<String, Long>? = { null },
    ) : this(
        dao = database.localEpisodeCatalogDao(),
        feedClient = feedClient,
        runInTransaction = { block -> database.withTransaction { block() } },
        isFeedUnchanged = { url, etag, lastModified ->
            feedClient.confirmUnchanged(url, etag, lastModified)
        },
        loadListenerEpisodeIds = loadListenerEpisodeIds,
        loadKnownTip = loadKnownTip,
        megaGetGate = Semaphore(MEGA_GET_PERMITS),
    )

    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun isReady(podcastId: String): Boolean = withContext(Dispatchers.IO) {
        LocalCatalogReadyLogic.isReady(dao.getFeed(podcastId))
    }

    override suspend fun getPage(
        podcastId: String,
        limit: Int,
        offset: Int,
        sort: String,
        meta: PodcastMeta,
    ): List<Episode> = withContext(Dispatchers.IO) {
        val rows =
            if (sort == "oldest") {
                dao.getOldestPage(podcastId, limit, offset)
            } else {
                dao.getNewestPage(podcastId, limit, offset)
            }
        rows.map { it.toDomain(meta) }
    }

    override suspend fun getWindow(
        podcastId: String,
        sort: String,
        bound: Int,
        aroundEpisodeId: String?,
        meta: PodcastMeta,
    ): List<Episode> = withContext(Dispatchers.IO) {
        val limit = bound.coerceAtLeast(1)
        val around = aroundEpisodeId?.let { dao.getEpisode(it) }
        val rows =
            if (around != null && around.podcastId == podcastId) {
                continuationRows(podcastId, sort, around, limit)
            } else if (sort == "oldest") {
                dao.getOldestPage(podcastId, limit, 0)
            } else {
                dao.getNewestPage(podcastId, limit, 0)
            }
        rows.map { it.toDomain(meta) }
    }

    override suspend fun getEpisode(
        episodeId: String,
        meta: PodcastMeta,
    ): Episode? = withContext(Dispatchers.IO) {
        dao.getEpisode(episodeId)?.toDomain(meta)
    }

    override suspend fun findByCatalogKey(
        podcastId: String,
        guid: String?,
        enclosureUrl: String?,
        meta: PodcastMeta,
    ): Episode? = withContext(Dispatchers.IO) {
        val key = StickyEpisodeIdentity.catalogKey(guid, enclosureUrl) ?: return@withContext null
        dao.getByGuid(podcastId, key)?.toDomain(meta)
    }

    override suspend fun search(
        podcastId: String,
        query: String,
        meta: PodcastMeta,
    ): List<Episode> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        dao.search(podcastId, trimmed.escapeForSqlLike()).map { it.toDomain(meta) }
    }

    override suspend fun newest(
        podcastId: String,
        meta: PodcastMeta,
    ): Episode? = withContext(Dispatchers.IO) {
        dao.getNewest(podcastId)?.toDomain(meta)
    }

    override suspend fun count(podcastId: String): Int = withContext(Dispatchers.IO) {
        dao.count(podcastId)
    }

    override suspend fun isPublisherFeedUnchanged(
        podcastId: String,
        feedUrl: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getFeed(podcastId) ?: return@withContext false
        if (existing.feedEtag.isNullOrBlank() && existing.feedLastModified.isNullOrBlank()) {
            return@withContext false
        }
        val url = resolveHttps(feedUrl, existing.feedUrl) ?: return@withContext false
        try {
            isFeedUnchanged(url, existing.feedEtag, existing.feedLastModified)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refresh(request: RefreshRequest): RefreshOutcome =
        withContext(Dispatchers.IO) {
            val lock = refreshLocks.getOrPut(request.podcastIndexId) { Mutex() }
            lock.withLock { refreshLocked(request) }
        }

    override suspend fun markFeedUrlLookup(podcastId: String, atMillis: Long) =
        withContext(Dispatchers.IO) {
            val existing = dao.getFeed(podcastId)
            if (existing != null) {
                dao.upsertFeed(existing.copy(feedUrlLookupAt = atMillis))
                return@withContext
            }
            dao.upsertFeed(stubFeed(podcastId, feedUrlLookupAt = atMillis))
        }

    override suspend fun lastFeedUrlLookupAt(podcastId: String): Long =
        withContext(Dispatchers.IO) {
            dao.getFeed(podcastId)?.feedUrlLookupAt ?: 0L
        }

    override suspend fun setUnsubscribedTtl(podcastId: String, ttlExpiresAt: Long?) =
        withContext(Dispatchers.IO) {
            if (dao.getFeed(podcastId) == null) return@withContext
            dao.setTtl(podcastId, ttlExpiresAt)
        }

    override suspend fun sweepExpired(nowMillis: Long) = withContext(Dispatchers.IO) {
        for (id in dao.listExpiredFeedIds(nowMillis)) {
            dao.deleteCatalog(id)
        }
    }

    private suspend fun refreshLocked(request: RefreshRequest): RefreshOutcome {
        if (request.podcastIndexId.isBlank() || request.podcastIndexId.startsWith("rss:")) {
            return RefreshOutcome.Failure(FEED_LOAD_FAILED_MESSAGE)
        }
        val existing = dao.getFeed(request.podcastIndexId)
        val url = resolveHttps(request.feedUrl, existing?.feedUrl)
            ?: return RefreshOutcome.Failure(FEED_LOAD_FAILED_MESSAGE)
        if (shouldSkipQuiet(existing)) {
            return RefreshOutcome.Unchanged(newest(request.podcastIndexId, request.meta))
        }
        if (existing != null &&
            isPublisherFeedUnchanged(request.podcastIndexId, url)
        ) {
            dao.upsertFeed(existing.copy(fetchedAt = System.currentTimeMillis()))
            return RefreshOutcome.Unchanged(newest(request.podcastIndexId, request.meta))
        }
        return fetchAndPersist(request, url, existing)
    }

    private suspend fun fetchAndPersist(
        request: RefreshRequest,
        url: String,
        existing: LocalEpisodeFeedEntity?,
    ): RefreshOutcome {
        return try {
            megaGetGate.withPermit {
                val fetched = feedClient.fetch(url)
                val rssNamespaceId = RssIdGenerator.podcastId(fetched.finalUrl)
                val parsed = feedClient.parse(
                    feedUrl = fetched.finalUrl,
                    bytes = fetched.body,
                    podcastId = rssNamespaceId,
                )
                val baseline =
                    if (existing == null || existing.needsFullBackfill) {
                        request.loadPiBaseline?.invoke()
                    } else {
                        null
                    }
                persistParsed(request, existing, fetched, rssNamespaceId, parsed, baseline)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            RefreshOutcome.Failure(FEED_LOAD_FAILED_MESSAGE)
        }
    }

    private suspend fun persistParsed(
        request: RefreshRequest,
        existing: LocalEpisodeFeedEntity?,
        fetched: RssFetchResult,
        rssNamespaceId: String,
        parsed: ParsedRssFeed,
        baseline: List<Episode>?,
    ): RefreshOutcome {
        val identities = dao.listIdentities(request.podcastIndexId)
        val rows = LocalEpisodeCatalogPersist.toLocalEpisodes(
            podcastIndexId = request.podcastIndexId,
            rssNamespaceId = rssNamespaceId,
            parsed = parsed.episodes,
            existing = identities,
            piBaseline = baseline,
            channelImageUrl = parsed.imageUrl,
            showImageUrl = request.meta.imageUrl,
        )
        if (rows.isEmpty()) {
            return RefreshOutcome.Failure(FEED_LOAD_FAILED_MESSAGE)
        }
        val copied = existing?.copiedExtrasCount ?: 0
        val feedOrder = existing?.feedOrder
            ?.takeIf { it != LocalFeedOrder.MIXED }
            ?: FeedOrderLogic.classify(parsed.episodes.map { it.publishedDate })
        val newestRow = rows.maxByOrNull { it.publishedDate }
        var storedCount = rows.size
        var ready = false
        runInTransaction {
            dao.upsertEpisodes(rows)
            storedCount = maxOf(dao.count(request.podcastIndexId), rows.size)
            val catalogIds = dao.listIdentities(request.podcastIndexId).map { it.episodeId }.toSet()
            val listenerIds = loadListenerEpisodeIds(request.podcastIndexId)
            val knownTip = loadKnownTip(request.podcastIndexId)
            ready =
                LocalCatalogReadyLogic.isReadyToFlip(
                    feedReady = storedCount >= copied,
                    catalogIds = catalogIds,
                    listenerIds = listenerIds,
                    existingTipId = knownTip?.first,
                    existingPublishedDate = knownTip?.second,
                    newTipId = newestRow?.episodeId,
                    newPublishedDate = newestRow?.publishedDate,
                )
            dao.upsertFeed(
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
            newest = newestRow?.toDomain(request.meta),
            itemCount = storedCount,
            ready = ready,
        )
    }

    private fun shouldSkipQuiet(existing: LocalEpisodeFeedEntity?): Boolean {
        if (existing == null) return false
        if (!existing.feedEtag.isNullOrBlank() || !existing.feedLastModified.isNullOrBlank()) {
            return false
        }
        if (existing.fetchedAt <= 0L) return false
        return System.currentTimeMillis() - existing.fetchedAt < QUIET_INTERVAL_MS
    }

    private suspend fun continuationRows(
        podcastId: String,
        sort: String,
        around: LocalEpisodeEntity,
        limit: Int,
    ): List<LocalEpisodeEntity> {
        val rest =
            if (sort == "oldest") {
                dao.getOlderThan(podcastId, around.publishedDate, around.episodeId, limit)
            } else {
                dao.getNewerThan(podcastId, around.publishedDate, around.episodeId, limit)
            }
        return listOf(around) + rest
    }

    private fun LocalEpisodeEntity.toDomain(meta: PodcastMeta): Episode =
        toEpisode(
            podcastTitle = meta.title,
            podcastImageUrl = meta.imageUrl,
            podcastGenre = meta.genre,
            podcastArtist = meta.artist,
        )

    companion object {
        const val FEED_LOAD_FAILED_MESSAGE = "Couldn't update episodes from the feed"
        const val QUIET_INTERVAL_MS = 6L * 60L * 60L * 1000L
        const val UNSUBSCRIBE_TTL_MS = 14L * 24L * 60L * 60L * 1000L
        const val FEED_URL_LOOKUP_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val MEGA_GET_PERMITS = 2

        fun create(
            database: BoxLoreDatabase,
            feedClient: RssFeedClient = RssFeedClient(),
            loadListenerEpisodeIds: suspend (String) -> Set<String> = { emptySet() },
            loadKnownTip: suspend (String) -> Pair<String, Long>? = { null },
        ): LocalEpisodeCatalogRepository =
            LocalEpisodeCatalogRepository(
                database = database,
                feedClient = feedClient,
                loadListenerEpisodeIds = loadListenerEpisodeIds,
                loadKnownTip = loadKnownTip,
            )

        internal fun resolveHttps(primary: String, fallback: String?): String? {
            val candidate = primary.trim().ifBlank { fallback.orEmpty() }
            return candidate.takeIf { it.startsWith("https://", ignoreCase = true) }
        }

        internal fun stubFeed(
            podcastId: String,
            feedUrl: String = "",
            feedUrlLookupAt: Long = 0L,
        ): LocalEpisodeFeedEntity = LocalEpisodeFeedEntity(
            podcastId = podcastId,
            feedUrl = feedUrl,
            feedEtag = null,
            feedLastModified = null,
            fetchedAt = 0L,
            itemCount = 0,
            feedOrder = LocalFeedOrder.MIXED,
            ttlExpiresAt = null,
            needsFullBackfill = true,
            copiedExtrasCount = 0,
            ready = false,
            feedUrlLookupAt = feedUrlLookupAt,
        )
    }
}
