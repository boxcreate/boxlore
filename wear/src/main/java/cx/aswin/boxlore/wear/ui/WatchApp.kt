package cx.aswin.boxlore.wear.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.WatchContainer
import cx.aswin.boxlore.wear.data.StoragePolicy
import cx.aswin.boxlore.wear.data.WatchDownloadProblem
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.ui.components.LocalPlayingEpisode
import cx.aswin.boxlore.wear.ui.components.PlayingEpisode
import cx.aswin.boxlore.wear.ui.home.HomeLink
import cx.aswin.boxlore.wear.ui.home.HomeScreen
import cx.aswin.boxlore.wear.ui.lists.EpisodeDetail
import cx.aswin.boxlore.wear.ui.lists.EpisodeListScreen
import cx.aswin.boxlore.wear.ui.lists.ListEmpty
import cx.aswin.boxlore.wear.ui.player.PlayWhereScreen
import cx.aswin.boxlore.wear.ui.player.PlayerActions
import cx.aswin.boxlore.wear.ui.player.PlayerMoreScreen
import cx.aswin.boxlore.wear.ui.player.PlayerScreen
import cx.aswin.boxlore.wear.ui.player.SleepTimerScreen
import cx.aswin.boxlore.wear.ui.player.SpeedScreen
import cx.aswin.boxlore.wear.ui.search.SearchResultsScreen
import cx.aswin.boxlore.wear.ui.search.SearchScreen
import cx.aswin.boxlore.wear.ui.settings.SettingsScreen
import cx.aswin.boxlore.wear.ui.show.EpisodeActions
import cx.aswin.boxlore.wear.ui.show.EpisodeScreen
import cx.aswin.boxlore.wear.ui.show.ShowScreen
import cx.aswin.boxlore.wear.ui.shows.YourShowsScreen

/** Route names. Arguments are appended as path segments. */
internal object WatchRoutes {
    const val HOME = "home"
    const val SHOWS = "shows"
    const val JUMP_BACK_IN = "jump_back_in"
    const val NEW_EPISODES = "new_episodes"
    const val UP_NEXT = "up_next"
    const val DOWNLOADS = "downloads"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val PLAYER_MORE = "player_more"
    const val SLEEP_TIMER = "sleep_timer"
    const val SPEED = "speed"
    const val ARG = "arg"
    const val SHOW = "show/{$ARG}"
    const val EPISODE = "episode/{$ARG}"
    const val RESULTS = "results/{$ARG}"
    const val PLAY_WHERE = "play_where/{$ARG}"

    /** Launch extra naming the screen to open on top of Home. Only [launchable] routes are honoured. */
    const val EXTRA_ROUTE = "cx.aswin.boxlore.wear.extra.ROUTE"

    /** Launch extra set by the tile and the watch-face shortcut: the ID of the episode to carry on with. */
    const val EXTRA_RESUME = "cx.aswin.boxlore.wear.extra.RESUME"

    private val launchable =
        setOf(SHOWS, JUMP_BACK_IN, NEW_EPISODES, UP_NEXT, DOWNLOADS, SEARCH, SETTINGS, PLAYER, PLAYER_MORE, SLEEP_TIMER, SPEED)

    /** The route a launch [intent] asks for, or null for a plain launch or an unknown route. */
    fun fromLaunch(intent: Intent?): String? = launchRoute(intent?.getStringExtra(EXTRA_ROUTE))

    fun launchRoute(requested: String?): String? = requested?.takeIf { it in launchable }

    fun show(showId: String): String = "show/${Uri.encode(showId)}"

    fun episode(episodeId: String): String = "episode/${Uri.encode(episodeId)}"

    fun results(query: String): String = "results/${Uri.encode(query)}"

    fun playWhere(episodeId: String): String = "play_where/${Uri.encode(episodeId)}"

    fun forHomeLink(link: HomeLink): String? =
        when (link) {
            HomeLink.Shows -> SHOWS
            HomeLink.JumpBackIn -> JUMP_BACK_IN
            HomeLink.NewEpisodes -> NEW_EPISODES
            HomeLink.Search -> SEARCH
            HomeLink.Downloads -> DOWNLOADS
            HomeLink.Settings -> SETTINGS
            // Daily Mix starts playing instead of opening a list.
            HomeLink.DailyMix -> null
        }
}

@Composable
internal fun WatchApp(
    container: WatchContainer,
    requestedRoute: String? = null,
    onRouteShown: () -> Unit = {},
) {
    val navController = rememberSwipeDismissableNavController()
    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            navController.navigate(requestedRoute) { launchSingleTop = true }
            onRouteShown()
        }
    }
    val swipeState = rememberSwipeToDismissBoxState()
    val navHostState = rememberSwipeDismissableNavHostState(swipeState)
    val inPlayer by container.playback.nowPlaying.collectAsStateWithLifecycle()
    val playingEpisode = inPlayer?.let { PlayingEpisode(it.episode.id, it.isPlaying) }
    AppScaffold {
        CompositionLocalProvider(LocalPlayingEpisode provides playingEpisode) {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = WatchRoutes.HOME,
                state = navHostState,
            ) {
                homeRoutes(container, navController, swipeState)
                listRoutes(container, navController)
                detailRoutes(container, navController)
                playerRoutes(container, navController)
            }
        }
    }
}

private fun NavGraphBuilder.homeRoutes(
    container: WatchContainer,
    navController: NavHostController,
    swipeState: SwipeToDismissBoxState,
) {
    composable(WatchRoutes.HOME) {
        val nowPlaying by container.playback.nowPlaying.collectAsStateWithLifecycle()
        val phoneLinked by container.library.phoneLinked.collectAsStateWithLifecycle()
        val dailyMix by container.library.dailyMix.collectAsStateWithLifecycle()
        HomeScreen(
            nowPlaying = nowPlaying,
            phoneLinked = phoneLinked,
            hasDailyMix = dailyMix.isNotEmpty() || container.playback.hasDailyMix,
            onOpenPlayer = { navController.navigate(WatchRoutes.PLAYER) },
            onOpen = { link ->
                val route = WatchRoutes.forHomeLink(link)
                if (route != null) {
                    navController.navigate(route)
                } else {
                    container.playback.playDailyMix()
                    navController.navigate(WatchRoutes.PLAYER)
                }
            },
        )
    }
    composable(WatchRoutes.SHOWS) {
        val shows by container.library.shows.collectAsStateWithLifecycle()
        val layout by container.settings.showsLayout.collectAsStateWithLifecycle()
        YourShowsScreen(layout, shows, swipeState) { navController.navigate(WatchRoutes.show(it)) }
    }
    composable(WatchRoutes.SETTINGS) {
        val layout by container.settings.showsLayout.collectAsStateWithLifecycle()
        val limitMb by container.settings.storageLimitMb.collectAsStateWithLifecycle()
        SettingsScreen(layout, container.settings::setShowsLayout, limitMb, container.settings::setStorageLimitMb)
    }
    composable(WatchRoutes.SEARCH) {
        SearchScreen(
            // The results take the search route's place, so Back from them returns to Home.
            onQuery = { query -> navController.navigate(WatchRoutes.results(query)) { popUpTo(WatchRoutes.SEARCH) { inclusive = true } } },
            onCancel = { navController.popBackStack() },
        )
    }
    composable(WatchRoutes.RESULTS) { entry ->
        val query = entry.arguments?.getString(WatchRoutes.ARG).orEmpty()
        val result by produceState<WatchSearchResult?>(initialValue = null, query) {
            value = container.library.search(query)
        }
        SearchResultsScreen(
            query = query,
            result = result,
            onShow = { navController.navigate(WatchRoutes.show(it)) },
            onDownloads = { navController.navigate(WatchRoutes.DOWNLOADS) },
        )
    }
}

private fun NavGraphBuilder.listRoutes(container: WatchContainer, navController: NavHostController) {
    val openEpisode: (String) -> Unit = { navController.navigate(WatchRoutes.episode(it)) }
    composable(WatchRoutes.JUMP_BACK_IN) {
        val episodes by container.library.jumpBackIn.collectAsStateWithLifecycle()
        EpisodeListScreen(
            title = stringResource(R.string.wear_jump_back_in),
            episodes = episodes,
            detail = EpisodeDetail.TimeLeft,
            empty = ListEmpty(Icons.Rounded.History, R.string.wear_jump_back_in_empty, R.string.wear_jump_back_in_empty_body),
            onEpisode = openEpisode,
        )
    }
    composable(WatchRoutes.NEW_EPISODES) {
        val episodes by container.library.newEpisodes.collectAsStateWithLifecycle()
        EpisodeListScreen(
            title = stringResource(R.string.wear_new_episodes),
            episodes = episodes,
            detail = EpisodeDetail.Length,
            empty = ListEmpty(Icons.Rounded.AutoAwesome, R.string.wear_new_episodes_empty),
            onEpisode = openEpisode,
        )
    }
    composable(WatchRoutes.UP_NEXT) {
        val own by container.library.upNext.collectAsStateWithLifecycle()
        // While the phone's player is the one shown, what comes next is the phone's queue.
        val onPhone by container.playback.phoneUpNext.collectAsStateWithLifecycle()
        EpisodeListScreen(
            title = stringResource(R.string.wear_up_next),
            subtitle = if (onPhone != null) stringResource(R.string.wear_home_on_phone) else null,
            episodes = onPhone ?: own,
            detail = EpisodeDetail.Length,
            empty = ListEmpty(Icons.AutoMirrored.Rounded.QueueMusic, R.string.wear_up_next_empty),
            onEpisode = openEpisode,
        )
    }
    composable(WatchRoutes.DOWNLOADS) {
        val episodes by container.library.downloads.collectAsStateWithLifecycle()
        EpisodeListScreen(
            title = stringResource(R.string.wear_downloads),
            subtitle = storageUsage(container),
            episodes = episodes,
            detail = EpisodeDetail.LengthAndSize,
            empty = ListEmpty(Icons.Rounded.DownloadDone, R.string.wear_downloads_empty, R.string.wear_downloads_empty_body),
            onEpisode = openEpisode,
        )
    }
}

/** "30 MB of 500 MB": what downloads take against the listener's limit. */
@Composable
private fun storageUsage(container: WatchContainer): String {
    val used by container.library.storedBytes.collectAsStateWithLifecycle()
    val limitMb by container.settings.storageLimitMb.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(
        R.string.wear_downloads_usage,
        WatchFormat.megabytes(used, locale),
        WatchFormat.megabytes(StoragePolicy.limitBytes(limitMb), locale),
    )
}

private fun NavGraphBuilder.detailRoutes(container: WatchContainer, navController: NavHostController) {
    composable(WatchRoutes.SHOW) { entry ->
        val showId = entry.arguments?.getString(WatchRoutes.ARG).orEmpty()
        val show by remember(showId) { container.library.show(showId) }.collectAsStateWithLifecycle(null)
        val episodes by remember(showId) { container.library.episodes(showId) }.collectAsStateWithLifecycle(emptyList())
        show?.let { current ->
            ShowScreen(
                show = current,
                episodes = episodes,
                onToggleSubscribed = { container.library.setSubscribed(current.id, !current.subscribed) },
                onEpisode = { navController.navigate(WatchRoutes.episode(it)) },
            )
        }
    }
    composable(WatchRoutes.EPISODE) { entry ->
        val episodeId = entry.arguments?.getString(WatchRoutes.ARG).orEmpty()
        val episode by remember(episodeId) { container.library.episode(episodeId) }.collectAsStateWithLifecycle(null)
        var problem by remember { mutableStateOf<WatchDownloadProblem?>(null) }
        LaunchedEffect(Unit) { container.library.downloadProblems.collect { problem = it } }
        episode?.let { current ->
            val stored = current.download != cx.aswin.boxlore.wear.model.WatchDownload.None
            EpisodeScreen(
                episode = current,
                problem = problem,
                onProblemSeen = { problem = null },
                actions =
                    EpisodeActions(
                        onPlay = {
                            // With the phone playing, Play here could mean either device: ask.
                            if (container.playback.phoneIsPlaying) {
                                navController.navigate(WatchRoutes.playWhere(current.id))
                            } else {
                                container.playback.play(current.id)
                                navController.navigate(WatchRoutes.PLAYER)
                            }
                        },
                        onPause = { container.playback.togglePlayPause() },
                        onToggleDownload = { container.library.setDownloaded(current.id, !stored) },
                        onPlayNext = { container.library.playNext(current.id) },
                        onMarkPlayed = { container.library.markPlayed(current.id) },
                    ),
            )
        }
    }
}

private fun NavGraphBuilder.playerRoutes(container: WatchContainer, navController: NavHostController) {
    val playback = container.playback
    composable(WatchRoutes.PLAY_WHERE) { entry ->
        val episodeId = entry.arguments?.getString(WatchRoutes.ARG).orEmpty()
        val episode by remember(episodeId) { container.library.episode(episodeId) }.collectAsStateWithLifecycle(null)
        episode?.let { current ->
            // Either choice ends on the player, which shows the device that was picked.
            val openPlayer = { navController.navigate(WatchRoutes.PLAYER) { popUpTo(WatchRoutes.PLAY_WHERE) { inclusive = true } } }
            PlayWhereScreen(
                episodeTitle = current.title,
                onWatch = {
                    playback.play(current.id)
                    openPlayer()
                },
                onPhone = {
                    playback.playOnPhone(current)
                    openPlayer()
                },
            )
        }
    }
    composable(WatchRoutes.PLAYER) {
        val nowPlaying by playback.nowPlaying.collectAsStateWithLifecycle()
        nowPlaying?.let { current ->
            PlayerScreen(
                nowPlaying = current,
                actions =
                    PlayerActions(
                        onTogglePlay = playback::togglePlayPause,
                        onSeekBack = playback::seekBack,
                        onSeekForward = playback::seekForward,
                        onUpNext = { navController.navigate(WatchRoutes.UP_NEXT) },
                        onMore = { navController.navigate(WatchRoutes.PLAYER_MORE) },
                        onSwitchPlace = playback::switchPlace.takeIf { playback.canSwitchPlace },
                    ),
            )
        }
    }
    composable(WatchRoutes.PLAYER_MORE) {
        val nowPlaying by playback.nowPlaying.collectAsStateWithLifecycle()
        nowPlaying?.let { current ->
            PlayerMoreScreen(
                nowPlaying = current,
                onSpeed = { navController.navigate(WatchRoutes.SPEED) },
                onSleepTimer = { navController.navigate(WatchRoutes.SLEEP_TIMER) },
                onSwitchPlace = playback::switchPlace.takeIf { playback.canSwitchPlace },
            )
        }
    }
    composable(WatchRoutes.SLEEP_TIMER) {
        val nowPlaying by playback.nowPlaying.collectAsStateWithLifecycle()
        nowPlaying?.let { current ->
            SleepTimerScreen(selected = current.sleepTimer) { choice ->
                playback.setSleepTimer(choice)
                navController.popBackStack()
            }
        }
    }
    composable(WatchRoutes.SPEED) {
        val nowPlaying by playback.nowPlaying.collectAsStateWithLifecycle()
        nowPlaying?.let { current -> SpeedScreen(speed = current.speed, onSpeed = playback::setSpeed) }
    }
}
