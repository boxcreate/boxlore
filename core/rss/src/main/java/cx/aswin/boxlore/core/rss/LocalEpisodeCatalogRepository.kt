package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * First-class local episode catalog for subscribed Podcast Index shows.
 *
 * Sticky upsert by guid. Never remints. Never treats a prefix parse as the archive.
 * Reads live on [LocalEpisodeCatalogReads]; refresh lives in [refreshLocalCatalogLocked].
 */
class LocalEpisodeCatalogRepository private constructor(
    private val reads: LocalEpisodeCatalogReads,
    private val refreshDeps: LocalCatalogRefreshDeps,
) : LocalEpisodeCatalogPort by reads {
    constructor(
        database: BoxLoreDatabase,
        feedClient: RssFeedClient = RssFeedClient(),
        downloadCacheRelinker: DownloadCacheRelinker = DownloadCacheRelinker { _, _ -> false },
    ) : this(
        dao = database.localEpisodeCatalogDao(),
        feedClient = feedClient,
        runInTransaction = { block -> database.withTransaction { block() } },
        isFeedUnchanged = { url, etag, lastModified ->
            feedClient.confirmUnchanged(url, etag, lastModified)
        },
        reconcileListenerState =
        LocalCatalogListenerStateReconciler(
            database = database,
            downloadCacheRelinker = downloadCacheRelinker,
        )::reconcile,
        megaGetGate = Semaphore(MEGA_GET_PERMITS),
    )

    internal constructor(
        dao: LocalEpisodeCatalogDao,
        feedClient: RssFeedClient,
        runInTransaction: suspend (suspend () -> Unit) -> Unit,
        isFeedUnchanged: suspend (url: String, etag: String?, lastModified: String?) -> Boolean,
        reconcileListenerState: suspend (
            podcastId: String,
            catalogRows: List<LocalEpisodeEntity>,
        ) -> Unit,
        megaGetGate: Semaphore,
    ) : this(
        reads = LocalEpisodeCatalogReads(dao, isFeedUnchanged),
        refreshDeps =
        LocalCatalogRefreshDeps(
            dao = dao,
            feedClient = feedClient,
            runInTransaction = runInTransaction,
            isFeedUnchanged = isFeedUnchanged,
            reconcileListenerState = reconcileListenerState,
            megaGetGate = megaGetGate,
        ),
    )

    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun refresh(request: RefreshRequest): RefreshOutcome = withContext(Dispatchers.IO) {
        val lock = refreshLocks.getOrPut(request.podcastIndexId) { Mutex() }
        lock.withLock { refreshLocalCatalogLocked(refreshDeps, request) }
    }

    companion object {
        const val FEED_LOAD_FAILED_MESSAGE = "Couldn't update episodes from the feed"
        const val QUIET_INTERVAL_MS = 6L * 60L * 60L * 1000L
        const val UNSUBSCRIBE_TTL_MS = 14L * 24L * 60L * 60L * 1000L
        const val FEED_URL_LOOKUP_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val MEGA_GET_PERMITS = 2

        fun create(
            database: BoxLoreDatabase,
            feedClient: RssFeedClient = RssFeedClient(),
            downloadCacheRelinker: DownloadCacheRelinker = DownloadCacheRelinker { _, _ -> false },
        ): LocalEpisodeCatalogRepository = LocalEpisodeCatalogRepository(
            database = database,
            feedClient = feedClient,
            downloadCacheRelinker = downloadCacheRelinker,
        )

        internal fun resolveHttps(primary: String, fallback: String?,): String? {
            val candidate = primary.trim().ifBlank { fallback.orEmpty() }
            return candidate.takeIf { it.startsWith("https://", ignoreCase = true) }
        }

        internal fun stubFeed(podcastId: String, feedUrl: String = "", feedUrlLookupAt: Long = 0L,): LocalEpisodeFeedEntity = LocalEpisodeFeedEntity(
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
