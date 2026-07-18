package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cx.aswin.boxlore.core.data.DownloadRepository
import cx.aswin.boxlore.core.data.EngagementPromptCoordinator
import cx.aswin.boxlore.core.data.PlaybackRepository
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.RssPodcastRepository
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository

/** Builds [HomeViewModel] from shared container deps (production or test doubles). */
object HomeViewModelAssembler {
    fun create(
        application: Application,
        podcastRepository: PodcastRepository,
        playbackRepository: PlaybackRepository,
        engagementCoordinator: EngagementPromptCoordinator,
        subscriptionRepository: SubscriptionRepository,
        downloadRepository: DownloadRepository,
        rssRepository: RssPodcastRepository,
        adaptiveRankingRepository: AdaptiveRankingRepository,
        adaptiveScorer: AdaptiveCandidateScorer,
        rankingFeedback: RankingFeedbackRepository,
        database: BoxLoreDatabase,
    ): HomeViewModel = HomeViewModel(
        application = application,
        podcastRepository = podcastRepository,
        playbackRepository = playbackRepository,
        engagementCoordinator = engagementCoordinator,
        subscriptionRepository = subscriptionRepository,
        downloadRepository = downloadRepository,
        rssRepository = rssRepository,
        adaptiveRankingRepository = adaptiveRankingRepository,
        adaptiveScorer = adaptiveScorer,
        rankingFeedback = rankingFeedback,
        database = database,
    )

    fun factory(
        application: Application,
        podcastRepository: PodcastRepository,
        playbackRepository: PlaybackRepository,
        engagementCoordinator: EngagementPromptCoordinator,
        subscriptionRepository: SubscriptionRepository,
        downloadRepository: DownloadRepository,
        rssRepository: RssPodcastRepository,
        adaptiveRankingRepository: AdaptiveRankingRepository,
        adaptiveScorer: AdaptiveCandidateScorer,
        rankingFeedback: RankingFeedbackRepository,
        database: BoxLoreDatabase,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return create(
                application = application,
                podcastRepository = podcastRepository,
                playbackRepository = playbackRepository,
                engagementCoordinator = engagementCoordinator,
                subscriptionRepository = subscriptionRepository,
                downloadRepository = downloadRepository,
                rssRepository = rssRepository,
                adaptiveRankingRepository = adaptiveRankingRepository,
                adaptiveScorer = adaptiveScorer,
                rankingFeedback = rankingFeedback,
                database = database,
            ) as T
        }
    }
}
