package cx.aswin.boxlore

import android.content.Context
import cx.aswin.boxlore.core.data.DownloadRepository
import cx.aswin.boxlore.core.data.InstallReferrerManager
import cx.aswin.boxlore.core.data.PlaybackRepository
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.QueueManager
import cx.aswin.boxlore.core.data.QueueRepository
import cx.aswin.boxlore.core.data.RssPodcastRepository
import cx.aswin.boxlore.core.data.SharedAppDependencies
import cx.aswin.boxlore.core.data.SmartDownloadManager
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.data.UserPreferencesRepository
import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.data.ports.DownloadCacheRelinker
import cx.aswin.boxlore.core.data.ports.SmartDownloadSyncPort
import cx.aswin.boxlore.core.data.privacy.ConsentManager
import cx.aswin.boxlore.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.downloads.DownloadsDependencies

/**
 * Application-scoped composition root for shared DB / repositories / managers.
 *
 * Construction order (invariant):
 * DB → RSS/ranking peers → PodcastRepository → QueueRepository → PlaybackRepository
 * → QueueManager → SmartDownloadManager.
 *
 * Ranking/RSS [getInstance] calls live only here so workers/services can consume the same
 * instances via [cx.aswin.boxlore.core.data.SharedAppDependenciesHolder] (catalog/prefs/ranking)
 * and [cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder] (download types).
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
) : SharedAppDependencies, DownloadsDependencies {
    private val appContext = context.applicationContext

    override val database: BoxLoreDatabase by lazy {
        BoxLoreDatabase.getDatabase(appContext)
    }

    /** Single install path for RSS; production callers must not call getInstance. */
    override val rssPodcastRepository: RssPodcastRepository by lazy {
        RssPodcastRepository.getInstance(appContext)
    }

    /** Single install path for adaptive ranking; production callers must not call getInstance. */
    override val adaptiveRankingRepository: AdaptiveRankingRepository by lazy {
        AdaptiveRankingRepository.getInstance(appContext)
    }

    override val rankingFeedbackRepository: RankingFeedbackRepository by lazy {
        RankingFeedbackRepository.getInstance(appContext)
    }

    override val adaptiveCandidateScorer: AdaptiveCandidateScorer by lazy {
        AdaptiveCandidateScorer.getInstance(appContext)
    }

    override val podcastRepository: PodcastRepository by lazy {
        PodcastRepository(
            baseUrl = apiBaseUrl,
            publicKey = publicKey,
            context = appContext,
            rssRepository = rssPodcastRepository,
        )
    }

    val queueRepository: QueueRepository by lazy {
        QueueRepository(database, podcastRepository)
    }

    val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(
            context = appContext,
            listeningHistoryDao = database.listeningHistoryDao(),
            queueRepository = queueRepository,
            podcastRepository = podcastRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
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
                }
            )
        }
    }

    override val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(database.podcastDao())
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
        cx.aswin.boxlore.core.data.DefaultSmartQueueSources(
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
        InstallReferrerManager(appContext)
    }
}
