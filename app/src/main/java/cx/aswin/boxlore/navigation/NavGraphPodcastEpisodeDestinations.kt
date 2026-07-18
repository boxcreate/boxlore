package cx.aswin.boxlore.navigation

import android.Manifest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import cx.aswin.boxlore.feature.briefing.BriefingRoute
import cx.aswin.boxlore.feature.home.HomeRoute
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun androidx.navigation.NavGraphBuilder.addPodcastDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs
    val queueManager = w.queueManager

    // -----------------------------------------------------------------------
    // Podcast info (+ deep links)
    // -----------------------------------------------------------------------
    composable(
        route = "podcast/{podcastId}?entryPoint={entryPoint}&genre={genre}&depth={depth}&query={query}",
        arguments = listOf(
            navArgument("podcastId") { type = NavType.StringType },
            navArgument("entryPoint") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("genre") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("depth") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "boxlore://podcast/{podcastId}" },
            navDeepLink { uriPattern = "boxcast://podcast/{podcastId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=podcast&id={podcastId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=podcast&id={podcastId}" },
        ),
    ) { backStackEntry ->
        val podcastId = backStackEntry.arguments?.getString("podcastId") ?: return@composable
        val briefingRegion = briefingRegionFromPodcastId(podcastId)
        if (briefingRegion != null) {
            LaunchedEffect(podcastId) {
                navController.navigate("briefing?region=$briefingRegion") {
                    popUpTo("podcast/{podcastId}?entryPoint={entryPoint}&genre={genre}&depth={depth}&query={query}") {
                        inclusive = true
                    }
                }
            }
            return@composable
        }

        val entryPoint = backStackEntry.arguments?.getString("entryPoint")
        val genre = backStackEntry.arguments?.getString("genre")
        val depth = backStackEntry.arguments?.getString("depth")?.toIntOrNull()
        val query = backStackEntry.arguments?.getString("query")

        val infoSharedDeps = cx.aswin.boxlore.feature.info.InfoSharedDeps(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            queueManager = queueManager,
            localCatalog = container.localCatalogPort,
            episodeOfflineLookup = container.episodeOfflineLookupPort,
        )
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.info.PodcastInfoViewModel>(
            factory = cx.aswin.boxlore.feature.info.InfoViewModelAssembler.podcastInfoFactory(
                application = application,
                deps = infoSharedDeps,
                subscriptionRepository = subscriptionRepository,
                rssRepository = container.rssPodcastRepository,
                userPrefs = userPrefs,
                routeArgs = cx.aswin.boxlore.feature.info.PodcastInfoRouteArgs(
                    entryPoint = entryPoint,
                    genreFilter = genre,
                    scrollDepth = depth,
                    searchQuery = query,
                ),
            ),
        )

        val isPlayerVisible by remember(playbackRepository) {
            playbackRepository.playerState.map { it.currentEpisode != null }.distinctUntilChanged()
        }.collectAsState(initial = false)

        cx.aswin.boxlore.feature.info.PodcastInfoScreen(
            podcastId = podcastId,
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            bottomContentPadding = miniPlayerBottomPadding(isPlayerVisible),
            onPodcastClick = { pId ->
                navController.navigate("podcast/${android.net.Uri.encode(pId)}?entryPoint=podroll")
            },
            onEpisodeClick = { episode, entryPointStr, index ->
                val state = viewModel.uiState.value
                navigatePodcastInfoEpisode(
                    navController = navController,
                    episode = episode,
                    entryPointStr = entryPointStr,
                    index = index,
                    podcastId = podcastIdFromInfoUiState(state, podcastId),
                    podcastTitle = podcastTitleFromInfoUiState(state, "Podcast"),
                )
            },
            onPlayEpisode = { episode ->
                val state = viewModel.uiState.value as? cx.aswin.boxlore.feature.info.PodcastInfoUiState.Success
                    ?: return@PodcastInfoScreen
                queueManager.playEpisode(episode, state.podcast)
            },
        )
    }
}

internal fun androidx.navigation.NavGraphBuilder.addEpisodeFullPathDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val userPrefs = w.userPrefs
    val queueManager = w.queueManager
    val scope = w.scope
    val hasSeenMarkPlayedTip = w.session.hasSeenMarkPlayedTip

    // -----------------------------------------------------------------------
    // Episode info — full path route
    // -----------------------------------------------------------------------
    composable(
        route = "episode/{episodeId}/{episodeTitle}/{episodeDescription}/{episodeImageUrl}/{episodeAudioUrl}/{episodeDuration}/{podcastId}/{podcastTitle}?entryPoint={entryPoint}&vibeId={vibeId}&carouselPosition={carouselPosition}",
        arguments = listOf(
            navArgument("episodeId") { type = NavType.StringType },
            navArgument("episodeTitle") { type = NavType.StringType },
            navArgument("episodeDescription") { type = NavType.StringType },
            navArgument("episodeImageUrl") { type = NavType.StringType },
            navArgument("episodeAudioUrl") { type = NavType.StringType },
            navArgument("episodeDuration") { type = NavType.IntType },
            navArgument("podcastId") { type = NavType.StringType },
            navArgument("podcastTitle") { type = NavType.StringType },
            navArgument("entryPoint") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("vibeId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("carouselPosition") { type = NavType.IntType; defaultValue = -1 },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments ?: return@composable
        val episodeId = args.getString("episodeId") ?: ""
        val briefingRegion = briefingRegionFromEpisodeId(episodeId)
        if (briefingRegion != null) {
            LaunchedEffect(episodeId) {
                navController.navigate("briefing?region=$briefingRegion") {
                    popUpTo(
                        "episode/{episodeId}/{episodeTitle}/{episodeDescription}/{episodeImageUrl}/{episodeAudioUrl}/{episodeDuration}/{podcastId}/{podcastTitle}?entryPoint={entryPoint}&vibeId={vibeId}&carouselPosition={carouselPosition}",
                    ) { inclusive = true }
                }
            }
            return@composable
        }

        val episodeInfoDeps = cx.aswin.boxlore.feature.info.InfoSharedDeps(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            queueManager = queueManager,
            localCatalog = container.localCatalogPort,
            episodeOfflineLookup = container.episodeOfflineLookupPort,
        )
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.info.EpisodeInfoViewModel>(
            factory = cx.aswin.boxlore.feature.info.InfoViewModelAssembler.episodeInfoFactory(
                application = application,
                deps = episodeInfoDeps,
                userPrefs = userPrefs,
            ),
        )

        val podcastId = decodeNavArg(args.getString("podcastId"))
        val podcastTitle = decodeNavArg(args.getString("podcastTitle"))
        val episodeTitle = decodeNavArg(args.getString("episodeTitle"))
        val episodeImageUrl = decodeNavArg(args.getString("episodeImageUrl"))
        val episodeAudioUrl = decodeNavArg(args.getString("episodeAudioUrl"))
        val episodeDuration = args.getInt("episodeDuration")
        val entryPoint = args.getString("entryPoint")
        val vibeId = decodeNavArg(args.getString("vibeId"))
        val carouselPosition = args.getInt("carouselPosition", -1)
        val playContext = entryPointBundle(entryPoint, vibeId, carouselPosition)

        cx.aswin.boxlore.feature.info.EpisodeInfoScreen(
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            episodeDescription = decodeNavArg(args.getString("episodeDescription")),
            episodeImageUrl = episodeImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            episodeDuration = episodeDuration,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onPodcastClick = { pId ->
                navController.navigate("podcast/${android.net.Uri.encode(pId)}?entryPoint=episode_info")
            },
            onEpisodeClick = { ep ->
                navigateRelatedEpisode(navController, ep, podcastId, podcastTitle)
            },
            onPlay = {
                val episode = cx.aswin.boxlore.core.model.Episode(
                    id = episodeId,
                    title = episodeTitle,
                    description = "",
                    imageUrl = episodeImageUrl,
                    audioUrl = episodeAudioUrl,
                    duration = episodeDuration,
                    publishedDate = 0L,
                )
                val podcast = cx.aswin.boxlore.core.model.Podcast(
                    id = podcastId,
                    title = podcastTitle,
                    artist = "",
                    imageUrl = "",
                    description = "",
                    genre = "",
                )
                queueManager.playEpisode(episode, podcast, entryPointContext = playContext)
            },
            entryPointContext = playContext,
            showMarkPlayedTip = !hasSeenMarkPlayedTip,
            onMarkPlayedTipDismissed = { scope.launch { userPrefs.markMarkPlayedTipSeen() } },
        )
    }
}

internal fun androidx.navigation.NavGraphBuilder.addEpisodeDeepLinkDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val userPrefs = w.userPrefs
    val queueManager = w.queueManager

    // -----------------------------------------------------------------------
    // Episode info — simplified deep-link route
    // -----------------------------------------------------------------------
    composable(
        route = "episode/{episodeId}?entryPoint={entryPoint}&t={t}&start={start}&end={end}&autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}",
        arguments = listOf(
            navArgument("episodeId") { type = NavType.StringType },
            navArgument("entryPoint") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("t") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("start") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("end") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("autoplay") { type = NavType.StringType; nullable = true; defaultValue = "true" },
            navArgument("podcastId") { type = NavType.StringType; nullable = true; defaultValue = "" },
            navArgument("podcastTitle") { type = NavType.StringType; nullable = true; defaultValue = "" },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?podcastId={podcastId}&podcastTitle={podcastTitle}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}?t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}?autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}&t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}&t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}&t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}&t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}" },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments ?: return@composable
        val episodeId = args.getString("episodeId") ?: ""
        val t = args.getString("t")?.toLongOrNull()
        val start = args.getString("start")?.toLongOrNull()
        val autoplay = args.getString("autoplay") ?: "true"
        val podcastIdArg = args.getString("podcastId") ?: ""
        val podcastTitleArg = args.getString("podcastTitle") ?: ""

        val deepLinkEpisodeDeps = cx.aswin.boxlore.feature.info.InfoSharedDeps(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            queueManager = queueManager,
            localCatalog = container.localCatalogPort,
            episodeOfflineLookup = container.episodeOfflineLookupPort,
        )
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.info.EpisodeInfoViewModel>(
            factory = cx.aswin.boxlore.feature.info.InfoViewModelAssembler.episodeInfoFactory(
                application = application,
                deps = deepLinkEpisodeDeps,
                userPrefs = userPrefs,
            ),
        )

        LaunchedEffect(episodeId, podcastIdArg, podcastTitleArg) {
            viewModel.loadEpisode(episodeId = episodeId, podcastId = podcastIdArg, podcastTitle = podcastTitleArg)
        }

        val coroutineScope = rememberCoroutineScope()
        val state by viewModel.uiState.collectAsState()

        LaunchedEffect(state, t, start, autoplay) {
            val success = state as? cx.aswin.boxlore.feature.info.EpisodeInfoUiState.Success ?: return@LaunchedEffect
            autoplayDeepLinkEpisodeIfNeeded(
                playbackRepository = playbackRepository,
                queueManager = queueManager,
                localCatalog = container.localCatalogPort,
                episodeId = episodeId,
                autoplay = autoplay,
                t = t,
                start = start,
                success = success,
            )
        }

        val isPlayerVisible by remember(playbackRepository) {
            playbackRepository.playerState.map { it.currentEpisode != null }.distinctUntilChanged()
        }.collectAsState(initial = false)

        val successState = state as? cx.aswin.boxlore.feature.info.EpisodeInfoUiState.Success

        cx.aswin.boxlore.feature.info.EpisodeInfoScreen(
            episodeId = episodeId,
            episodeTitle = successState?.episode?.title ?: "",
            episodeDescription = successState?.episode?.description ?: "",
            episodeImageUrl = successState?.episode?.imageUrl ?: "",
            episodeAudioUrl = successState?.episode?.audioUrl ?: "",
            episodeDuration = successState?.episode?.duration ?: 0,
            podcastId = successState?.podcastId ?: "",
            podcastTitle = successState?.podcastTitle ?: "Podcast",
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onPodcastClick = { pId ->
                navController.navigate("podcast/${android.net.Uri.encode(pId)}?entryPoint=episode_info")
            },
            onEpisodeClick = { ep ->
                navigateRelatedEpisode(
                    navController,
                    ep,
                    successState?.podcastId ?: "",
                    successState?.podcastTitle ?: "Podcast",
                )
            },
            onPlay = {
                val current = successState ?: return@EpisodeInfoScreen
                coroutineScope.launch {
                    val podcast = resolveLocalOrFallbackPodcast(
                        localCatalog = container.localCatalogPort,
                        podcastId = current.podcastId,
                        podcastTitle = current.podcastTitle,
                        fallbackImageUrl = current.episode.podcastImageUrl ?: "",
                    )
                    queueManager.playEpisode(current.episode, podcast)
                }
            },
            bottomContentPadding = miniPlayerBottomPadding(isPlayerVisible),
        )
    }
}
