package cx.aswin.boxlore

import android.content.Context
import cx.aswin.boxlore.connectivity.AndroidConnectivityObserver
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.InstallReferrerManager
import cx.aswin.boxlore.core.catalog.LegacyRssRepair
import cx.aswin.boxlore.core.catalog.LegacyRssRepairActivation
import cx.aswin.boxlore.core.catalog.LegacyRssRepairCatalog
import cx.aswin.boxlore.core.catalog.LegacyRssRepairRuntime
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.RoomEpisodeOfflineLookup
import cx.aswin.boxlore.core.catalog.RoomFolderRepository
import cx.aswin.boxlore.core.catalog.RoomLocalCatalog
import cx.aswin.boxlore.core.catalog.SharedAppDependencies
import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.SmartDownloadSyncPort
import cx.aswin.boxlore.core.catalog.privacy.ConsentManager
import cx.aswin.boxlore.core.catalog.sync.HistorySyncResolver
import cx.aswin.boxlore.core.catalog.sync.QueueSyncResolver
import cx.aswin.boxlore.core.catalog.sync.SubscriptionSyncResolver
import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
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
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.rss.EpisodeSupplementRepository
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import cx.aswin.boxlore.sync.CloudSyncTriggerCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    authRepositoryOverride: cx.aswin.boxlore.core.network.AuthRepository? = null,
) : SharedAppDependencies,
    DownloadsDependencies {
    private val appContext = context.applicationContext

    private val syncScope = applicationScope
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
        RssPodcastRepository.create(appContext, database).also(RssPodcastRepository::install)
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
            .create(adaptiveRankingRepository, rankingRuntimeControls)
            .also(AdaptiveCandidateScorer::install)
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
            localEpisodeCatalog = localEpisodeCatalogRepository,
        )
    }

    val boxcastPrefs: BoxcastPrefs by lazy { BoxcastPrefs(appContext) }

    override val deviceIdentityPort: DeviceIdentityPort by lazy {
        DeviceIdentityPort { boxcastPrefs.getOrCreateSyncDeviceId() }
    }

    val queueRepository: QueueRepository by lazy {
        QueueRepository(database, podcastRepository, deviceIdentityPort)
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
        )
    }

    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(
            context = appContext,
            database = database,
            rankingFeedbackRepository = rankingFeedbackRepository,
        ).also { _ ->
            // Wire the DownloadCacheRelinker into RssPodcastRepository so the RSS module
            // does not need a compile-time dependency on :core:downloads.
            rssPodcastRepository.setDownloadCacheRelinker(
                DownloadCacheRelinker { oldId, newId ->
                    DownloadRepository.relinkDownloadCache(appContext, oldId, newId)
                },
            )
        }
    }

    override val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(
            podcastDao = database.podcastDao(),
            localEpisodeCatalog = localEpisodeCatalogRepository,
            lookupHttpsFeedUrl = { id ->
                cx.aswin.boxlore.core.catalog.TrackedPodcastRtdbLogic.httpsFeedUrl(
                    podcastRepository.getPodcastDetails(id)?.feedUrl,
                )
            },
            folderRepository = folderRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    override val subscriptionForegroundSync: SubscriptionForegroundSync by lazy {
        SubscriptionForegroundSync.create(
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            episodeSupplementPort = episodeSupplementRepository,
            localEpisodeCatalog = localEpisodeCatalogRepository,
            scope = syncScope,
        )
    }

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

    val authRepository: cx.aswin.boxlore.core.network.AuthRepository by lazy {
        authRepositoryOverride ?: runCatching {
            cx.aswin.boxlore.core.network.FirebaseAuthRepository(
                auth = com.google.firebase.auth.FirebaseAuth.getInstance(),
                pendingEmailStore = object : cx.aswin.boxlore.core.network.PendingEmailStore {
                    private val prefs = BoxcastPrefs(appContext)
                    override fun getPendingEmail(): String? = prefs.getPendingAuthEmail()
                    override fun setPendingEmail(email: String?) = prefs.setPendingAuthEmail(email)
                },
            )
        }.getOrElse {
            object : cx.aswin.boxlore.core.network.AuthRepository {
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
                override fun signOut() {
                    // No-op fallback when authRepository is not available
                }
                override suspend fun deleteAccount() =
                    Result.failure<Unit>(UnsupportedOperationException())
                override suspend fun getIdToken(forceRefresh: Boolean) = null
            }
        }
    }

    override val userSyncCoordinator: UserSyncCoordinator by lazy {
        val queuePort = queueRepository
        val subResolver = SubscriptionSyncResolver(
            podcastDao = database.podcastDao(),
            folderRepository = folderRepository,
            podcastRepository = podcastRepository,
            rssPodcastRepository = rssPodcastRepository,
            notificationSyncPort = subscriptionRepository,
        )
        val historyResolver = HistorySyncResolver(
            listeningHistoryDao = database.listeningHistoryDao(),
            activePlaybackSyncPort = playbackRepository,
        )
        val queueResolver = QueueSyncResolver(
            queueSyncPort = queuePort,
            database = database,
            activePlaybackSyncPort = playbackRepository,
            podcastRepository = podcastRepository,
        )
        UserSyncCoordinator(
            boxLoreApi = podcastRepository.api,
            publicKey = publicKey,
            authUserIdProvider = { authRepository.currentUserId },
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
            userPreferencesRepository = userPreferencesRepository,
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
        )
    }
}
