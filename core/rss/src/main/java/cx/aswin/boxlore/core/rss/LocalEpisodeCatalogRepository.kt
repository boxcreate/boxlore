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
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
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
    private val onCatalogPersisted: suspend (String) -> Unit,
    private val renumberedLinker: RenumberedShowLinker? = null,
) : LocalEpisodeCatalogPort by reads {
    /**
     * @param renumberedShows shows Podcast Index renumbered; each catalog read of the show one now
     *   answers as links its listener rows to the show's episodes ([RenumberedShowLinker])
     */
    constructor(
        database: BoxLoreDatabase,
        feedClient: RssFeedClient = RssFeedClient(),
        downloadCacheRelinker: DownloadCacheRelinker = DownloadCacheRelinker { _, _ -> false },
        onCatalogPersisted: suspend (String) -> Unit = {},
        renumberedShows: RenumberedShows = RenumberedShows.NONE,
    ) : this(database, feedClient, downloadCacheRelinker, onCatalogPersisted, RecipeIdHash.RECIPE, renumberedShows)

    /** [recipeHash] is a test seam: collision tests force recipe clashes through real Room (P4 PR 9). */
    internal constructor(
        database: BoxLoreDatabase,
        feedClient: RssFeedClient,
        downloadCacheRelinker: DownloadCacheRelinker,
        onCatalogPersisted: suspend (String) -> Unit,
        recipeHash: RecipeIdHash,
        renumberedShows: RenumberedShows = RenumberedShows.NONE,
    ) : this(
        database = database,
        feedClient = feedClient,
        downloadCacheRelinker = downloadCacheRelinker,
        onCatalogPersisted = onCatalogPersisted,
        recipeHash = recipeHash,
        renumberedLinker = RenumberedShowLinker(database, renumberedShows),
    )

    @Suppress("LongParameterList")
    private constructor(
        database: BoxLoreDatabase,
        feedClient: RssFeedClient,
        downloadCacheRelinker: DownloadCacheRelinker,
        onCatalogPersisted: suspend (String) -> Unit,
        recipeHash: RecipeIdHash,
        renumberedLinker: RenumberedShowLinker,
    ) : this(
        reads = LocalEpisodeCatalogReads(database.localEpisodeCatalogDao()) { url, etag, lastModified ->
            feedClient.confirmUnchanged(url, etag, lastModified)
        },
        refreshDeps =
        LocalCatalogRefreshDeps(
            dao = database.localEpisodeCatalogDao(),
            feedClient = feedClient,
            runInTransaction = { block -> database.withTransaction { block() } },
            isFeedUnchanged = { url, etag, lastModified ->
                feedClient.confirmUnchanged(url, etag, lastModified)
            },
            reconcileListenerState = listenerStateLinks(LocalCatalogListenerStateReconciler(database, downloadCacheRelinker), renumberedLinker),
            megaGetGate = PublisherFeedRefreshGate.permits,
            identity = RoomCatalogIdentityStore(database),
            recipeHash = recipeHash,
        ),
        onCatalogPersisted = onCatalogPersisted,
        renumberedLinker = renumberedLinker,
    )

    @Suppress("LongParameterList")
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
        identity: CatalogIdentityStore,
        onCatalogPersisted: suspend (String) -> Unit = {},
        recipeHash: RecipeIdHash = RecipeIdHash.RECIPE,
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
            identity = identity,
            recipeHash = recipeHash,
        ),
        onCatalogPersisted = onCatalogPersisted,
    )

    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * The show page's stored feed copies, followed or not, written under the same per-show lock as
     * [refresh] ([ShowPageFeedCatalog]).
     *
     * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
     * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
     */
    val showPages: ShowPageFeedPort by lazy {
        ShowPageFeedCatalog(refreshDeps) { podcastId -> refreshLocks.getOrPut(podcastId) { Mutex() } }
    }

    override suspend fun refresh(request: RefreshRequest): RefreshOutcome = withContext(Dispatchers.IO) {
        val lock = refreshLocks.getOrPut(request.podcastIndexId) { Mutex() }
        val outcome = lock.withLock { refreshLocalCatalogLocked(refreshDeps, request) }
        val notifyPersisted = request.runPostPersistCallback && request.reason != LocalEpisodeCatalogPort.RefreshReason.AUTO_DOWNLOAD
        if (outcome is RefreshOutcome.Success && notifyPersisted && request.canProceed()) {
            try {
                onCatalogPersisted(request.podcastIndexId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("LocalEpisodeCatalog", "Post-persist scheduling failed", e)
            }
        }
        outcome
    }

    /**
     * Links [formerId]'s listener rows to [currentId]'s stored episodes now, once Podcast Index was
     * found to answer [formerId] as [currentId] ([RenumberedShowLinker]); each later catalog read of
     * [currentId] links again. Returns the number of IDs linked; 0 without the app's wiring.
     *
     * Added for renumbered shows (docs/EPISODE_IDS.md §5, renumbered shows).
     */
    suspend fun linkRenumberedShow(formerId: String, currentId: String): Int = withContext(Dispatchers.IO) {
        val linker = renumberedLinker ?: return@withContext 0
        refreshLocks.getOrPut(currentId) { Mutex() }.withLock { linker.linkInTransaction(formerId, currentId) }
    }

    override suspend fun isRefreshDue(podcastId: String, feedUrl: String, nowMillis: Long): Boolean {
        val existing = refreshDeps.dao.getFeed(podcastId)
        return existing?.feedUrl?.trim() != feedUrl.trim() || !shouldSkipQuiet(existing, nowMillis = nowMillis)
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
            onCatalogPersisted: suspend (String) -> Unit = {},
            renumberedShows: RenumberedShows = RenumberedShows.NONE,
        ): LocalEpisodeCatalogRepository = LocalEpisodeCatalogRepository(
            database = database,
            feedClient = feedClient,
            downloadCacheRelinker = downloadCacheRelinker,
            onCatalogPersisted = onCatalogPersisted,
            renumberedShows = renumberedShows,
        )

        /**
         * The listener-state step of a catalog read, in its transaction: the orphan rematch, then the
         * links of shows renumbered into the show read ([RenumberedShowLinker]).
         */
        private fun listenerStateLinks(
            reconciler: LocalCatalogListenerStateReconciler,
            renumberedLinker: RenumberedShowLinker,
        ): suspend (String, List<LocalEpisodeEntity>) -> Unit = { podcastId, rows ->
            reconciler.reconcile(podcastId, rows)
            renumberedLinker.linkFormerShows(podcastId)
        }

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
