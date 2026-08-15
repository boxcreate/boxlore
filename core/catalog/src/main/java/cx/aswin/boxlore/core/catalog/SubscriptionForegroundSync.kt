package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.catalog.logic.DirectFeedSyncOrder
import cx.aswin.boxlore.core.catalog.logic.SubscriptionForegroundSyncLogic
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground refresh of subscribed shows' latest episodes via
 * [PodcastRepository.syncSubscriptions], with direct-feed tip refresh for shows
 * the user opted into via Podcast Info "Missing episodes?".
 *
 * Cold start: [ensureStarted] waits [DEFAULT_INITIAL_DELAY_MS] so Home first paint
 * is not competing with `/sync`. [requestRefresh] has no extra delay so
 * Subscriptions-first launches and returning to Library fetch immediately instead
 * of showing Room cache only. Later passes are coalesced while one is in flight
 * and skipped during [DEFAULT_REFRESH_COOLDOWN_MS]. A periodic loop keeps a
 * long-lived process from going stale.
 *
 * Feed refreshes are capped at [DEFAULT_FEED_CONCURRENCY], skip work on an
 * unchanged ETag, and still rematch against a 1000-episode PI-only baseline when
 * the feed changed. Home may [preferFeedPodcast] so the open chip is first.
 *
 * Call [ensureStarted] from the composition root after onboarding; Home may also
 * call it. Library Subscriptions must call [requestRefresh] on appear.
 */
class SubscriptionForegroundSync(
    private val scope: CoroutineScope,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val syncAction: suspend () -> Unit,
    private val preferredFeedPodcastId: AtomicReference<String?> = AtomicReference(null),
    private val directFeedRefreshedMutable: MutableSharedFlow<String> =
        MutableSharedFlow(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        ),
    private val cooldownMs: Long = DEFAULT_REFRESH_COOLDOWN_MS,
    private val periodicIntervalMs: Long = DEFAULT_PERIODIC_INTERVAL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val started = AtomicBoolean(false)
    private val syncInFlight = AtomicBoolean(false)
    private val lastCompletedAtMs = AtomicLong(SubscriptionForegroundSyncLogic.NEVER_COMPLETED_MS)

    /** Emits a PI podcast id after a successful publisher-feed persist (not 304 skips). */
    val directFeedRefreshed: SharedFlow<String> = directFeedRefreshedMutable.asSharedFlow()

    /** Prefer this opted-in id when ordering the feed-refresh queue (open Home chip). */
    fun preferFeedPodcast(id: String?) {
        preferredFeedPodcastId.set(id)
    }

    /** Starts the delayed sync loop at most once per process (Home first-paint). */
    fun ensureStarted() {
        startSyncLoop(initialDelayMs)
    }

    /**
     * Fetches latest episodes now (cooldown / in-flight aware). Used when
     * Subscriptions is on screen — including open-app-to Subscriptions — so the
     * New Episodes tab is not Room cache from a previous session only.
     */
    fun requestRefresh() {
        if (started.get()) {
            scope.launch { runSync() }
            return
        }
        startSyncLoop(initialDelayMs = 0L)
    }

    /** Test seam: whether the sync loop has been claimed. */
    internal fun hasStarted(): Boolean = started.get()

    private fun startSyncLoop(initialDelayMs: Long) {
        if (!started.compareAndSet(false, true)) return
        if (!processSyncStarted.compareAndSet(false, true)) {
            scope.launch { runSync() }
            return
        }
        scope.launch {
            if (initialDelayMs > 0L) delay(initialDelayMs)
            runSync()
            if (periodicIntervalMs <= 0L) return@launch
            while (true) {
                delay(periodicIntervalMs)
                runSync()
            }
        }
    }

    private suspend fun runSync() {
        if (
            SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                inFlight = syncInFlight.get(),
                lastCompletedAtMs = lastCompletedAtMs.get(),
                nowMs = nowMs(),
                cooldownMs = cooldownMs,
            )
        ) {
            return
        }
        if (!syncInFlight.compareAndSet(false, true)) return
        try {
            if (
                SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                    inFlight = false,
                    lastCompletedAtMs = lastCompletedAtMs.get(),
                    nowMs = nowMs(),
                    cooldownMs = cooldownMs,
                )
            ) {
                return
            }
            syncAction()
            lastCompletedAtMs.set(nowMs())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Foreground subscription sync failed", e)
        } finally {
            syncInFlight.set(false)
        }
    }

    @Suppress("TooManyFunctions")
    companion object {
        private const val TAG = "SubscriptionForegroundSync"
        const val DEFAULT_INITIAL_DELAY_MS = 2000L

        /** Skip a second `/sync` if one finished inside this window. */
        const val DEFAULT_REFRESH_COOLDOWN_MS = 5 * 60 * 1000L

        /** Keep a warm process from going stale while the app stays in memory. */
        const val DEFAULT_PERIODIC_INTERVAL_MS = 15 * 60 * 1000L

        /** Kept for tests; production launch sync no longer waits after PI `/sync`. */
        const val DEFAULT_FEED_NETWORK_DELAY_MS = 0L
        const val DEFAULT_CHUNK_SIZE = 10
        const val DEFAULT_FEED_CONCURRENCY = 6

        /** Same PI page size Podcast Info uses when matching feed-only extras. */
        const val DIRECT_FEED_BASELINE_LIMIT = 1000

        /** Process-wide guard — AppRoot + Home may hold distinct instances after restarts. */
        private val processSyncStarted = AtomicBoolean(false)

        /** Test-only: clear process-once guard between JVM test cases. */
        internal fun resetProcessGuardForTests() {
            processSyncStarted.set(false)
        }

        @Suppress("LongParameterList")
        fun create(
            podcastRepository: PodcastRepository,
            subscriptionRepository: SubscriptionRepository,
            episodeSupplementPort: EpisodeSupplementPort,
            scope: CoroutineScope,
            localEpisodeCatalog: LocalEpisodeCatalogPort? = null,
            initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
            feedNetworkDelayMs: Long = DEFAULT_FEED_NETWORK_DELAY_MS,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
        ): SubscriptionForegroundSync {
            val preferred = AtomicReference<String?>(null)
            val refreshed =
                MutableSharedFlow<String>(
                    extraBufferCapacity = 64,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            return SubscriptionForegroundSync(
                scope = scope,
                initialDelayMs = initialDelayMs,
                preferredFeedPodcastId = preferred,
                directFeedRefreshedMutable = refreshed,
                syncAction = {
                    val ids = subscriptionRepository.subscribedPodcastIds.first()
                    recoverMissingFeedUrls(
                        ids = ids,
                        subscriptionRepository = subscriptionRepository,
                        podcastRepository = podcastRepository,
                        localEpisodeCatalog = localEpisodeCatalog,
                    )
                    syncSubscribedLatestEpisodes(
                        loadIds = { ids },
                        loadPodcastMeta = { id ->
                            subscriptionRepository.getPodcastEntity(id)?.let { entity ->
                                DirectFeedTipMeta(
                                    feedUrl = entity.feedUrl,
                                    title = entity.title,
                                    imageUrl = entity.imageUrl,
                                    genre = entity.genre,
                                    artist = entity.author,
                                    knownTip = entity.latestEpisode,
                                )
                            }
                        },
                        syncChunk = { chunk -> podcastRepository.syncSubscriptions(chunk) },
                        saveLatest = { id, episode ->
                            subscriptionRepository.updateLatestEpisode(id, episode)
                        },
                        chunkSize = chunkSize,
                        directFeed =
                            DirectFeedSyncSeams(
                                loadOptedInIds = {
                                    httpsSubscribedIds(ids, subscriptionRepository)
                                },
                                loadReadyIds = {
                                    readyCatalogIds(ids, localEpisodeCatalog)
                                },
                                loadCachedFeedTip = { id ->
                                    localEpisodeCatalog?.newest(
                                        id,
                                        LocalEpisodeCatalogPort.PodcastMeta(),
                                    )
                                        ?: episodeSupplementPort
                                            .getEpisodesForPodcast(id)
                                            .maxByOrNull { it.publishedDate }
                                },
                                resolveFeedTip = { id, meta ->
                                    resolveLocalCatalogTip(
                                        podcastId = id,
                                        meta = meta,
                                        localEpisodeCatalog = localEpisodeCatalog,
                                        episodeSupplementPort = episodeSupplementPort,
                                        podcastRepository = podcastRepository,
                                    )
                                },
                                saveDirectFeedLatest = { id, episode ->
                                    subscriptionRepository.updateLatestEpisode(
                                        podcastId = id,
                                        episode = episode,
                                        markAsNew = false,
                                    )
                                },
                                feedNetworkDelayMs = feedNetworkDelayMs,
                                feedConcurrency = DEFAULT_FEED_CONCURRENCY,
                                preferredPodcastId = { preferred.get() },
                                onFeedRefreshed = { refreshed.tryEmit(it) },
                            ),
                    )
                    syncTrackedFeedUrlsForHttpsNotifications(
                        ids = ids,
                        subscriptionRepository = subscriptionRepository,
                    )
                },
            )
        }

        private suspend fun resolveLocalCatalogTip(
            podcastId: String,
            meta: DirectFeedTipMeta,
            localEpisodeCatalog: LocalEpisodeCatalogPort?,
            episodeSupplementPort: EpisodeSupplementPort,
            podcastRepository: PodcastRepository,
        ): DirectFeedResolveResult {
            val catalog = localEpisodeCatalog
            if (catalog != null) {
                return resolveCatalogRefresh(podcastId, meta, catalog, podcastRepository)
            }
            return resolveLegacySupplementTip(podcastId, meta, episodeSupplementPort, podcastRepository)
        }

        private suspend fun resolveCatalogRefresh(
            podcastId: String,
            meta: DirectFeedTipMeta,
            catalog: LocalEpisodeCatalogPort,
            podcastRepository: PodcastRepository,
        ): DirectFeedResolveResult {
            val podcastMeta = catalogMeta(meta)
            if (catalog.isPublisherFeedUnchanged(podcastId, meta.feedUrl.orEmpty())) {
                return DirectFeedResolveResult(
                    tip = catalog.newest(podcastId, podcastMeta),
                    persisted = false,
                )
            }
            val needsBaseline = !catalog.isReady(podcastId)
            return when (
                val outcome =
                    catalog.refresh(
                        LocalEpisodeCatalogPort.RefreshRequest(
                            podcastIndexId = podcastId,
                            feedUrl = meta.feedUrl.orEmpty(),
                            meta = podcastMeta,
                            loadPiBaseline =
                                if (needsBaseline) {
                                    {
                                        podcastRepository.loadPiEpisodesForBaseline(
                                            feedId = podcastId,
                                            limit = DIRECT_FEED_BASELINE_LIMIT,
                                        )
                                    }
                                } else {
                                    null
                                },
                        ),
                    )
            ) {
                is LocalEpisodeCatalogPort.RefreshOutcome.Success ->
                    DirectFeedResolveResult(tip = outcome.newest, persisted = true)
                is LocalEpisodeCatalogPort.RefreshOutcome.Unchanged ->
                    DirectFeedResolveResult(tip = outcome.newest, persisted = false)
                is LocalEpisodeCatalogPort.RefreshOutcome.Failure -> {
                    Log.w(TAG, "Local catalog refresh failed for $podcastId: ${outcome.message}")
                    DirectFeedResolveResult(tip = null, persisted = false)
                }
            }
        }

        private suspend fun resolveLegacySupplementTip(
            podcastId: String,
            meta: DirectFeedTipMeta,
            episodeSupplementPort: EpisodeSupplementPort,
            podcastRepository: PodcastRepository,
        ): DirectFeedResolveResult {
            if (episodeSupplementPort.isPublisherFeedUnchanged(podcastId, meta.feedUrl.orEmpty())) {
                return DirectFeedResolveResult(
                    tip =
                        episodeSupplementPort
                            .getEpisodesForPodcast(podcastId)
                            .maxByOrNull { it.publishedDate },
                    persisted = false,
                )
            }
            return when (
                val outcome =
                    episodeSupplementPort.refreshFromFeed(
                        EpisodeSupplementPort.RefreshFromFeedRequest(
                            podcastIndexId = podcastId,
                            feedUrl = meta.feedUrl.orEmpty(),
                            loadBaseline = {
                                podcastRepository.loadPiEpisodesForBaseline(
                                    feedId = podcastId,
                                    limit = DIRECT_FEED_BASELINE_LIMIT,
                                )
                            },
                            podcastTitle = meta.title,
                            podcastImageUrl = meta.imageUrl,
                            podcastGenre = meta.genre,
                            podcastArtist = meta.artist,
                        ),
                    )
            ) {
                is EpisodeSupplementOutcome.Success ->
                    DirectFeedResolveResult(tip = outcome.newestFeedEpisode, persisted = true)
                is EpisodeSupplementOutcome.Failure ->
                    DirectFeedResolveResult(tip = null, persisted = false)
                EpisodeSupplementOutcome.NoDisconnect ->
                    DirectFeedResolveResult(tip = null, persisted = false)
            }
        }

        private fun catalogMeta(meta: DirectFeedTipMeta) =
            LocalEpisodeCatalogPort.PodcastMeta(
                title = meta.title,
                imageUrl = meta.imageUrl,
                genre = meta.genre,
                artist = meta.artist,
            )

        /**
         * Chunked sync body (test seam).
         *
         * Order: PI `/sync` chunks in parallel → promote opted-in tips from **local**
         * supplement cache → optional delay → concurrent [EpisodeSupplementPort.refreshFromFeed]
         * (1000-oldest PI-only baseline) for opted-in so extras land in Room for Home chips.
         */
        internal suspend fun syncSubscribedLatestEpisodes(
            loadIds: suspend () -> Set<String>,
            syncChunk: suspend (List<String>) -> Map<String, Episode>,
            saveLatest: suspend (String, Episode) -> Unit,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta? = { null },
            directFeed: DirectFeedSyncSeams = DirectFeedSyncSeams(),
        ) {
            val currentSubs =
                try {
                    loadIds()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync failed totally", e)
                    return
                }
            if (currentSubs.isEmpty()) return

            val optedIn =
                try {
                    directFeed.loadOptedInIds()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load HTTPS feed ids; falling back to PI sync", e)
                    emptySet()
                }
            val readyIds =
                try {
                    directFeed.loadReadyIds()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptySet()
                }

            val feedTipIds =
                currentSubs.filter { id ->
                    !id.startsWith("rss:") && id in optedIn
                }
            val piSyncIds =
                currentSubs.filter { id ->
                    id !in readyIds && id !in optedIn
                }

            Log.d(
                TAG,
                "Starting background sync for ${currentSubs.size} subs " +
                    "(${feedTipIds.size} direct-feed, ${piSyncIds.size} PI) " +
                    "in chunks of $chunkSize",
            )

            syncPiChunks(piSyncIds, chunkSize, syncChunk, saveLatest)

            val saveFeed = directFeed.saveDirectFeedLatest ?: saveLatest
            for (id in feedTipIds) {
                promoteCachedDirectFeedTip(id, loadPodcastMeta, directFeed.loadCachedFeedTip, saveFeed)
            }

            if (feedTipIds.isNotEmpty() && directFeed.feedNetworkDelayMs > 0L) {
                delay(directFeed.feedNetworkDelayMs)
            }

            syncDirectFeedNetwork(feedTipIds, loadPodcastMeta, saveFeed, directFeed)
            Log.d(TAG, "Finished background sync for all ${currentSubs.size} subs")
        }

        private suspend fun syncPiChunks(
            piSyncIds: List<String>,
            chunkSize: Int,
            syncChunk: suspend (List<String>) -> Map<String, Episode>,
            saveLatest: suspend (String, Episode) -> Unit,
        ) {
            val chunks = piSyncIds.chunked(chunkSize)
            if (chunks.isEmpty()) return
            coroutineScope {
                val gate = Semaphore(DEFAULT_FEED_CONCURRENCY)
                chunks
                    .map { chunk ->
                        async {
                            gate.withPermit { syncOneChunk(chunk, syncChunk, saveLatest) }
                        }
                    }.awaitAll()
            }
        }

        private suspend fun syncDirectFeedNetwork(
            feedTipIds: List<String>,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta?,
            saveFeed: suspend (String, Episode) -> Unit,
            directFeed: DirectFeedSyncSeams,
        ) {
            if (feedTipIds.isEmpty()) return
            val ordered =
                DirectFeedSyncOrder.prioritize(feedTipIds, directFeed.preferredPodcastId())
            val gate = Semaphore(directFeed.feedConcurrency.coerceAtLeast(1))
            coroutineScope {
                ordered
                    .map { id ->
                        async {
                            gate.withPermit {
                                syncOneDirectFeedTip(
                                    podcastId = id,
                                    loadPodcastMeta = loadPodcastMeta,
                                    resolveFeedTip = directFeed.resolveFeedTip,
                                    saveLatest = saveFeed,
                                    onFeedRefreshed = directFeed.onFeedRefreshed,
                                )
                            }
                        }
                    }.awaitAll()
            }
        }

        /**
         * Heal: opted-in shows that already have notifications on get
         * `feedUrl` on RTDB so the checker can poll RSS without a notification toggle.
         */
        private suspend fun syncTrackedFeedUrlsForHttpsNotifications(
            ids: Set<String>,
            subscriptionRepository: SubscriptionRepository,
        ) {
            try {
                ids
                    .filter { !it.startsWith("rss:") }
                    .mapNotNull { subscriptionRepository.getPodcastEntity(it) }
                    .forEach { entity ->
                        subscriptionRepository.syncTrackedPodcastFeedUrl(entity.toPodcast())
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync tracked feed URLs for notifications", e)
            }
        }

        private suspend fun recoverMissingFeedUrls(
            ids: Set<String>,
            subscriptionRepository: SubscriptionRepository,
            podcastRepository: PodcastRepository,
            localEpisodeCatalog: LocalEpisodeCatalogPort?,
        ) {
            val now = System.currentTimeMillis()
            ids
                .filter { !it.startsWith("rss:") }
                .forEach { id ->
                    recoverOneMissingFeedUrl(
                        id = id,
                        now = now,
                        subscriptionRepository = subscriptionRepository,
                        podcastRepository = podcastRepository,
                        localEpisodeCatalog = localEpisodeCatalog,
                    )
                }
        }

        private suspend fun recoverOneMissingFeedUrl(
            id: String,
            now: Long,
            subscriptionRepository: SubscriptionRepository,
            podcastRepository: PodcastRepository,
            localEpisodeCatalog: LocalEpisodeCatalogPort?,
        ) {
            val entity = subscriptionRepository.getPodcastEntity(id) ?: return
            if (TrackedPodcastRtdbLogic.httpsFeedUrl(entity.feedUrl) != null) return
            val lastLookup = localEpisodeCatalog?.lastFeedUrlLookupAt(id) ?: 0L
            if (lastLookup > 0L &&
                now - lastLookup < LocalEpisodeCatalogRepository.FEED_URL_LOOKUP_INTERVAL_MS
            ) {
                return
            }
            localEpisodeCatalog?.markFeedUrlLookup(id, now)
            val details =
                try {
                    podcastRepository.getPodcastDetails(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            val https = TrackedPodcastRtdbLogic.httpsFeedUrl(details?.feedUrl) ?: return
            subscriptionRepository.ensureHttpsFeedUrl(id, https)
        }

        private suspend fun httpsSubscribedIds(
            ids: Set<String>,
            subscriptionRepository: SubscriptionRepository,
        ): Set<String> =
            ids
                .filter { id ->
                    !id.startsWith("rss:") &&
                        TrackedPodcastRtdbLogic.httpsFeedUrl(
                            subscriptionRepository.getPodcastEntity(id)?.feedUrl,
                        ) != null
                }.toSet()

        private suspend fun readyCatalogIds(
            ids: Set<String>,
            localEpisodeCatalog: LocalEpisodeCatalogPort?,
        ): Set<String> {
            val catalog = localEpisodeCatalog ?: return emptySet()
            return ids.filter { id -> !id.startsWith("rss:") && catalog.isReady(id) }.toSet()
        }

        private suspend fun promoteCachedDirectFeedTip(
            podcastId: String,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta?,
            loadCachedFeedTip: suspend (String) -> Episode?,
            saveLatest: suspend (String, Episode) -> Unit,
        ) {
            try {
                val meta = loadPodcastMeta(podcastId) ?: return
                val cached = loadCachedFeedTip(podcastId) ?: return
                val known = meta.knownTip
                val shouldPromote =
                    known == null || cached.publishedDate > known.publishedDate
                if (!shouldPromote) {
                    Log.d(TAG, "Cached tip already current for $podcastId")
                    return
                }
                saveLatest(podcastId, cached)
                Log.d(TAG, "Promoted cached direct-feed tip for $podcastId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cached direct-feed tip promote failed for $podcastId", e)
            }
        }

        private suspend fun syncOneDirectFeedTip(
            podcastId: String,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta?,
            resolveFeedTip: suspend (String, DirectFeedTipMeta) -> DirectFeedResolveResult,
            saveLatest: suspend (String, Episode) -> Unit,
            onFeedRefreshed: (String) -> Unit,
        ) {
            try {
                val meta = loadPodcastMeta(podcastId)
                if (meta == null) {
                    Log.w(TAG, "No Room row for opted-in $podcastId; skipping feed tip")
                    return
                }
                val storedUrl = meta.feedUrl?.trim().orEmpty()
                if (storedUrl.isEmpty()) {
                    Log.d(
                        TAG,
                        "No Room feedUrl for opted-in $podcastId; port may still use the stored supplement URL",
                    )
                }
                val resolved = resolveFeedTip(podcastId, meta)
                if (resolved.persisted) {
                    onFeedRefreshed(podcastId)
                }
                val tip = resolved.tip
                if (tip != null) {
                    val known = meta.knownTip
                    if (known != null && known.id == tip.id && known.publishedDate == tip.publishedDate) {
                        Log.d(TAG, "Network tip unchanged for $podcastId")
                        return
                    }
                    saveLatest(podcastId, tip)
                    Log.d(TAG, "Saved direct-feed tip for $podcastId")
                } else {
                    Log.w(TAG, "Direct-feed tip empty for $podcastId")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Direct-feed tip sync failed for $podcastId", e)
            }
        }

        private suspend fun syncOneChunk(
            chunk: List<String>,
            syncChunk: suspend (List<String>) -> Map<String, Episode>,
            saveLatest: suspend (String, Episode) -> Unit,
        ) {
            try {
                val synced = syncChunk(chunk)
                Log.d(TAG, "Successfully fetched chunk of ${chunk.size} subs, saving to DB...")
                for ((podId, episode) in synced) {
                    saveLatest(podId, episode)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Background sync chunk failed", e)
            }
        }
    }
}

/** Room fields needed to resolve a direct-feed library tip. */
internal data class DirectFeedTipMeta(
    val feedUrl: String?,
    val title: String?,
    val imageUrl: String?,
    val genre: String?,
    val artist: String?,
    val knownTip: Episode?,
)

/** Result of an opted-in publisher-feed resolve (tip plus whether extras were persisted). */
internal data class DirectFeedResolveResult(
    val tip: Episode?,
    val persisted: Boolean,
)

/** Direct-feed callbacks grouped so [SubscriptionForegroundSync.syncSubscribedLatestEpisodes] stays under the param limit. */
internal data class DirectFeedSyncSeams(
    val loadOptedInIds: suspend () -> Set<String> = { emptySet() },
    val loadReadyIds: suspend () -> Set<String> = { emptySet() },
    val loadCachedFeedTip: suspend (String) -> Episode? = { null },
    val resolveFeedTip: suspend (String, DirectFeedTipMeta) -> DirectFeedResolveResult =
        { _, _ -> DirectFeedResolveResult(tip = null, persisted = false) },
    val saveDirectFeedLatest: (suspend (String, Episode) -> Unit)? = null,
    val feedNetworkDelayMs: Long = 0L,
    val feedConcurrency: Int = SubscriptionForegroundSync.DEFAULT_FEED_CONCURRENCY,
    val preferredPodcastId: () -> String? = { null },
    val onFeedRefreshed: (String) -> Unit = {},
)
