package cx.aswin.boxlore.navigation

import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cx.aswin.boxlore.core.playback.completedEpisodeIds

/**
 * Library › Playlists and one playlist's page. Storage is `:core:catalog`'s `PlaylistRepository`;
 * playing goes through `QueueManager.playPlaylist`, with `upNextSnapshot`/`restoreUpNext` for Undo.
 */
internal fun androidx.navigation.NavGraphBuilder.addPlaylistDestinations(w: NavGraphWiring) {
    val navController = w.navController
    val playlistRepository = w.playlistRepository
    val playbackRepository = w.playbackRepository
    val queueManager = w.queueManager

    fun viewModelFactory(playlistId: String?) =
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = cx.aswin.boxlore.feature.library.playlists.PlaylistsViewModel(
                repository = playlistRepository,
                ports =
                cx.aswin.boxlore.feature.library.playlists.PlaylistPlaybackPorts(
                    followedShows = w.subscriptionRepository.subscribedPodcasts,
                    folders = w.folderRepository.folders,
                    completedEpisodeIds = playbackRepository.completedEpisodeIds,
                    startPlayback = { id, episodes ->
                        val first = episodes.first()
                        val fallback =
                            cx.aswin.boxlore.core.model.Podcast(
                                id = first.podcastId.orEmpty(),
                                title = first.podcastTitle.orEmpty(),
                                artist = first.podcastArtist.orEmpty(),
                                imageUrl = first.podcastImageUrl ?: first.imageUrl.orEmpty(),
                            )
                        queueManager.playPlaylist(id, episodes, fallback)
                    },
                    upNextSnapshot = queueManager::upNextSnapshot,
                    restoreUpNext = queueManager::restoreUpNext,
                ),
                playlistId = playlistId,
            ) as T
        }

    composable(NavRoutes.LIBRARY_PLAYLISTS) {
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.playlists.PlaylistsViewModel>(factory = viewModelFactory(null))
        cx.aswin.boxlore.feature.library.playlists.PlaylistsScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onOpenPlaylist = { id -> w.navigate(NavRoutes.libraryPlaylist(id)) },
            isPlayerActive = w.session.currentEpisode != null,
        )
    }

    composable(
        route = NavRoutes.LIBRARY_PLAYLIST,
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
        val viewModel =
            androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.playlists.PlaylistsViewModel>(
                key = "playlist:$playlistId",
                factory = viewModelFactory(playlistId),
            )
        cx.aswin.boxlore.feature.library.playlists.PlaylistDetailScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            isPlayerActive = w.session.currentEpisode != null,
        )
    }
}
