package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.catalog.logic.DirectFeedSyncOrder
import cx.aswin.boxlore.core.catalog.logic.SubscriptionForegroundSyncLogic
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
 * Subscribe calls [requestCatalogIngest] so the first publisher-feed persist
 * survives leaving Podcast Info (cooldown does not apply). Each sync pass
 * sweeps local catalogs whose unsubscribe TTL has elapsed.
 *
 * Call [ensureStarted] from the composition root after onboarding; Home may also
 * call it. Library Subscriptions must call [requestRefresh] on appear; its pull to
 * refresh calls [refreshNow], which skips the cooldown.
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
    private val catalogIngestAction: suspend (String) -> Unit = {},
) {
    private val started = AtomicBoolean(false)
    private val foreground = MutableStateFlow(true)

    /** App lifecycle owns this gate; process survival must not authorize polling. */
    fun setForeground(active: Boolean) {
        foreground.value = active
    }
    private val syncInFlight = AtomicBoolean(false)

    /** Mirrors [syncInFlight] so [refreshNow] can wait for a pass it did not start. */
    private val passRunning = MutableStateFlow(false)
    private val lastCompletedAtMs = AtomicLong(SubscriptionForegroundSyncLogic.NEVER_COMPLETED_MS)
    private val catalogIngestFinishedMutable: MutableSharedFlow<String> =
        MutableSharedFlow(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** Emits a PI podcast id after a successful publisher-feed persist (not 304 skips). */
    val directFeedRefreshed: SharedFlow<String> = directFeedRefreshedMutable.asSharedFlow()

    /**
     * Emits after a [requestCatalogIngest] attempt finishes (success, skip, or
     * failure) so Podcast Info can remount without owning the GET.
     */
    val catalogIngestFinished: SharedFlow<String> = catalogIngestFinishedMutable.asSharedFlow()

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

    /**
     * Pull to refresh on New Episodes: runs a pass now even inside the cooldown and returns when
     * it is done. A pass that is already running is waited for instead of starting a second one.
     * The pass runs on [scope], so leaving the screen does not cancel it. Does nothing while the
     * app is in the background.
     */
    suspend fun refreshNow() {
        if (!foreground.value) return
        scope.launch { runSync(ignoreCooldown = true) }.join()
        passRunning.first { running -> !running }
    }

    /**
     * First local-catalog ingest after Subscribe. Runs on the application scope
     * so leaving Podcast Info cannot cancel persist. Ignores [requestRefresh]
     * cooldown / in-flight gates.
     */
    fun requestCatalogIngest(podcastId: String) {
        if (!SubscriptionForegroundSyncLogic.shouldRequestCatalogIngest(podcastId)) return
        preferFeedPodcast(podcastId)
        scope.launch {
            try {
                catalogIngestAction(podcastId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Post-subscribe catalog ingest failed for $podcastId", e)
            } finally {
                catalogIngestFinishedMutable.tryEmit(podcastId)
            }
        }
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
            if (periodicIntervalMs <= 0L) {
                if (initialDelayMs > 0L) delay(initialDelayMs)
                runSync()
                return@launch
            }
            runForegroundSyncLoop(initialDelayMs)
        }
    }

    private suspend fun runForegroundSyncLoop(initialDelayMs: Long) {
        var firstPass = true
        foreground.collectLatest { active ->
            if (!active) return@collectLatest
            if (firstPass && initialDelayMs > 0L) delay(initialDelayMs)
            firstPass = false
            runSync()
            while (true) {
                delay(periodicIntervalMs)
                runSync()
            }
        }
    }

    private suspend fun runSync(ignoreCooldown: Boolean = false) {
        if (!foreground.value) return
        val passCooldownMs = if (ignoreCooldown) 0L else cooldownMs
        if (
            SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                inFlight = syncInFlight.get(),
                lastCompletedAtMs = lastCompletedAtMs.get(),
                nowMs = nowMs(),
                cooldownMs = passCooldownMs,
            )
        ) {
            return
        }
        if (!syncInFlight.compareAndSet(false, true)) return
        passRunning.value = true
        try {
            if (
                SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                    inFlight = false,
                    lastCompletedAtMs = lastCompletedAtMs.get(),
                    nowMs = nowMs(),
                    cooldownMs = passCooldownMs,
                )
            ) {
                return
            }
            coroutineScope {
                val action = async { if (foreground.value) syncAction() }
                val watcher = launch {
                    foreground.first { !it }
                    action.cancel()
                }
                try {
                    action.await()
                } finally {
                    watcher.cancel()
                }
            }
            lastCompletedAtMs.set(nowMs())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Foreground subscription sync failed", e)
        } finally {
            syncInFlight.set(false)
            passRunning.value = false
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
        const val DEFAULT_FEED_CONCURRENCY = 2
        const val DEFAULT_PI_CONCURRENCY = 6

        /** Process-wide guard — AppRoot + Home may hold distinct instances after restarts. */
        private val processSyncStarted = AtomicBoolean(false)

        /** Test-only: clear process-once guard between JVM test cases. */
        internal fun resetProcessGuardForTests() {
            processSyncStarted.set(false)
        }

        @Suppress("LongParameterList", "kotlin:S107")
        fun create(
            podcastRepository: PodcastRepository,
            subscriptionRepository: SubscriptionRepository,
            episodeSupplementPort: EpisodeSupplementPort,
            scope: CoroutineScope,
            localEpisodeCatalog: LocalEpisodeCatalogPort? = null,
            initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
            feedNetworkDelayMs: Long = DEFAULT_FEED_NETWORK_DELAY_MS,
            chunkSize: Int = DEFAULT_CHUNK_SIZE,
            feedFixes: FeedFixToggles = FeedFixToggles(),
            addressUpkeep: FeedAddressUpkeep = FeedAddressUpkeep(),
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
                catalogIngestAction =
                SubscriptionForegroundSyncIngest.catalogIngestAction(
                    podcastRepository = podcastRepository,
                    subscriptionRepository = subscriptionRepository,
                    localEpisodeCatalog = localEpisodeCatalog,
                    onFeedRefreshed = { refreshed.tryEmit(it) },
                ),
                syncAction =
                subscribedSyncAction(
                    podcastRepository = podcastRepository,
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = episodeSupplementPort,
                    localEpisodeCatalog = localEpisodeCatalog,
                    preferred = preferred,
                    refreshed = refreshed,
                    feedNetworkDelayMs = feedNetworkDelayMs,
                    chunkSize = chunkSize,
                    feedFixes = feedFixes,
                    addressJobs =
                    addressJobs(podcastRepository, subscriptionRepository, addressUpkeep, DirectFeedRecovery.addressRead(subscriptionRepository, localEpisodeCatalog)),
                ),
            )
        }

        /** The daily address check and broken-feed reports of followed shows (P4 PR 14), behind `address-check-off`. */
        private fun addressJobs(
            podcastRepository: PodcastRepository,
            subscriptionRepository: SubscriptionRepository,
            upkeep: FeedAddressUpkeep,
            readsAt: suspend (podcastId: String, feedUrl: String) -> Boolean,
        ): FeedAddressJobs {
            val enabled = podcastRepository.rolloutSwitches.addressCheck
            return FeedAddressJobs(
                dailyCheck =
                FeedAddressDailyCheck(
                    followedShows = subscriptionRepository::followedShowRows,
                    fetchAddresses = { ids -> FeedAddressDailyCheck.fetchWith(podcastRepository.apiV2, ids) },
                    readsAt = readsAt,
                    replaceAddress = subscriptionRepository::updateFeedAddress,
                    markChecked = subscriptionRepository::markFeedAddressChecked,
                    enabled = enabled,
                    onMerged = { oldId, newId -> subscriptionRepository.renumberedFollows.adoptKnown(oldId, confirmedNewId = newId) },
                ),
                reporter =
                FeedReporter(
                    send = { request -> FeedReporter.sendWith(podcastRepository.apiV2, request) },
                    limiter = upkeep.reportLimiter,
                    isOnline = upkeep.isOnline,
                    enabled = enabled,
                ),
            )
        }

        @Suppress("LongParameterList", "kotlin:S107")
        private fun subscribedSyncAction(
            podcastRepository: PodcastRepository,
            subscriptionRepository: SubscriptionRepository,
            episodeSupplementPort: EpisodeSupplementPort,
            localEpisodeCatalog: LocalEpisodeCatalogPort?,
            preferred: AtomicReference<String?>,
            refreshed: MutableSharedFlow<String>,
            feedNetworkDelayMs: Long,
            chunkSize: Int,
            feedFixes: FeedFixToggles,
            addressJobs: FeedAddressJobs,
        ): suspend () -> Unit {
            val refreshBatch = DirectFeedRefreshBatch()
            return {
            SubscriptionForegroundSyncIngest.sweepExpiredLocalCatalogs(localEpisodeCatalog)
            runDailyAddressCheck(addressJobs.dailyCheck, subscriptionRepository)
            // One follow per renumbered show before feeds are read (RenumberedShowFollows.collapseFollowsQuietly).
            subscriptionRepository.renumberedFollows.collapseFollowsQuietly(podcastRepository::getPodcastDetails)
            val ids = subscriptionRepository.subscribedPodcastIds.first()
            val recoveryNow = System.currentTimeMillis()
            recoverMissingFeedUrls(
                ids = ids,
                recoverOne = { id ->
                    recoverOneMissingFeedUrl(
                        id = id,
                        now = recoveryNow,
                        subscriptionRepository = subscriptionRepository,
                        podcastRepository = podcastRepository,
                        localEpisodeCatalog = localEpisodeCatalog,
                    )
                },
            )
            syncSubscribedLatestEpisodes(
                loadIds = { ids },
                loadPodcastMeta = { id -> tipMeta(subscriptionRepository, id) },
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
                            followMoves = feedFixes.movedFeed(),
                            adoptMovedFeed = { readFrom, url -> subscriptionRepository.adoptMovedFeed(id, readFrom, url) },
                        )
                    },
                    saveDirectFeedLatest = { id, episode ->
                        subscriptionRepository.updateLatestEpisode(
                            podcastId = id,
                            episode = episode,
                            markAsNew = false,
                            publisherFeedAuthoritative = true,
                        )
                    },
                    feedNetworkDelayMs = feedNetworkDelayMs,
                    feedConcurrency = DEFAULT_FEED_CONCURRENCY,
                    preferredPodcastId = { preferred.get() },
                    selectRefreshBatch = { candidates ->
                        refreshBatch.select(candidates, preferred.get()) { id ->
                            val feed = subscriptionRepository.getPodcastEntity(id)?.feedUrl
                            feed != null && (localEpisodeCatalog?.isRefreshDue(id, feed, System.currentTimeMillis()) != false)
                        }
                    },
                    onFeedRefreshed = { refreshed.tryEmit(it) },
                    recheckAddress = DirectFeedRecovery.addressRecheck(feedFixes, subscriptionRepository, podcastRepository),
                    reportFeed = { id, status ->
                        addressJobs.reporter.report(id, status, subscriptionRepository.getPodcastEntity(id)?.feedUrl)
                    },
                    saveAddress = { id, candidate ->
                        subscriptionRepository.updateFeedAddress(candidate.change(id, System.currentTimeMillis()))
                    },
                    syncFallbackEnabled = feedFixes.syncFallback,
                ),
            )
            syncTrackedFeedUrlsForHttpsNotifications(
                ids = ids,
                subscriptionRepository = subscriptionRepository,
            )
            }
        }

        private suspend fun tipMeta(subscriptionRepository: SubscriptionRepository, id: String): DirectFeedTipMeta? =
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

        /**
         * Runs the daily address check before the pass reads feeds, so a swapped address is read at
         * once, after asking again for notification registrations still waiting for Firebase.
         */
        private suspend fun runDailyAddressCheck(dailyCheck: FeedAddressDailyCheck, subscriptionRepository: SubscriptionRepository) {
            try {
                subscriptionRepository.feedAddresses.resumePendingRegistrations()
                dailyCheck.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Daily address check failed", e)
            }
        }

        @Suppress("LongParameterList")
        private suspend fun resolveLocalCatalogTip(
            podcastId: String,
            meta: DirectFeedTipMeta,
            localEpisodeCatalog: LocalEpisodeCatalogPort?,
            episodeSupplementPort: EpisodeSupplementPort,
            podcastRepository: PodcastRepository,
            followMoves: Boolean,
            adoptMovedFeed: suspend (readFrom: String, movedTo: String) -> Unit,
        ): DirectFeedResolveResult {
            val catalog = localEpisodeCatalog
            if (catalog != null) {
                return resolveCatalogRefresh(podcastId, meta, catalog, followMoves, adoptMovedFeed)
            }
            return resolveLegacySupplementTip(podcastId, meta, episodeSupplementPort, podcastRepository)
        }

        /**
         * With [followMoves], a feed that moved for good ([LocalEpisodeCatalogPort.RefreshOutcome.Success.movedTo])
         * has its new address saved through [adoptMovedFeed] (P4a); never for `rss:` shows, whose
         * address is their identity and notification consent.
         */
        internal suspend fun resolveCatalogRefresh(
            podcastId: String,
            meta: DirectFeedTipMeta,
            catalog: LocalEpisodeCatalogPort,
            followMoves: Boolean = false,
            adoptMovedFeed: suspend (readFrom: String, movedTo: String) -> Unit = { _, _ -> },
        ): DirectFeedResolveResult {
            val podcastMeta = catalogMeta(meta)
            if (catalog.isPublisherFeedUnchanged(podcastId, meta.feedUrl.orEmpty())) {
                return DirectFeedResolveResult(
                    tip = catalog.newest(podcastId, podcastMeta),
                    persisted = false,
                )
            }
            return when (
                val outcome =
                    catalog.refresh(
                        LocalEpisodeCatalogPort.RefreshRequest(
                            podcastIndexId = podcastId,
                            feedUrl = meta.feedUrl.orEmpty(),
                            meta = podcastMeta,
                            detectMove = followMoves && !podcastId.startsWith("rss:"),
                        ),
                    )
            ) {
                is LocalEpisodeCatalogPort.RefreshOutcome.Success -> {
                    outcome.movedTo?.takeIf { followMoves && !podcastId.startsWith("rss:") }?.let { moved ->
                        Log.i(TAG, "Feed for $podcastId moved; saving its new address")
                        adoptMovedFeed(meta.feedUrl.orEmpty().trim(), moved)
                    }
                    DirectFeedResolveResult(tip = outcome.newest, persisted = true)
                }
                is LocalEpisodeCatalogPort.RefreshOutcome.Unchanged ->
                    DirectFeedResolveResult(tip = outcome.newest, persisted = false)
                is LocalEpisodeCatalogPort.RefreshOutcome.Failure -> {
                    Log.w(TAG, "Local catalog refresh failed for $podcastId: ${outcome.message} (gone=${outcome.addressGone})")
                    DirectFeedResolveResult(
                        tip = null,
                        persisted = false,
                        failed = true,
                        addressGone = outcome.addressGone,
                        reportStatus = outcome.reportStatus,
                    )
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
                            loadBaseline = { podcastRepository.loadPiEpisodesForSupplement(podcastId) },
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
                    DirectFeedResolveResult(tip = null, persisted = false, failed = true)
                EpisodeSupplementOutcome.NoDisconnect ->
                    DirectFeedResolveResult(tip = null, persisted = false)
            }
        }

        private fun catalogMeta(meta: DirectFeedTipMeta) = LocalEpisodeCatalogPort.PodcastMeta(
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
                    id in optedIn
                }
            val piSyncIds =
                currentSubs.filter { id ->
                    !id.startsWith("rss:") && id !in readyIds && id !in optedIn
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

            DirectFeedRecovery.recover(
                failed = syncDirectFeedNetwork(feedTipIds, loadPodcastMeta, saveFeed, directFeed),
                directFeed = directFeed,
                readAgain = { id, url ->
                    syncOneDirectFeedTip(id, { loadPodcastMeta(it)?.copy(feedUrl = url) }, directFeed.resolveFeedTip, saveFeed, directFeed.onFeedRefreshed)
                },
                syncFromServer = { ids -> syncPiChunks(ids, chunkSize, syncChunk, saveLatest) },
            )
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
                val gate = Semaphore(DEFAULT_PI_CONCURRENCY)
                chunks
                    .map { chunk ->
                        async {
                            gate.withPermit { syncOneChunk(chunk, syncChunk, saveLatest) }
                        }
                    }.awaitAll()
            }
        }

        /** Reads each due show's own feed; returns the shows whose read failed, with their results. */
        private suspend fun syncDirectFeedNetwork(
            feedTipIds: List<String>,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta?,
            saveFeed: suspend (String, Episode) -> Unit,
            directFeed: DirectFeedSyncSeams,
        ): Map<String, DirectFeedResolveResult> {
            if (feedTipIds.isEmpty()) return emptyMap()
            val ordered =
                directFeed.selectRefreshBatch(DirectFeedSyncOrder.prioritize(feedTipIds, directFeed.preferredPodcastId()))
            val gate = Semaphore(directFeed.feedConcurrency.coerceAtLeast(1))
            return coroutineScope {
                ordered
                    .map { id ->
                        async {
                            gate.withPermit {
                                id to
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
                    .mapNotNull { (id, result) -> result?.takeIf { it.failed }?.let { id to it } }
                    .toMap()
            }
        }

        /**
         * Heal: opted-in shows that already have notifications on get
         * `feedUrl` on RTDB so the checker can poll RSS without a notification toggle.
         */
        private suspend fun syncTrackedFeedUrlsForHttpsNotifications(ids: Set<String>, subscriptionRepository: SubscriptionRepository,) {
            try {
                ids
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

        internal suspend fun recoverMissingFeedUrls(
            ids: Set<String>,
            concurrency: Int = DEFAULT_PI_CONCURRENCY,
            recoverOne: suspend (id: String) -> Unit,
        ) {
            val targets = ids.filter { !it.startsWith("rss:") }
            if (targets.isEmpty()) return
            val gate = Semaphore(concurrency.coerceAtLeast(1))
            coroutineScope {
                targets
                    .map { id ->
                        async {
                            gate.withPermit {
                                try {
                                    recoverOne(id)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e(TAG, "Feed URL recovery failed for $id", e)
                                }
                            }
                        }
                    }.awaitAll()
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

        private suspend fun httpsSubscribedIds(ids: Set<String>, subscriptionRepository: SubscriptionRepository,): Set<String> = ids
            .filter { id ->
                TrackedPodcastRtdbLogic.httpsFeedUrl(
                        subscriptionRepository.getPodcastEntity(id)?.feedUrl,
                    ) != null
            }.toSet()

        private suspend fun readyCatalogIds(ids: Set<String>, localEpisodeCatalog: LocalEpisodeCatalogPort?,): Set<String> {
            val catalog = localEpisodeCatalog ?: return emptySet()
            return ids.filter { id -> catalog.isReady(id) }.toSet()
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

        /** Reads one show's own feed and saves its tip; null when there was nothing to read. */
        private suspend fun syncOneDirectFeedTip(
            podcastId: String,
            loadPodcastMeta: suspend (String) -> DirectFeedTipMeta?,
            resolveFeedTip: suspend (String, DirectFeedTipMeta) -> DirectFeedResolveResult,
            saveLatest: suspend (String, Episode) -> Unit,
            onFeedRefreshed: (String) -> Unit,
        ): DirectFeedResolveResult? {
            try {
                val meta = loadPodcastMeta(podcastId)
                if (meta == null) {
                    Log.w(TAG, "No Room row for opted-in $podcastId; skipping feed tip")
                    return null
                }
                val storedUrl = meta.feedUrl?.trim().orEmpty()
                if (storedUrl.isEmpty()) {
                    Log.d(
                        TAG,
                        "No Room feedUrl for opted-in $podcastId; port may still use the stored supplement URL",
                    )
                }
                val resolved = DirectFeedRecovery.withReadAddress(resolveFeedTip(podcastId, meta), meta.feedUrl)
                if (resolved.persisted) {
                    onFeedRefreshed(podcastId)
                }
                val tip = resolved.tip
                if (tip != null) {
                    val known = meta.knownTip
                    if (known != null && known.id == tip.id && known.publishedDate == tip.publishedDate) {
                        Log.d(TAG, "Network tip unchanged for $podcastId")
                        return resolved
                    }
                    saveLatest(podcastId, tip)
                    Log.d(TAG, "Saved direct-feed tip for $podcastId")
                } else {
                    Log.w(TAG, "Direct-feed tip empty for $podcastId")
                }
                return resolved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Direct-feed tip sync failed for $podcastId", e)
                return DirectFeedResolveResult(tip = null, persisted = false, failed = true)
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

/**
 * Result of an opted-in publisher-feed resolve (tip plus whether extras were persisted).
 * [failed]: the feed couldn't be read this pass; [addressGone]: and its saved address looks dead
 * ([LocalEpisodeCatalogPort.RefreshOutcome.Failure.addressGone]); [reportStatus]: what the read saw,
 * for `/v2/feed-report` ([LocalEpisodeCatalogPort.RefreshOutcome.Failure.reportStatus]);
 * [readFeedUrl]: the saved address the failed read used, the only one a recovered address may replace.
 */
internal data class DirectFeedResolveResult(
    val tip: Episode?,
    val persisted: Boolean,
    val failed: Boolean = false,
    val addressGone: Boolean = false,
    val reportStatus: String? = null,
    val readFeedUrl: String? = null,
)

/**
 * Off switches for the feed fixes in this sync, from the app's PostHog kill switches (each fix
 * stays on without an answer). [addressRecheck]: re-check a dead saved address; [syncFallback]:
 * ask `/sync` for shows whose own feed failed; [movedFeed]: follow a feed that moved for good.
 */
data class FeedFixToggles(
    val addressRecheck: () -> Boolean = { true },
    val syncFallback: () -> Boolean = { true },
    /** Adopt a feed's confirmed new address (permanent redirects, `itunes:new-feed-url`). */
    val movedFeed: () -> Boolean = { true },
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
    val selectRefreshBatch: suspend (List<String>) -> List<String> = { it },
    /** Re-checks a show's dead saved address: a different address to try, or null. */
    val recheckAddress: suspend (String) -> String? = { null },
    /**
     * Reports a failed read to the API (`/v2/feed-report`); returns the API's HTTPS address when it
     * differs from the saved one. Null status: no read happened (P4 PR 14).
     */
    val reportFeed: suspend (id: String, status: String?) -> String? = { _, _ -> null },
    /** Saves an address that [reportFeed] or [recheckAddress] offered and that then read successfully. */
    val saveAddress: suspend (id: String, candidate: AddressCandidate) -> Unit = { _, _ -> },
    /** Whether shows whose own feed failed this pass get their latest episode from `/sync`. */
    val syncFallbackEnabled: () -> Boolean = { true },
)

/**
 * What the daily address check and broken-feed reports need from the app (P4 PR 14): where report
 * times are kept across launches, and whether the phone is online (reports are never sent offline).
 * [reportLimiter] is the app's one limiter, shared with the show page (P4 PR 16) so both stay inside
 * one daily limit; the default builds one on [reportStamps].
 */
data class FeedAddressUpkeep(
    val reportStamps: FeedReportStamps = MemoryFeedReportStamps(),
    val isOnline: () -> Boolean = { true },
    val reportLimiter: FeedReportLimiter = FeedReportLimiter(reportStamps),
)

/** The daily address check and the broken-feed reporter of one [SubscriptionForegroundSync] (P4 PR 14). */
internal data class FeedAddressJobs(
    val dailyCheck: FeedAddressDailyCheck,
    val reporter: FeedReporter,
)
