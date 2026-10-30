package cx.aswin.boxlore

import android.content.Context
import cx.aswin.boxlore.connectivity.AndroidConnectivityObserver
import cx.aswin.boxlore.core.analytics.EpisodeRecipeIdLookup
import cx.aswin.boxlore.core.catalog.AddressCheckAnswer
import cx.aswin.boxlore.core.catalog.DiscoveryEpisodeIds
import cx.aswin.boxlore.core.catalog.DiscoveryRouting
import cx.aswin.boxlore.core.catalog.EpisodeAliasReads
import cx.aswin.boxlore.core.catalog.EpisodeSharePreparer
import cx.aswin.boxlore.core.catalog.EpisodeShareRegistrar
import cx.aswin.boxlore.core.catalog.FeedAddressUpkeep
import cx.aswin.boxlore.core.catalog.FeedAddressWiring
import cx.aswin.boxlore.core.catalog.FeedFirstWiring
import cx.aswin.boxlore.core.catalog.FeedFixToggles
import cx.aswin.boxlore.core.catalog.FeedMoveFollower
import cx.aswin.boxlore.core.catalog.FeedReportLimiter
import cx.aswin.boxlore.core.catalog.FileUpgradeNotificationMarker
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.InstallReferrerManager
import cx.aswin.boxlore.core.catalog.LegacyRssRepair
import cx.aswin.boxlore.core.catalog.LegacyRssRepairActivation
import cx.aswin.boxlore.core.catalog.LegacyRssRepairCatalog
import cx.aswin.boxlore.core.catalog.LegacyRssRepairRuntime
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.RenumberingWiring
import cx.aswin.boxlore.core.catalog.RolloutSwitches
import cx.aswin.boxlore.core.catalog.RoomDiscoveryEpisodeIds
import cx.aswin.boxlore.core.catalog.RoomEpisodeAliasReads
import cx.aswin.boxlore.core.catalog.RoomEpisodeOfflineLookup
import cx.aswin.boxlore.core.catalog.RoomFolderRepository
import cx.aswin.boxlore.core.catalog.RoomLocalCatalog
import cx.aswin.boxlore.core.catalog.SharedAppDependencies
import cx.aswin.boxlore.core.catalog.SharedPreferencesFeedReportStamps
import cx.aswin.boxlore.core.catalog.SharedPreferencesPendingNotificationRegistrations
import cx.aswin.boxlore.core.catalog.SharedPreferencesUpgradeRepairTally
import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.UpgradeAddressRepair
import cx.aswin.boxlore.core.catalog.UpgradeRepairPorts
import cx.aswin.boxlore.core.catalog.ports.SmartDownloadSyncPort
import cx.aswin.boxlore.core.catalog.privacy.ConsentManager
import cx.aswin.boxlore.core.catalog.sync.HistorySyncResolver
import cx.aswin.boxlore.core.catalog.sync.QueueSyncResolver
import cx.aswin.boxlore.core.catalog.sync.SubscriptionSyncResolver
import cx.aswin.boxlore.core.catalog.sync.SyncEpisodeIdentity
import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.database.knownRecipeIds
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.downloads.DownloadsDependencies
import cx.aswin.boxlore.core.downloads.SmartDownloadManager
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncher
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncherHolder
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.QueueManager
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.service.MediaDownloadService
import cx.aswin.boxlore.core.playback.synchronizeCastSession
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.rss.EpisodeSupplementRepository
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import cx.aswin.boxlore.core.rss.RenumberedShows
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.rss.SharedPreferencesRenumberedShows
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import cx.aswin.boxlore.fcm.PodcastNotificationRegistrationWorker
import cx.aswin.boxlore.share.EpisodeShareRegistrationWorker
import cx.aswin.boxlore.sync.AnalyticsFeedTelemetry
import cx.aswin.boxlore.sync.CloudSyncTriggerCoordinator
import cx.aswin.boxlore.sync.FeedFirstSwitch
import cx.aswin.boxlore.sync.FeedFixSwitches
import cx.aswin.boxlore.sync.RolloutFlagMemory
import cx.aswin.boxlore.sync.SavedKillSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped composition root for shared DB / repositories / managers.
 *
 * Construction order (invariant):
 * DB → RSS/ranking peers → PodcastRepository → QueueRepository → PlaybackRepository
 * → QueueManager → SmartDownloadManager.
 *
 * Ranking/RSS are [create]+[install]ed here (not via production getInstance call sites).
 * Workers/services consume the same instances via
 * [cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder] and
 * [cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder].
 */
class AppContainer(
    context: Context,
    apiBaseUrl: String,
    publicKey: String,
    /**
     * Optional pre-built prefs instance so [BoxLoreApplication] can keep a single
     * [UserPreferencesRepository] (theme cache / engagement) without a second DataStore client.
     */
    sharedUserPreferences: UserPreferencesRepository? = null,
    /** Process-scoped scope from [BoxLoreApplication] for foreground subscription sync. */
    applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    authRepositoryOverride: cx.aswin.boxlore.core.auth.AuthRepository? = null,
) : SharedAppDependencies,
    DownloadsDependencies {
    private val appContext = context.applicationContext

    private val syncScope = applicationScope
    val appUpdates by lazy { cx.aswin.boxlore.updates.createAppUpdates(appContext, syncScope) }
    private val legacyRssRepairLaunchDecision =
        runCatching {
            @Suppress("DEPRECATION")
            val packageInfo =
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    0,
                )
            LegacyRssRepairLaunchGate.evaluate(
                versionName = BuildConfig.VERSION_NAME,
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime,
            )
        }.getOrDefault(LegacyRssRepairLaunchDecision.Disabled)

    /** Process-scoped online/offline for NavHost offline UX. */
    val connectivityObserver: AndroidConnectivityObserver =
        AndroidConnectivityObserver(appContext).also { it.start() }

    val connectivityStatus: ConnectivityStatusPort get() = connectivityObserver

    override val database: BoxLoreDatabase by lazy {
        BoxLoreDatabase.getDatabase(appContext)
    }

    /** Feature / nav local podcast access — prefer this over injecting [database]. */
    val localCatalogPort: LocalCatalogPort by lazy {
        RoomLocalCatalog(database)
    }

    /** Episode Info offline hydration — prefer this over injecting [database]. */
    val episodeOfflineLookupPort: EpisodeOfflineLookupPort by lazy {
        RoomEpisodeOfflineLookup(database)
    }

    /** Single install path for RSS; production callers must not call getInstance. */
    override val rssPodcastRepository: RssPodcastRepository by lazy {
        RssPodcastRepository.create(appContext, database).also { repository ->
            RssPodcastRepository.install(repository)
            repository.episodeCatalog.onCatalogPersisted = { id -> autoDownloadCoordinator.scanCached(id) }
        }
    }

    /**
     * PI show feed-only episode cache (not a subscription). Wired into [podcastRepository]
     * for episode lookup fallback and into Podcast Info for "Missing episodes?".
     */
    val episodeSupplementRepository: EpisodeSupplementRepository by lazy {
        EpisodeSupplementRepository.create(database)
    }

    val localEpisodeCatalogRepository: LocalEpisodeCatalogRepository by lazy {
        LocalEpisodeCatalogRepository.create(
            database = database,
            downloadCacheRelinker =
            DownloadCacheRelinker { oldId, newId ->
                DownloadRepository.relinkDownloadCache(appContext, oldId, newId)
            },
            onCatalogPersisted = { id -> autoDownloadCoordinator.scanCached(id) },
            renumberedShows = renumberedShows,
        )
    }

    /**
     * Shows Podcast Index renumbered (an old show ID that now answers as another), kept in
     * `boxlore_renumbered_shows`: follows move to the new show and catalog reads link the old
     * show's listener rows to its episodes.
     */
    override val renumberedShows: RenumberedShows by lazy {
        SharedPreferencesRenumberedShows(appContext.getSharedPreferences(SharedPreferencesRenumberedShows.FILE_NAME, Context.MODE_PRIVATE))
    }

    /**
     * Shared source-aware catalog for automation, foreground sync and push hydration. Every refresh
     * through it follows a feed that moved for good (P4 PR 14), behind `feed-fix-moved-feed-off`.
     */
    val subscribedEpisodeCatalog: cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort by lazy {
        cx.aswin.boxlore.core.catalog.SubscribedEpisodeCatalog(
            localEpisodeCatalogRepository,
            rssPodcastRepository.episodeCatalog,
            isSubscribed = { id -> database.podcastDao().getPodcast(id)?.isSubscribed == true },
            feedMoves =
            FeedMoveFollower(
                enabled = { FeedFixSwitches.isOn(FeedFixSwitches.MOVED_FEED_OFF) },
                showTitle = { id -> database.podcastDao().getPodcast(id)?.title },
                adopt = { id, readFrom, movedTo -> subscriptionRepository.adoptMovedFeed(id, readFrom, movedTo) },
            ),
        )
    }

    /** Single install path for adaptive ranking; production callers must not call getInstance. */
    override val adaptiveRankingRepository: AdaptiveRankingRepository by lazy {
        AdaptiveRankingRepository.create(appContext).also(AdaptiveRankingRepository::install)
    }

    override val rankingRuntimeControls: RankingRuntimeControls by lazy {
        RankingRuntimeControls.create(appContext).also(RankingRuntimeControls::install)
    }

    override val rankingFeedbackRepository: RankingFeedbackRepository by lazy {
        RankingFeedbackRepository.create(adaptiveRankingRepository).also(RankingFeedbackRepository::install)
    }

    override val adaptiveCandidateScorer: AdaptiveCandidateScorer by lazy {
        AdaptiveCandidateScorer
            .create(
                adaptiveRankingRepository,
                rankingRuntimeControls,
                // Count an episode stored under several IDs once (own-episode-IDs P4).
                episodeTokens = { ids -> database.episodeListenerStore().groupTokens(ids) },
            ).also(AdaptiveCandidateScorer::install)
    }

    init {
        // Playback owns MediaDownloadService; downloads starts it via this launcher (no Class.forName).
        DownloadServiceLauncherHolder.instance =
            DownloadServiceLauncher { MediaDownloadService::class.java }
    }

    override val podcastRepository: PodcastRepository by lazy {
        PodcastRepository(
            baseUrl = apiBaseUrl,
            publicKey = publicKey,
            context = appContext,
            rssRepository = rssPodcastRepository,
            episodeSupplementRepository = episodeSupplementRepository,
            localEpisodeCatalog = subscribedEpisodeCatalog,
            useEpisodeMatchBaseline = { FeedFixSwitches.isOn(FeedFixSwitches.MATCH_BASELINE_OFF) },
            rolloutSwitches = rolloutSwitches,
            episodeAliases = episodeAliasReads,
            discoveryEpisodeIds = discoveryEpisodeIds,
            // Feed-first show pages (P4 PR 16): stored copies, the shared report limiter and feed_fetch_result.
            feedFirstWiring =
            FeedFirstWiring(
                copies = localEpisodeCatalogRepository.showPages,
                isOnline = { connectivityStatus.isOnline() },
                reportLimiter = feedReportLimiter,
                telemetry = feedTelemetry,
                isFollowed = { id -> database.podcastDao().getPodcast(id)?.isSubscribed == true },
            ),
            // A show Podcast Index renumbered keeps one follow, under the show it now answers as.
            onShowRenumbered = { oldId, answer -> subscriptionRepository.renumberedFollows.adoptAnswer(oldId, answer) },
        )
    }

    /** Trusted alias reads for links, pushes, shares and Android Auto (own-IDs plan section 9; P4 PR 13). */
    val episodeAliasReads: EpisodeAliasReads by lazy { RoomEpisodeAliasReads { database } }

    /**
     * Names the ID an episode share link carries and registers the episode first; a registration
     * that can't reach the API in time is retried by [EpisodeShareRegistrationWorker].
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
     */
    val episodeSharePreparer: EpisodeSharePreparer by lazy {
        EpisodeSharePreparer(
            aliases = episodeAliasReads,
            register = { request -> EpisodeShareRegistrar.registerOnce(podcastRepository.apiV2, request) },
            scheduleRetry = { request -> EpisodeShareRegistrationWorker.enqueue(appContext, request) },
        )
    }

    /**
     * Translates episode IDs between this phone's stored IDs and the recipe IDs of the `/v2`
     * discovery routes, through the alias table. Opens the database on first use only.
     * Added in P4 PR 12 (own episode IDs, plan section 9, v2 discovery routes).
     */
    internal val discoveryEpisodeIds: DiscoveryEpisodeIds by lazy { RoomDiscoveryEpisodeIds { database } }

    /**
     * The `feed_first` rollout flag; one per process so its value stays fixed while the app runs.
     * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
     *
     * LEGACY(own-ids): staged feed-first rollout. Remove at sunset step S7 (MIGRATION.md §6) once
     * `feed_first` has been at 100% for 30 days.
     */
    internal val feedFirstSwitch: FeedFirstSwitch by lazy { FeedFirstSwitch.create(appContext) }

    /** Fail-open kill switch for the one-time upgrade repair; uses the saved answer before PostHog is ready. */
    internal val upgradeRepairSwitch: SavedKillSwitch by lazy {
        SavedKillSwitch.create(appContext, FeedFixSwitches.UPGRADE_REPAIR_OFF)
    }

    /** Fail-open kill switch for the daily address check; uses the saved answer before PostHog is ready. */
    internal val addressCheckSwitch: SavedKillSwitch by lazy {
        SavedKillSwitch.create(appContext, FeedFixSwitches.ADDRESS_CHECK_OFF)
    }

    /**
     * Fail-open kill switch for the v2 discovery routes; uses the saved answer before PostHog is ready.
     *
     * LEGACY(own-ids): fallback to the v1 discovery routes. Remove at sunset step S7
     * (MIGRATION.md §6) once the v1 API client methods are removed.
     */
    internal val v2DiscoverySwitch: SavedKillSwitch by lazy {
        SavedKillSwitch.create(appContext, FeedFixSwitches.V2_DISCOVERY_OFF)
    }

    /**
     * Saves each loaded answer of the switches above for the next process; called from
     * `PostHogAndroidConfig.onFeatureFlags`. Added in P4 (own episode IDs, plan section 11, Rollback).
     *
     * LEGACY(own-ids): staged feed-first rollout. Remove at sunset step S7 (MIGRATION.md §6) once
     * `feed_first` has been at 100% for 30 days.
     */
    internal val rolloutFlagMemory: RolloutFlagMemory by lazy {
        RolloutFlagMemory(
            feedFirst = feedFirstSwitch,
            killSwitches = listOf(upgradeRepairSwitch, addressCheckSwitch, v2DiscoverySwitch),
        )
    }

    /**
     * The v2 build's rollout switches handed to :core:catalog: `feed_first` (off without an answer,
     * fixed per process) and the fail-open kill switches for upgrade repair, the daily address
     * check and v2 discovery. Added in P4 (own episode IDs, plan section 11, Rollback).
     *
     * LEGACY(own-ids): the `feedFirst` and `v2Discovery` wiring is the staged rollout and the v1
     * discovery fallback. Remove both at sunset step S7 (MIGRATION.md §6) once `feed_first` has been
     * at 100% for 30 days and the v1 API client methods are removed.
     */
    val rolloutSwitches: RolloutSwitches by lazy {
        RolloutSwitches(
            // LEGACY(own-ids): staged feed-first rollout; remove at S7.
            feedFirst = { feedFirstSwitch.isOn() },
            upgradeRepair = { upgradeRepairSwitch.isOn() },
            addressCheck = { addressCheckSwitch.isOn() },
            // LEGACY(own-ids): v1 discovery fallback; remove at S7.
            v2Discovery = { v2DiscoverySwitch.isOn() },
        )
    }

    init {
        // Briefing link builders and chapters run outside PodcastRepository (features, playback);
        // they follow the same v2 discovery switch and translator. Nothing is opened here.
        DiscoveryRouting.install(v2Discovery = { rolloutSwitches.v2Discovery() }, episodeIds = discoveryEpisodeIds)
    }

    /**
     * Feed-read and address-swap reporting for catalog code (`feed_fetch_result`,
     * `feed_url_corrected`), handed in like [FeedFixToggles] so catalog never depends on analytics.
     * Phase P4, plan section "PostHog".
     */
    val feedTelemetry: FeedTelemetryPort by lazy { AnalyticsFeedTelemetry() }

    val boxcastPrefs: BoxcastPrefs by lazy { BoxcastPrefs(appContext) }

    override val deviceIdentityPort: DeviceIdentityPort by lazy {
        DeviceIdentityPort { boxcastPrefs.getOrCreateSyncDeviceId() }
    }

    val queueRepository: QueueRepository by lazy {
        QueueRepository(database, podcastRepository, deviceIdentityPort)
    }

    private val deferredCastSession = DeferredCastSessionState()

    /** Forwards Cast state to playback, or keeps it until playback is first created. */
    fun synchronizeCastSession(isActive: Boolean?) {
        deferredCastSession.submit(isActive)
    }

    val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(
            context = appContext,
            listeningHistoryDao = database.listeningHistoryDao(),
            listeningSessionDao = database.listeningSessionDao(),
            listeningRollupDao = database.listeningRollupDao(),
            listeningInsightsMaintenance = database.listeningInsightsMaintenance(),
            queueRepository = queueRepository,
            podcastRepository = podcastRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
            userPreferencesRepository = userPreferencesRepository,
        ).also { repository ->
            deferredCastSession.attach { isActive -> repository.synchronizeCastSession(isActive) }
        }
    }

    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(
            context = appContext,
            database = database,
            rankingFeedbackRepository = rankingFeedbackRepository,
        )
    }

    override val autoDownloadCoordinator: cx.aswin.boxlore.core.downloads.AutoDownloadCoordinator by lazy {
        cx.aswin.boxlore.core.downloads.AutoDownloadCoordinator.create(
            appContext,
            database,
            subscribedEpisodeCatalog,
            userPreferencesRepository,
            recoverFeedUrl = { id -> cx.aswin.boxlore.core.catalog.TrackedPodcastRtdbLogic.httpsFeedUrl(podcastRepository.getPodcastDetails(id)?.feedUrl) },
            saveFeedUrl = { id, url -> subscriptionRepository.ensureHttpsFeedUrl(id, url) },
        )
    }

    override val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(
            podcastDao = database.podcastDao(),
            localEpisodeCatalog = subscribedEpisodeCatalog,
            lookupHttpsFeedUrl = { id ->
                cx.aswin.boxlore.core.catalog.TrackedPodcastRtdbLogic.httpsFeedUrl(
                    podcastRepository.getPodcastDetails(id)?.feedUrl,
                )
            },
            folderRepository = folderRepository,
            userPreferencesRepository = userPreferencesRepository,
            autoDownloadDao = database.autoDownloadDao(),
            rssNotificationConsent = cx.aswin.boxlore.core.catalog.DeviceRssNotificationConsent(
                java.io.File(appContext.noBackupFilesDir, "rss_notification_consent"),
            ),
            rssNotificationRegistrations = cx.aswin.boxlore.core.catalog.DeviceRssNotificationRegistrations(
                java.io.File(appContext.noBackupFilesDir, "rss_notification_registrations"),
            ),
            requestRssNotificationSync = { cx.aswin.boxlore.fcm.RssNotificationSyncWorker.enqueue(appContext) },
            // One feed address writer: acknowledged re-registration and feed_url_corrected (P4 PR 14).
            feedAddressWiring =
            FeedAddressWiring(
                requestNotificationRegistration = { id -> PodcastNotificationRegistrationWorker.enqueue(appContext, id) },
                telemetry = feedTelemetry,
                pendingRegistrations =
                SharedPreferencesPendingNotificationRegistrations(
                    appContext.getSharedPreferences(SharedPreferencesPendingNotificationRegistrations.FILE_NAME, Context.MODE_PRIVATE),
                ),
            ),
            renumbering =
            RenumberingWiring(
                shows = renumberedShows,
                linkEpisodes = { oldId, newId -> localEpisodeCatalogRepository.linkRenumberedShow(oldId, newId) },
            ),
        )
    }

    override val subscriptionForegroundSync: SubscriptionForegroundSync by lazy {
        SubscriptionForegroundSync.create(
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            episodeSupplementPort = episodeSupplementRepository,
            localEpisodeCatalog = subscribedEpisodeCatalog,
            scope = syncScope,
            feedFixes =
            FeedFixToggles(
                addressRecheck = { FeedFixSwitches.isOn(FeedFixSwitches.ADDRESS_RECHECK_OFF) },
                syncFallback = { FeedFixSwitches.isOn(FeedFixSwitches.SYNC_FALLBACK_OFF) },
                movedFeed = { FeedFixSwitches.isOn(FeedFixSwitches.MOVED_FEED_OFF) },
            ),
            // Daily address check and broken-feed reports (P4 PR 14), behind address-check-off.
            addressUpkeep =
            FeedAddressUpkeep(
                reportStamps = feedReportStamps,
                isOnline = connectivityStatus::isOnline,
                reportLimiter = feedReportLimiter,
            ),
        )
    }

    /**
     * The one-time repair of saved feed addresses after the update, run by
     * [cx.aswin.boxlore.sync.UpgradeRepairWorker] behind `upgrade-repair-off`. Step 4's record lives
     * in `noBackupFilesDir/upgrade_repair_notifications`, so a restored phone registers again.
     * Once every show was read again, the sync's identity catch-up runs once more.
     * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
     * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
     */
    override val upgradeAddressRepair: UpgradeAddressRepair by lazy {
        UpgradeAddressRepair(
            repairDao = database.upgradeRepairDao(),
            podcastDao = database.podcastDao(),
            ports =
            UpgradeRepairPorts(
                fetchAddresses = AddressCheckAnswer.fetchWith(podcastRepository.apiV2),
                replaceAddress = subscriptionRepository::updateFeedAddress,
                reread = UpgradeAddressRepair.rereadWith(subscribedEpisodeCatalog),
                requestNotificationRegistration = { id -> PodcastNotificationRegistrationWorker.request(appContext, id) },
                onAddressStepsFinished = { userSyncCoordinator.requestIdentityCatchUp() },
                isOnline = connectivityStatus::isOnline,
                telemetry = feedTelemetry,
            ),
            marker = FileUpgradeNotificationMarker(java.io.File(appContext.noBackupFilesDir, FileUpgradeNotificationMarker.FILE_NAME)),
            enabled = rolloutSwitches.upgradeRepair,
            // Backed up with Room, so a restored phone never reports the upgrade twice (P4 PR 18).
            tally =
            SharedPreferencesUpgradeRepairTally(
                appContext.getSharedPreferences(SharedPreferencesUpgradeRepairTally.FILE_NAME, Context.MODE_PRIVATE),
            ),
        )
    }

    /**
     * `recipe_id` beside `episode_id` on analytics events (P4 PR 18): IDs the analytics façade
     * can't resolve itself are looked up here, off the main thread, from trusted aliases.
     * COMPAT(own-ids): analytics joins on recipe IDs. Permanent: never remove (MIGRATION.md §5).
     */
    val episodeRecipeIdLookup: EpisodeRecipeIdLookup =
        EpisodeRecipeIdLookup { ids, deliver ->
            syncScope.launch {
                val found = runCatching { database.episodeIdentityDao().knownRecipeIds(ids) }.getOrDefault(emptyMap())
                deliver(found)
            }
        }

    /**
     * Broken-feed report times, shared by the daily address check and the show page so both stay
     * inside one daily limit (P4 PR 14 and PR 16).
     */
    private val feedReportStamps: SharedPreferencesFeedReportStamps by lazy {
        SharedPreferencesFeedReportStamps(
            appContext.getSharedPreferences(SharedPreferencesFeedReportStamps.FILE_NAME, Context.MODE_PRIVATE),
        )
    }

    /**
     * The app's one broken-feed report limiter over [feedReportStamps]: the daily address check and
     * the show page claim through the same instance, so they never pass the caps together (P4 PR 16).
     */
    private val feedReportLimiter: FeedReportLimiter by lazy { FeedReportLimiter(feedReportStamps) }

    override val folderRepository: FolderRepository by lazy {
        RoomFolderRepository(
            folderDao = database.folderDao(),
            podcastDao = database.podcastDao(),
            resolveGenreIconKey = { genre ->
                cx.aswin.boxlore.core.designsystem.icon.findExactGenreIconKey(genre)
            },
        )
    }

    val legacyRssRepair: LegacyRssRepair by lazy {
        LegacyRssRepair.create(
            catalog =
            LegacyRssRepairCatalog(
                podcastDao = database.podcastDao(),
                rssRepository = rssPodcastRepository,
                podcastRepository = podcastRepository,
                userPreferences = userPreferencesRepository,
                boxcastPrefs = BoxcastPrefs(appContext),
                adaptiveRanking = adaptiveRankingRepository,
            ),
            runtime =
            LegacyRssRepairRuntime(
                isOnline = connectivityStatus::isOnline,
                activation =
                when (legacyRssRepairLaunchDecision) {
                    LegacyRssRepairLaunchDecision.Enabled -> LegacyRssRepairActivation.ENABLED
                    LegacyRssRepairLaunchDecision.SettleWithoutRepair ->
                        LegacyRssRepairActivation.SETTLE_WITHOUT_REPAIR
                    LegacyRssRepairLaunchDecision.Disabled -> LegacyRssRepairActivation.DISABLED
                },
                scope = syncScope,
                restoreNotifications = { podcast ->
                    subscriptionRepository.setNotificationsEnabled(podcast, true)
                },
            ),
        )
    }

    override val userPreferencesRepository: UserPreferencesRepository =
        sharedUserPreferences ?: UserPreferencesRepository(appContext)

    val consentManager: ConsentManager by lazy {
        ConsentManager(appContext)
    }

    val queueManager: QueueManager by lazy {
        QueueManager(queueRepository, playbackRepository)
    }

    override val historyRecommendationSource: HistoryRecommendationSource by lazy {
        cx.aswin.boxlore.core.playback.DefaultSmartQueueSources(
            context = appContext,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    override val smartDownloadManager: SmartDownloadManager by lazy {
        SmartDownloadManager(
            context = appContext,
            database = database,
            podcastRepository = podcastRepository,
            historyRecommendationSource = historyRecommendationSource,
            downloadRepository = downloadRepository,
            subscriptionRepository = subscriptionRepository,
            userPrefs = userPreferencesRepository,
            adaptiveScorer = adaptiveCandidateScorer,
        ).also {
            // Wire SmartDownloadSyncPort so LibraryBackupManager (in :core:data) can schedule
            // WorkManager periodic work without a data→downloads compile edge.
            SmartDownloadSyncPort.schedulePeriodicSync = { wifiOnly, chargingOnly ->
                SmartDownloadManager.schedulePeriodicSync(appContext, wifiOnly, chargingOnly)
            }
            SmartDownloadSyncPort.cancelPeriodicSync = {
                SmartDownloadManager.cancelPeriodicSync(appContext)
            }
        }
    }

    val installReferrerManager: InstallReferrerManager by lazy {
        InstallReferrerManager(appContext).also { manager ->
            manager.onInstallReferrerResolved = { channel, raw ->
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackInstallChannelAttributed(
                    installChannel = channel,
                    referrerRaw = raw,
                )
            }
        }
    }

    val authRepository: cx.aswin.boxlore.core.auth.AuthRepository by lazy {
        authRepositoryOverride ?: runCatching {
            cx.aswin.boxlore.core.auth.FirebaseAuthRepository(
                auth = com.google.firebase.auth.FirebaseAuth.getInstance(),
                pendingEmailStore = object : cx.aswin.boxlore.core.auth.PendingEmailStore {
                    private val prefs = BoxcastPrefs(appContext)
                    override fun getPendingEmail(): String? = prefs.getPendingAuthEmail()
                    override fun setPendingEmail(email: String?) = prefs.setPendingAuthEmail(email)
                },
                onPreDeleteAccount = {
                    userSyncCoordinator.deleteCloudSyncData().getOrThrow()
                },
            )
        }.getOrElse {
            object : cx.aswin.boxlore.core.auth.AuthRepository {
                override val currentUser =
                    kotlinx.coroutines.flow.MutableStateFlow<cx.aswin.boxlore.core.model.BoxLoreUser?>(null)
                override val currentUserId: String? = null
                override suspend fun signInWithGoogle(idToken: String) =
                    Result.failure<cx.aswin.boxlore.core.model.BoxLoreUser>(UnsupportedOperationException())
                override suspend fun signInWithEmailPassword(email: String, password: String) =
                    Result.failure<cx.aswin.boxlore.core.model.BoxLoreUser>(UnsupportedOperationException())
                override suspend fun signUpWithEmailPassword(email: String, password: String) =
                    Result.failure<cx.aswin.boxlore.core.model.BoxLoreUser>(UnsupportedOperationException())
                override suspend fun sendMagicLink(email: String) =
                    Result.failure<Unit>(UnsupportedOperationException())
                override suspend fun signInWithEmailLink(email: String, emailLink: String) =
                    Result.failure<cx.aswin.boxlore.core.model.BoxLoreUser>(UnsupportedOperationException())
                override fun isSignInWithEmailLink(link: String) = false
                override suspend fun sendPasswordReset(email: String) =
                    Result.failure<Unit>(UnsupportedOperationException())
                override suspend fun sendEmailVerification() =
                    Result.failure<Unit>(UnsupportedOperationException())
                override suspend fun reloadUser() =
                    Result.success<cx.aswin.boxlore.core.model.BoxLoreUser?>(null)
                override fun signOut() {
                    // No-op fallback when authRepository is not available
                }
                override suspend fun deleteAccount() =
                    Result.failure<Unit>(UnsupportedOperationException())
                override suspend fun getIdToken(forceRefresh: Boolean) = null
            }
        }
    }

    /** Signing in from emailed links; the Account page shows its progress. */
    val emailLinkSignIn: cx.aswin.boxlore.core.auth.EmailLinkSignIn by lazy {
        cx.aswin.boxlore.core.auth.EmailLinkSignIn(authRepository, syncScope)
    }

    override val userSyncCoordinator: UserSyncCoordinator by lazy {
        val queuePort = queueRepository
        // Episode identity on the sync wire: recipe IDs and catalog keys out, aliases for pulled rows in (own-episode-IDs P4).
        val episodeIdentity = SyncEpisodeIdentity(database)
        val subResolver = SubscriptionSyncResolver(
            podcastDao = database.podcastDao(),
            folderRepository = folderRepository,
            podcastRepository = podcastRepository,
            rssPodcastRepository = rssPodcastRepository,
            notificationSyncPort = subscriptionRepository,
            feedAddresses = subscriptionRepository.feedAddresses,
        )
        val historyResolver = HistorySyncResolver(
            listeningHistoryDao = database.listeningHistoryDao(),
            activePlaybackSyncPort = playbackRepository,
            // The playing episode is protected under every stored ID (own-episode-IDs P4).
            episodeIds = { episodeId -> database.episodeListenerStore().memberIds(episodeId) },
        )
        val queueResolver = QueueSyncResolver(
            queueSyncPort = queuePort,
            database = database,
            activePlaybackSyncPort = playbackRepository,
            podcastRepository = podcastRepository,
            episodeIdentity = episodeIdentity,
        )
        UserSyncCoordinator(
            boxLoreApi = podcastRepository.api,
            publicKey = publicKey,
            authUserIdProvider = {
                val user = authRepository.currentUser.value
                if (cx.aswin.boxlore.sync.canSyncUser(user)) user?.uid else null
            },
            tokenProvider = { authRepository.getIdToken(forceRefresh = false) },
            podcastDao = database.podcastDao(),
            listeningHistoryDao = database.listeningHistoryDao(),
            queueSyncPort = queuePort,
            subscriptionSyncResolver = subResolver,
            historySyncResolver = historyResolver,
            queueSyncResolver = queueResolver,
            boxcastPrefs = boxcastPrefs,
            activePlaybackSyncPort = playbackRepository,
            folderDao = database.folderDao(),
            listeningSessionDao = database.listeningSessionDao(),
            listeningRollupDao = database.listeningRollupDao(),
            playedShowDao = database.playedShowDao(),
            userPreferencesRepository = userPreferencesRepository,
            episodeIdentityDao = database.episodeIdentityDao(),
            episodeIdentity = episodeIdentity,
        )
    }

    val cloudSyncTriggerCoordinator: CloudSyncTriggerCoordinator by lazy {
        CloudSyncTriggerCoordinator(
            context = appContext,
            applicationScope = syncScope,
            userSyncCoordinator = userSyncCoordinator,
            authRepository = authRepository,
            boxcastPrefs = boxcastPrefs,
            podcastDao = database.podcastDao(),
            listeningHistoryDao = database.listeningHistoryDao(),
            queueDao = database.queueDao(),
            playbackRepository = playbackRepository,
            playerStateFlow = playbackRepository.playerState,
            isOnlineFlow = connectivityObserver.isOnlineFlow,
            onSignOutAction = {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
                        .addOnFailureListener { e ->
                            android.util.Log.e("FcmSignOut", "Failed to delete FCM token on sign-out", e)
                        }
                } catch (e: Exception) {
                    android.util.Log.e("FcmSignOut", "Failed to delete FCM token on sign-out", e)
                }
            },
        )
    }
}
