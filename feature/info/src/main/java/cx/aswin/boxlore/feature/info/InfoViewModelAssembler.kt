package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.QueueManager
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository

/** Shared deps for podcast/episode info ViewModels (keeps assembler APIs ≤7 params). */
data class InfoSharedDeps(
    val podcastRepository: PodcastRepository,
    val playbackRepository: PlaybackRepository,
    val downloadRepository: DownloadRepository,
    val queueManager: QueueManager,
    val localCatalog: LocalCatalogPort,
    val episodeOfflineLookup: EpisodeOfflineLookupPort,
    val subscriptionForegroundSync: SubscriptionForegroundSync? = null,
    val folderRepository: cx.aswin.boxlore.core.catalog.FolderRepository? = null,
)

data class PodcastInfoRouteArgs(
    val entryPoint: String?,
    val genreFilter: String?,
    val scrollDepth: Int?,
    val searchQuery: String?,
)

/** Builds Info ViewModels from shared container deps (production or test doubles). */
object InfoViewModelAssembler {
    fun createPodcastInfo(
        application: Application,
        deps: InfoSharedDeps,
        subscriptionRepository: SubscriptionRepository,
        rssRepository: RssPodcastRepository,
        episodeSupplementPort: EpisodeSupplementPort,
        userPrefs: UserPreferencesRepository,
        routeArgs: PodcastInfoRouteArgs,
    ): PodcastInfoViewModel = PodcastInfoViewModel(
        application = application,
        deps = deps,
        subscriptionRepository = subscriptionRepository,
        rssRepository = rssRepository,
        episodeSupplementPort = episodeSupplementPort,
        userPreferencesRepository = userPrefs,
        routeArgs = routeArgs,
    )

    fun podcastInfoFactory(
        application: Application,
        deps: InfoSharedDeps,
        subscriptionRepository: SubscriptionRepository,
        rssRepository: RssPodcastRepository,
        episodeSupplementPort: EpisodeSupplementPort,
        userPrefs: UserPreferencesRepository,
        routeArgs: PodcastInfoRouteArgs,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PodcastInfoViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return createPodcastInfo(
                application = application,
                deps = deps,
                subscriptionRepository = subscriptionRepository,
                rssRepository = rssRepository,
                episodeSupplementPort = episodeSupplementPort,
                userPrefs = userPrefs,
                routeArgs = routeArgs,
            ) as T
        }
    }

    fun createEpisodeInfo(
        application: Application,
        deps: InfoSharedDeps,
        userPrefs: UserPreferencesRepository,
    ): EpisodeInfoViewModel = EpisodeInfoViewModel(
        application = application,
        podcastRepository = deps.podcastRepository,
        playbackRepository = deps.playbackRepository,
        downloadRepository = deps.downloadRepository,
        queueManager = deps.queueManager,
        userPrefs = userPrefs,
        localCatalog = deps.localCatalog,
        episodeOfflineLookup = deps.episodeOfflineLookup,
    )

    fun episodeInfoFactory(
        application: Application,
        deps: InfoSharedDeps,
        userPrefs: UserPreferencesRepository,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EpisodeInfoViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return createEpisodeInfo(
                application = application,
                deps = deps,
                userPrefs = userPrefs,
            ) as T
        }
    }
}
