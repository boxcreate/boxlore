package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cx.aswin.boxlore.core.data.DownloadRepository
import cx.aswin.boxlore.core.data.PlaybackRepository
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.QueueManager
import cx.aswin.boxlore.core.data.RssPodcastRepository
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.data.UserPreferencesRepository
import cx.aswin.boxlore.core.data.database.BoxLoreDatabase

/** Builds Info ViewModels from shared container deps (production or test doubles). */
object InfoViewModelAssembler {
    fun createPodcastInfo(
        application: Application,
        repository: PodcastRepository,
        playbackRepository: PlaybackRepository,
        downloadRepository: DownloadRepository,
        queueManager: QueueManager,
        subscriptionRepository: SubscriptionRepository,
        rssRepository: RssPodcastRepository,
        database: BoxLoreDatabase,
        entryPoint: String?,
        genreFilter: String?,
        scrollDepth: Int?,
        searchQuery: String?,
    ): PodcastInfoViewModel = PodcastInfoViewModel(
        application = application,
        repository = repository,
        playbackRepository = playbackRepository,
        downloadRepository = downloadRepository,
        queueManager = queueManager,
        subscriptionRepository = subscriptionRepository,
        rssRepository = rssRepository,
        database = database,
        entryPoint = entryPoint,
        genreFilter = genreFilter,
        scrollDepth = scrollDepth,
        searchQuery = searchQuery,
    )

    fun podcastInfoFactory(
        application: Application,
        repository: PodcastRepository,
        playbackRepository: PlaybackRepository,
        downloadRepository: DownloadRepository,
        queueManager: QueueManager,
        subscriptionRepository: SubscriptionRepository,
        rssRepository: RssPodcastRepository,
        database: BoxLoreDatabase,
        entryPoint: String?,
        genreFilter: String?,
        scrollDepth: Int?,
        searchQuery: String?,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PodcastInfoViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return createPodcastInfo(
                application = application,
                repository = repository,
                playbackRepository = playbackRepository,
                downloadRepository = downloadRepository,
                queueManager = queueManager,
                subscriptionRepository = subscriptionRepository,
                rssRepository = rssRepository,
                database = database,
                entryPoint = entryPoint,
                genreFilter = genreFilter,
                scrollDepth = scrollDepth,
                searchQuery = searchQuery,
            ) as T
        }
    }

    fun createEpisodeInfo(
        application: Application,
        podcastRepository: PodcastRepository,
        playbackRepository: PlaybackRepository,
        downloadRepository: DownloadRepository,
        queueManager: QueueManager,
        userPrefs: UserPreferencesRepository,
        database: BoxLoreDatabase,
    ): EpisodeInfoViewModel = EpisodeInfoViewModel(
        application = application,
        podcastRepository = podcastRepository,
        playbackRepository = playbackRepository,
        downloadRepository = downloadRepository,
        queueManager = queueManager,
        userPrefs = userPrefs,
        database = database,
    )

    fun episodeInfoFactory(
        application: Application,
        podcastRepository: PodcastRepository,
        playbackRepository: PlaybackRepository,
        downloadRepository: DownloadRepository,
        queueManager: QueueManager,
        userPrefs: UserPreferencesRepository,
        database: BoxLoreDatabase,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EpisodeInfoViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return createEpisodeInfo(
                application = application,
                podcastRepository = podcastRepository,
                playbackRepository = playbackRepository,
                downloadRepository = downloadRepository,
                queueManager = queueManager,
                userPrefs = userPrefs,
                database = database,
            ) as T
        }
    }
}
