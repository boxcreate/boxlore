package cx.aswin.boxlore.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun androidx.navigation.NavGraphBuilder.addDebugDestination(w: NavGraphWiring) {
    val navController = w.navController
    val container = w.container
    val playbackRepository = w.playbackRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs

    // -----------------------------------------------------------------------
    // Debug
    // -----------------------------------------------------------------------
    composable("debug") {
        cx.aswin.boxlore.feature.home.DebugScreen(
            playbackRepository = playbackRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPrefs,
            adaptiveRankingRepository = container.adaptiveRankingRepository,
            bottomContentPadding = w.session.miniPlayerPadding,
            onBack = { navController.popBackStack() },
        )
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun androidx.navigation.NavGraphBuilder.addLibraryDestinations(w: NavGraphWiring) {
    val navController = w.navController
    val container = w.container
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val subscriptionRepository = w.subscriptionRepository
    val folderRepository = w.folderRepository
    val userPrefs = w.userPrefs
    val smartDownloadManager = w.smartDownloadManager
    val queueManager = w.queueManager
    val scope = w.scope
    val currentEpisode = w.session.currentEpisode
    val isOnline = w.isOnline
    var isSyncingSmartDownloads by w.isSyncingSmartDownloads

    // -----------------------------------------------------------------------
    // Library
    // -----------------------------------------------------------------------
    composable("library") {
        val viewModel =
            androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
                factory =
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                        folderRepository,
                    ) as T
                },
            )
        cx.aswin.boxlore.feature.library.LibraryScreen(
            viewModel = viewModel,
            onNavigateToLiked = { navController.navigate("library/liked") },
            onNavigateToSubscriptions = { navController.navigate(NavRoutes.LIBRARY_SUBSCRIPTIONS) },
            onNavigateToDownloads = { navController.navigate(NavRoutes.LIBRARY_DOWNLOADS) },
            onNavigateToHistory = { navController.navigate("library/history") },
            onNavigateToSettings = { navController.navigate("settings?page=hub") },
            onNavigateToDebug = { navController.navigate("debug") },
            onFeedbackClick = { w.actions.onShowFeedbackSheet() },
        )
    }

    composable("library/history") {
        val viewModel =
            androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.HistoryViewModel>(
                factory =
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = cx.aswin.boxlore.feature.library.HistoryViewModel(
                        playbackRepository,
                    ) as T
                },
            )
        cx.aswin.boxlore.feature.library.HistoryScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { item ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val desc = "Resuming from History"
                navController.navigate(
                    "episode/${item.episodeId}/${encode(item.episodeTitle)}/" +
                        "${encode(desc)}/" +
                        "${encode(item.episodeImageUrl ?: item.podcastImageUrl)}/" +
                        "${encode(item.episodeAudioUrl)}/" +
                        "${item.durationMs}/${encode(item.podcastId)}/" +
                        "${encode(item.podcastName)}" +
                        "?entryPoint=library_history",
                )
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_history_empty_state") {
                    popUpTo("home")
                }
            },
        )
    }

    composable("library/liked") {
        val viewModel =
            androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
                factory =
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                        folderRepository,
                    ) as T
                },
            )
        cx.aswin.boxlore.feature.library.LikedEpisodesScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { episode, podcast ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                navController.navigate(
                    "episode/${episode.id}/${encode(episode.title)}/" +
                        "${encode(episode.description.take(500))}/" +
                        "${encode(episode.imageUrl)}/" +
                        "${encode(episode.audioUrl)}/" +
                        "${episode.duration}/${encode(podcast.id)}/" +
                        "${encode(podcast.title)}" +
                        "?entryPoint=library_liked_episodes",
                )
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_history_empty_state") {
                    popUpTo("home")
                }
            },
        )
    }

    composable(
        "library/subscriptions?tab={tab}",
        arguments =
        listOf(
            navArgument("tab") {
                type = NavType.IntType
                defaultValue = cx.aswin.boxlore.core.prefs.SubscriptionsDefaultTab.NAV_USE_PREF
            },
        ),
        deepLinks =
        listOf(
            navDeepLink { uriPattern = "boxlore://library/subscriptions?tab={tab}" },
            navDeepLink { uriPattern = "boxlore://library/subscriptions" },
            navDeepLink { uriPattern = "boxcast://library/subscriptions?tab={tab}" },
            navDeepLink { uriPattern = "boxcast://library/subscriptions" },
        ),
    ) { backStackEntry ->
        val navTab =
            backStackEntry.arguments?.getInt("tab")
                ?: cx.aswin.boxlore.core.prefs.SubscriptionsDefaultTab.NAV_USE_PREF
        val initialTab =
            cx.aswin.boxlore.core.prefs.SubscriptionsDefaultTab.resolveIndex(
                navTab = navTab,
                preferred = userPrefs.cachedSubscriptionsDefaultTab,
            )
        val viewModel =
            androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
                factory =
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                        folderRepository,
                    ) as T
                },
            )
        cx.aswin.boxlore.feature.library.SubscriptionsScreen(
            viewModel = viewModel,
            onBack = {
                when (resolveLaunchSubscriptionsBack(w.session.openedToLandingOnLaunch.value)) {
                    LaunchSubscriptionsBackAction.NavigateHome -> {
                        w.session.openedToLandingOnLaunch.value = false
                        navController.navigateHomeFromLaunchSubscriptions()
                    }
                    LaunchSubscriptionsBackAction.PopBackStack -> navController.popBackStack()
                }
            },
            onPodcastClick = { podcastId ->
                navController.navigate(
                    "podcast/${android.net.Uri.encode(podcastId)}?entryPoint=library_subscriptions",
                )
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_subscriptions_empty_state") {
                    popUpTo("home")
                }
            },
            onPlayEpisode = { episode, podcast -> queueManager.playEpisode(episode, podcast) },
            onPlayEpisodes = { episodes, fallbackPodcast -> queueManager.playEpisodes(episodes, fallbackPodcast) },
            onEpisodeClick = { episode, podcast, entryPointStr ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val entryPointQuery = if (entryPointStr != null) "?entryPoint=$entryPointStr" else ""
                navController.navigate(
                    "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                        "${encode(episode.description.take(500))}/" +
                        "${encode(episode.imageUrl)}/" +
                        "${encode(episode.audioUrl)}/" +
                        "${episode.duration}/${encode(podcast.id)}/" +
                        "${encode(podcast.title)}" +
                        entryPointQuery,
                )
            },
            isPlayerActive = currentEpisode != null,
            initialTab = initialTab,
        )
    }

    composable(NavRoutes.LIBRARY_DOWNLOADS) {
        val viewModel =
            androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
                factory =
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                        folderRepository,
                    ) as T
                },
            )
        cx.aswin.boxlore.feature.library.DownloadedEpisodesScreen(
            viewModel = viewModel,
            userPrefs = userPrefs,
            isOffline = !isOnline,
            onBack = {
                when (resolveLaunchSubscriptionsBack(w.session.openedToLandingOnLaunch.value)) {
                    LaunchSubscriptionsBackAction.NavigateHome -> {
                        w.session.openedToLandingOnLaunch.value = false
                        navController.navigateHomeFromLaunchSubscriptions()
                    }
                    LaunchSubscriptionsBackAction.PopBackStack -> navController.popBackStack()
                }
            },
            isPlayerActive = currentEpisode != null,
            onPodcastShowClick = { podcastId, podcastTitle ->
                android.util.Log.d("NavHost", "onPodcastShowClick: id=$podcastId title=$podcastTitle")
                val encodedTitle = android.net.Uri.encode(podcastTitle.ifEmpty { "_" })
                val encodedId = android.net.Uri.encode(podcastId.ifEmpty { "_" })
                navController.navigate("library/downloads/show?podcastId=$encodedId&podcastTitle=$encodedTitle")
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_downloads_empty_state") {
                    popUpTo("home")
                }
            },
            onSettingsClick = { navController.navigate(NavRoutes.LIBRARY_DOWNLOADS_SETTINGS) },
            isSyncing = isSyncingSmartDownloads,
            onSyncNow = {
                scope.launch(Dispatchers.IO) {
                    isSyncingSmartDownloads = true
                    try {
                        smartDownloadManager.performSync(isManual = true)
                    } finally {
                        isSyncingSmartDownloads = false
                    }
                }
            },
        )
    }

    composable(
        route = "library/downloads/show?podcastId={podcastId}&podcastTitle={podcastTitle}",
        arguments =
        listOf(
            navArgument("podcastId") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("podcastTitle") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStackEntry ->
        val podcastId = backStackEntry.arguments?.getString("podcastId") ?: ""
        val podcastTitle = backStackEntry.arguments?.getString("podcastTitle") ?: ""

        val viewModel =
            androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
                factory =
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                        folderRepository,
                    ) as T
                },
            )
        cx.aswin.boxlore.feature.library.DownloadedShowEpisodesScreen(
            viewModel = viewModel,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            onBack = { navController.popBackStack() },
            isPlayerActive = currentEpisode != null,
            onEpisodeClick = { episode, podcast ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                navController.navigate(
                    "episode/${episode.id}/${encode(episode.title)}/" +
                        "${encode(episode.description.take(500))}/" +
                        "${encode(episode.imageUrl)}/" +
                        "${encode(episode.audioUrl)}/" +
                        "${episode.duration}/${encode(podcast.id)}/" +
                        "${encode(podcast.title)}" +
                        "?entryPoint=library_downloaded_episodes",
                )
            },
        )
    }
}
