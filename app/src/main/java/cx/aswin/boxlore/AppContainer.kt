package cx.aswin.boxlore

import android.content.Context
import cx.aswin.boxlore.core.data.DownloadRepository
import cx.aswin.boxlore.core.data.InstallReferrerManager
import cx.aswin.boxlore.core.data.PlaybackRepository
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.QueueManager
import cx.aswin.boxlore.core.data.QueueRepository
import cx.aswin.boxlore.core.data.RssPodcastRepository
import cx.aswin.boxlore.core.data.SmartDownloadManager
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.data.UserPreferencesRepository
import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.data.privacy.ConsentManager
import cx.aswin.boxlore.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository

/**
 * Application-scoped composition root for shared DB / repositories / managers.
 *
 * Construction order (invariant):
 * DB → PodcastRepository → QueueRepository → PlaybackRepository → QueueManager → SmartDownloadManager.
 *
 * Callers are wired in later phases; this type is safe to construct but unused until then.
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
) {
    private val appContext = context.applicationContext

    val database: BoxLoreDatabase by lazy {
        BoxLoreDatabase.getDatabase(appContext)
    }

    val podcastRepository: PodcastRepository by lazy {
        PodcastRepository(
            baseUrl = apiBaseUrl,
            publicKey = publicKey,
            context = appContext,
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
        )
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(appContext, database)
    }

    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(database.podcastDao())
    }

    val userPreferencesRepository: UserPreferencesRepository =
        sharedUserPreferences ?: UserPreferencesRepository(appContext)

    val consentManager: ConsentManager by lazy {
        ConsentManager(appContext)
    }

    val queueManager: QueueManager by lazy {
        QueueManager(queueRepository, playbackRepository)
    }

    val historyRecommendationSource: cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource by lazy {
        cx.aswin.boxlore.core.data.DefaultSmartQueueSources(
            context = appContext,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    val smartDownloadManager: SmartDownloadManager by lazy {
        SmartDownloadManager(
            context = appContext,
            database = database,
            podcastRepository = podcastRepository,
            historyRecommendationSource = historyRecommendationSource,
            downloadRepository = downloadRepository,
            subscriptionRepository = subscriptionRepository,
            userPrefs = userPreferencesRepository,
        )
    }

    val installReferrerManager: InstallReferrerManager by lazy {
        InstallReferrerManager(appContext)
    }

    val rssPodcastRepository: RssPodcastRepository by lazy {
        RssPodcastRepository.getInstance(appContext)
    }

    val adaptiveCandidateScorer: AdaptiveCandidateScorer by lazy {
        AdaptiveCandidateScorer.getInstance(appContext)
    }

    val rankingFeedbackRepository: RankingFeedbackRepository by lazy {
        RankingFeedbackRepository.getInstance(appContext)
    }

    val adaptiveRankingRepository: AdaptiveRankingRepository by lazy {
        AdaptiveRankingRepository.getInstance(appContext)
    }
}
