package cx.aswin.boxlore.feature.player.v2

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.CastMediaEligibility
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.playback.setOutputVolume
import cx.aswin.boxlore.core.playback.skipToNextEpisode
import cx.aswin.boxlore.core.playback.stopCasting
import cx.aswin.boxlore.feature.player.v2.logic.calculateResponsiveHeroLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * v2 full player: immersive artwork-tinted canvas, wavy seekbar, expressive control
 * deck, and below-the-fold Up Next + Show Notes cards.
 */
data class FullPlayerDependencies(
    val playbackRepository: PlaybackRepository,
    val downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    val userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
)

data class FullPlayerDisplay(
    val colorScheme: ColorScheme,
    val isFullscreenVideo: Boolean,
    val sheetNestedScrollConnection: NestedScrollConnection,
    val isExpanded: Boolean,
    val showSwipeMinimizeTip: Boolean = false,
)

data class FullPlayerActions(
    val onFullscreenVideoChange: (Boolean) -> Unit,
    val onCollapse: () -> Unit,
    val onEpisodeInfoClick: (Episode) -> Unit,
    val onPodcastInfoClick: (Podcast) -> Unit,
    val onSwipeMinimizeTipDismissed: () -> Unit = {},
)

@Stable
internal class FullPlayerUiState {
    var showQueueSheet by mutableStateOf(false)
    var showChaptersSheet by mutableStateOf(false)
    var showSpeedSheet by mutableStateOf(false)
    var showSleepSheet by mutableStateOf(false)
    var showShareSheet by mutableStateOf(false)
    var showGenerateDialog by mutableStateOf(false)
    var showFullscreenTranscript by mutableStateOf(false)
    var showInlineTranscript by mutableStateOf(false)
    var isSyncEnabled by mutableStateOf(true)
    var isAudioOnly by mutableStateOf(false)
}

internal val FullPlayerUiStateSaver =
    listSaver<FullPlayerUiState, Boolean>(
        save = { state ->
            listOf(
                state.showQueueSheet,
                state.showChaptersSheet,
                state.showSpeedSheet,
                state.showSleepSheet,
                state.showShareSheet,
                state.showGenerateDialog,
                state.showFullscreenTranscript,
                state.showInlineTranscript,
                state.isSyncEnabled,
                state.isAudioOnly,
            )
        },
        restore = { values ->
            FullPlayerUiState().apply {
                showQueueSheet = values[0]
                showChaptersSheet = values[1]
                showSpeedSheet = values[2]
                showSleepSheet = values[3]
                showShareSheet = values[4]
                showGenerateDialog = values[5]
                showFullscreenTranscript = values[6]
                showInlineTranscript = values[7]
                isSyncEnabled = values[8]
                isAudioOnly = values[9]
            }
        },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerV2(
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    modifier: Modifier = Modifier,
) {
    val playbackRepository = dependencies.playbackRepository
    val downloadRepository = dependencies.downloadRepository
    val colorScheme = display.colorScheme
    // Position ticks are split into separate flows so only the seekbar recomposes per tick.
    val state by remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.copy(position = 0, bufferedPosition = 0) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlayerState())

    val positionFlow =
        remember(playbackRepository) {
            playbackRepository.playerState.map { it.position }.distinctUntilChanged()
        }
    val bufferedPositionFlow =
        remember(playbackRepository) {
            playbackRepository.playerState.map { it.bufferedPosition }.distinctUntilChanged()
        }

    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast ?: return
    val nextEpisode = state.queue.drop(1).firstOrNull()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val window = (context as? android.app.Activity)?.window

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val ui = rememberSaveable(episode.id, saver = FullPlayerUiStateSaver) { FullPlayerUiState() }

    val queueSnackbarHostState = remember { SnackbarHostState() }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chaptersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isVideoPodcast = episode.enclosureType?.startsWith("video/") == true
    val isVideo = isVideoPodcast && !ui.isAudioOnly && !state.playbackRoute.isRemote

    val isDownloaded by remember(episode.id) { downloadRepository.isDownloaded(episode.id) }
        .collectAsStateWithLifecycle(initialValue = false)
    val isDownloading by remember(episode.id) { downloadRepository.isDownloading(episode.id) }
        .collectAsStateWithLifecycle(initialValue = false)

    BackHandler(enabled = ui.showFullscreenTranscript) { ui.showFullscreenTranscript = false }
    BackHandler(enabled = ui.showInlineTranscript && !ui.showFullscreenTranscript) {
        ui.showInlineTranscript = false
    }

    LaunchedEffect(state.currentTranscript.isEmpty()) {
        if (state.currentTranscript.isEmpty()) ui.showInlineTranscript = false
    }

    val resolvedDark = LocalEffectiveDarkTheme.current
    SideEffect {
        window?.let { win ->
            val insetsController =
                androidx.core.view.WindowCompat
                    .getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !resolvedDark
            insetsController.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    val canGenerateTranscript =
        state.currentTranscript.isEmpty() &&
            state.autoTranscriptState != AutoTranscriptState.GENERATING &&
            state.autoTranscriptState != AutoTranscriptState.COMPLETED

    FullPlayerBody(
        model =
        FullPlayerBodyModel(
            state = state,
            episode = episode,
            podcast = podcast,
            nextEpisode = nextEpisode,
            isVideo = isVideo,
            isVideoPodcast = isVideoPodcast,
            download = FullPlayerDownloadState(isDownloaded, isDownloading),
        ),
        dependencies = dependencies,
        display = display,
        actions = actions,
        flows = FullPlayerFlows(positionFlow, bufferedPositionFlow),
        ui = ui,
        resources =
        FullPlayerBodyResources(
            scope,
            queueSnackbarHostState,
            statusBarPadding,
            navBarPadding,
            modifier,
        ),
    )

    // ------------------------------------------------------------------
    // Sheets
    // ------------------------------------------------------------------

    FullPlayerModalSheets(
        dependencies = dependencies,
        model =
        FullPlayerModalModel(
            state,
            episode,
            podcast,
            colorScheme,
            positionFlow,
            canGenerateTranscript,
        ),
        ui = ui,
        resources =
        FullPlayerModalResources(
            scope,
            queueSnackbarHostState,
            queueSheetState,
            chaptersSheetState,
            context,
        ),
        actions = actions,
    )

    // ------------------------------------------------------------------
    // Fullscreen transcript overlay
    // ------------------------------------------------------------------

    FullPlayerFullscreenOverlays(
        dependencies = dependencies,
        model = FullPlayerOverlayModel(state, episode, colorScheme, positionFlow),
        display = display,
        actions = actions,
        ui = ui,
    )
}

internal data class FullPlayerDownloadState(
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
)

internal data class FullPlayerModalModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val colorScheme: ColorScheme,
    val positionFlow: Flow<Long>,
    val canGenerateTranscript: Boolean,
)

internal data class FullPlayerOverlayModel(
    val state: PlayerState,
    val episode: Episode,
    val colorScheme: ColorScheme,
    val positionFlow: Flow<Long>,
)

@Composable
private fun FullPlayerBody(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: FullPlayerBodyResources,
) {
    Box(
        modifier =
        resources.modifier
            .fillMaxSize()
            .playerCanvas(display.colorScheme),
    ) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val finalMaxHeight = screenHeight - resources.statusBarPadding - resources.navBarPadding

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(top = resources.statusBarPadding, bottom = resources.navBarPadding),
        ) {
            PlayerTopBar(
                colorScheme = display.colorScheme,
                showSwipeMinimizeTip = display.showSwipeMinimizeTip,
                isExpanded = display.isExpanded,
                isCasting = model.state.playbackRoute.isRemote,
                canCast = CastMediaEligibility.isCastable(model.episode.audioUrl),
                actions =
                PlayerTopBarActions(
                    onSwipeMinimizeTipDismissed = actions.onSwipeMinimizeTipDismissed,
                    onCollapse = {
                        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator
                            .logAction("collapsed")
                        actions.onCollapse()
                    },
                    onShare = { ui.showShareSheet = true },
                ),
            )
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(display.sheetNestedScrollConnection),
            ) {
                val layoutValues = calculateResponsiveHeroLayout(screenWidth, finalMaxHeight, model.isVideo)
                FullPlayerScrollableContent(
                    model = model,
                    dependencies = dependencies,
                    display = display,
                    actions = actions,
                    flows = flows,
                    ui = ui,
                    resources =
                    FullPlayerScrollResources(
                        resources.scope,
                        layoutValues,
                    ),
                )
            }
        }
        SnackbarHost(
            hostState = resources.snackbarHostState,
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun FullPlayerScrollableContent(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: FullPlayerScrollResources,
) {
    val scrollState = rememberScrollState()
    // Reset scroll when collapsing so reopen always starts at the top.
    LaunchedEffect(display.isExpanded) {
        if (!display.isExpanded && scrollState.value > 0) {
            scrollState.scrollTo(0)
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 6.dp else 14.dp))
        FullPlayerHeroSection(
            model =
            FullPlayerHeroModel(
                state = model.state,
                episode = model.episode,
                podcast = model.podcast,
                nextEpisode = model.nextEpisode,
                isVideo = model.isVideo,
                dimensions = resources.layout.dimensions,
            ),
            positionFlow = flows.position,
            playbackRepository = dependencies.playbackRepository,
            display = display,
            actions = actions,
            ui = ui,
            isCompact = resources.layout.isCompact,
        )
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 10.dp else 16.dp))
        PlayerMetadata(model.episode, model.podcast, display.colorScheme, actions)
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 8.dp else 14.dp))
        FullPlayerControls(
            model =
            FullPlayerControlModel(
                state = model.state,
                episode = model.episode,
                podcast = model.podcast,
                colorScheme = display.colorScheme,
                isDownloaded = model.download.isDownloaded,
                isDownloading = model.download.isDownloading,
                isCompact = resources.layout.isCompact,
            ),
            dependencies = dependencies,
            flows = flows,
            ui = ui,
            scope = resources.scope,
        )
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 14.dp else 20.dp))
        FullPlayerSupportingContent(
            state = model.state,
            episode = model.episode,
            nextEpisode = model.nextEpisode,
            colorScheme = display.colorScheme,
            playbackRepository = dependencies.playbackRepository,
            actions = actions,
            ui = ui,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private data class FullPlayerHeroModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val nextEpisode: Episode?,
    val isVideo: Boolean,
    val dimensions: HeroDimensions,
)

internal data class FullPlayerFlows(
    val position: Flow<Long>,
    val bufferedPosition: Flow<Long>,
)

internal data class FullPlayerControlModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val colorScheme: ColorScheme,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val isCompact: Boolean,
)

@Composable
private fun FullPlayerHeroSection(
    model: FullPlayerHeroModel,
    positionFlow: Flow<Long>,
    playbackRepository: PlaybackRepository,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    ui: FullPlayerUiState,
    isCompact: Boolean,
) {
    val isRemote = model.state.playbackRoute.isRemote
    val bindings =
        FullPlayerHeroBindings(
            positionFlow = positionFlow,
            playbackRepository = playbackRepository,
            display = display,
            actions = actions,
            ui = ui,
            isCompact = isCompact,
        )
    var showCastControls by rememberSaveable { mutableStateOf(isRemote) }
    var showCastRoutePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isRemote) {
        showCastControls = isRemote
        if (!isRemote) showCastRoutePicker = false
        if (isRemote) actions.onFullscreenVideoChange(false)
    }
    val heroMode =
        resolveCastHeroDisplayMode(
            isRemote = isRemote,
            showInlineTranscript = ui.showInlineTranscript,
            showCastControls = showCastControls,
        )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = heroMode,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(tween(280), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.96f))
            },
            contentAlignment = Alignment.Center,
            label = "playerHeroMode",
            modifier = Modifier.fillMaxWidth(),
        ) { mode ->
            FullPlayerHeroModeContent(
                mode = mode,
                model = model,
                bindings = bindings,
                onChangeCastDevice = { showCastRoutePicker = true },
            )
        }
        CastHeroModeToggle(
            visible = isRemote && heroMode != CastHeroDisplayMode.TRANSCRIPT,
            showCastControls = showCastControls,
            colorScheme = display.colorScheme,
            onToggle = { showCastControls = !showCastControls },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
    CastRoutePickerOverlay(
        visible = showCastRoutePicker,
        isRemote = isRemote,
        colorScheme = display.colorScheme,
        onDismiss = { showCastRoutePicker = false },
    )
    RemoteAwareVideoModeButtons(
        visible = shouldShowVideoModeButtons(model.isVideo, heroMode),
        bindings = bindings,
    )
}

private data class FullPlayerHeroBindings(
    val positionFlow: Flow<Long>,
    val playbackRepository: PlaybackRepository,
    val display: FullPlayerDisplay,
    val actions: FullPlayerActions,
    val ui: FullPlayerUiState,
    val isCompact: Boolean,
)

@Composable
private fun FullPlayerHeroModeContent(
    mode: CastHeroDisplayMode,
    model: FullPlayerHeroModel,
    bindings: FullPlayerHeroBindings,
    onChangeCastDevice: () -> Unit,
) {
    when (mode) {
        CastHeroDisplayMode.TRANSCRIPT ->
            InlineTranscriptHero(
                content =
                InlineTranscriptContent(
                    transcript = model.state.currentTranscript,
                    positionFlow = bindings.positionFlow,
                    transcriptUrl = model.episode.transcriptUrl,
                    artworkUrl =
                    model.episode.imageUrl?.takeIf { it.isNotBlank() }
                        ?: model.podcast.imageUrl,
                ),
                colorScheme = bindings.display.colorScheme,
                isSyncEnabled = bindings.ui.isSyncEnabled,
                actions =
                InlineTranscriptActions(
                    onSyncEnabledChange = { bindings.ui.isSyncEnabled = it },
                    onSeek = { seekPosition ->
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper
                            .setSeekSource("transcript_tap")
                        bindings.playbackRepository.seekTo(seekPosition)
                    },
                    onShowArtwork = { bindings.ui.showInlineTranscript = false },
                    onFullscreen = { bindings.ui.showFullscreenTranscript = true },
                ),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(maxOf(model.dimensions.height, if (bindings.isCompact) 220.dp else 250.dp)),
            )

        CastHeroDisplayMode.CAST_CONTROLS ->
            FullPlayerCastHero(
                model =
                FullPlayerCastHeroModel(
                    route = model.state.playbackRoute,
                    dimensions = model.dimensions,
                    canSkipNext = canSkipFromCastHero(model.nextEpisode?.id),
                    colorScheme = bindings.display.colorScheme,
                ),
                actions =
                FullPlayerCastHeroActions(
                    onVolumeChange = bindings.playbackRepository::setOutputVolume,
                    onChangeDevice = onChangeCastDevice,
                    onSkipNext = {
                        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator
                            .logAction("skip_next_episode")
                        bindings.playbackRepository.skipToNextEpisode()
                    },
                    onStopCasting = bindings.playbackRepository::stopCasting,
                ),
            )

        CastHeroDisplayMode.ARTWORK ->
            ArtworkPlayerHero(
                model = model,
                positionFlow = bindings.positionFlow,
                playbackRepository = bindings.playbackRepository,
                display = bindings.display,
            )
    }
}

@Composable
private fun CastHeroModeToggle(
    visible: Boolean,
    showCastControls: Boolean,
    colorScheme: ColorScheme,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Surface(
        modifier = modifier.offset(x = (-8).dp, y = 8.dp),
        shape = CircleShape,
        color = colorScheme.surfaceContainerHigh,
        contentColor = colorScheme.onSurface,
        shadowElevation = 4.dp,
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (showCastControls) Icons.Rounded.Image else Icons.Rounded.CastConnected,
                contentDescription = if (showCastControls) "Show artwork" else "Show Cast controls",
            )
        }
    }
}

@Composable
private fun CastRoutePickerOverlay(
    visible: Boolean,
    isRemote: Boolean,
    colorScheme: ColorScheme,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    MaterialTheme(colorScheme = colorScheme) {
        CastRoutePickerSheet(
            isCasting = isRemote,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun RemoteAwareVideoModeButtons(
    visible: Boolean,
    bindings: FullPlayerHeroBindings,
) {
    if (!visible) return
    VideoModeButtons(
        isAudioOnly = bindings.ui.isAudioOnly,
        colorScheme = bindings.display.colorScheme,
        onAudioOnlyChange = { bindings.ui.isAudioOnly = it },
        onFullscreenClick = { bindings.actions.onFullscreenVideoChange(true) },
    )
}

@Composable
private fun ArtworkPlayerHero(
    model: FullPlayerHeroModel,
    positionFlow: Flow<Long>,
    playbackRepository: PlaybackRepository,
    display: FullPlayerDisplay,
) {
    PlayerHero(
        artwork =
        PlayerHeroArtwork(
            episodeId = model.episode.id,
            artworkUrl = model.episode.imageUrl?.takeIf { it.isNotBlank() } ?: model.podcast.imageUrl,
            nextArtworkUrl =
            model.nextEpisode?.imageUrl?.takeIf { it.isNotBlank() }
                ?: model.nextEpisode?.podcastImageUrl?.takeIf { it.isNotBlank() },
            nextEpisodeTitle = model.nextEpisode?.title,
            chapterArtFlow =
            remember(model.state.currentChapters, positionFlow) {
                chapterArtFlow(positionFlow, model.state.currentChapters)
            },
        ),
        playback =
        PlayerHeroPlayback(
            isPlaying = model.state.isPlaying,
            isVideo = model.isVideo,
            isFullscreenVideo = display.isFullscreenVideo,
            controller = playbackRepository.controller,
            isExpanded = display.isExpanded,
        ),
        dimensions = model.dimensions,
        colorScheme = display.colorScheme,
        onSkipNextEpisode = {
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator
                .logAction("skip_next_episode")
            playbackRepository.skipToNextEpisode()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
