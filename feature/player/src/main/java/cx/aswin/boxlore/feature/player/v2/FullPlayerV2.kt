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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreConfigurationWindowSize
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneBottomInset
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneTopInset
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.CastMediaEligibility
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.playback.setOutputVolume
import cx.aswin.boxlore.core.playback.skipBackward
import cx.aswin.boxlore.core.playback.skipForward
import cx.aswin.boxlore.core.playback.skipToNextEpisode
import cx.aswin.boxlore.core.playback.stopCasting
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.v2.logic.PlayerMetadataStylingLogic
import cx.aswin.boxlore.feature.player.v2.logic.calculateResponsiveHeroLayout
import cx.aswin.boxlore.feature.player.v2.logic.castHeroDimensions
import cx.aswin.boxlore.feature.player.v2.logic.sidewaysHeroSize
import cx.aswin.boxlore.feature.player.v2.logic.useMinimalPlayer
import cx.aswin.boxlore.feature.player.v2.logic.usesSidewaysPhoneColumns
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

/**
 * How the full player is shown. [visibleHeight] is the part of its container on screen; the container
 * itself runs past the screen's foot, so full-screen overlays such as the transcript fit this height.
 */
data class FullPlayerDisplay(
    val colorScheme: ColorScheme,
    val isFullscreenVideo: Boolean,
    val sheetNestedScrollConnection: NestedScrollConnection,
    val isExpanded: Boolean,
    val showSwipeMinimizeTip: Boolean = false,
    val visibleHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    val isContextPane: Boolean = false,
)

data class FullPlayerActions(
    val onFullscreenVideoChange: (Boolean) -> Unit,
    val onCollapse: () -> Unit,
    val onEpisodeInfoClick: (Episode) -> Unit,
    val onPodcastInfoClick: (Podcast) -> Unit,
    val onSwipeMinimizeTipDismissed: () -> Unit = {},
    val onOpenImmersivePlayer: (() -> Unit)? = null,
    val onDockPlayer: (() -> Unit)? = null,
)

@Stable
internal class FullPlayerUiState {
    var showQueueSheet by mutableStateOf(false)
    var showChaptersSheet by mutableStateOf(false)
    var showSpeedSheet by mutableStateOf(false)
    var showSleepSheet by mutableStateOf(false)
    var showShareSheet by mutableStateOf(false)
    var showFullscreenTranscript by mutableStateOf(false)
    var showInlineTranscript by mutableStateOf(false)
    var isSyncEnabled by mutableStateOf(true)
    var isAudioOnly by mutableStateOf(false)
    var showRemoveDownloadDialog by mutableStateOf(false)
    var supportingTab by mutableStateOf(PlayerSupportingTab.UpNext)
    var supportingContentUsesTablet by mutableStateOf(false)
    var restoreSupportingContentOnCompact by mutableStateOf(false)

    /** The quick-action tray's open panel, kept when the player moves between phone and tablet layouts. */
    val quickControlModeState = mutableStateOf(QuickControlMode.ACTIONS)
    var quickControlMode by quickControlModeState

    /** Whether the Cast hero shows receiver controls, and the route it last followed ([castRouteRemote]). */
    var showCastControls by mutableStateOf(false)
    var castRouteRemote by mutableStateOf(false)
    val notesExpandedState = mutableStateOf(false)
    var notesExpanded by notesExpandedState

    /** Follows a Cast connect or disconnect once; a layout change with the same route keeps the listener's toggle. */
    fun followCastRoute(isRemote: Boolean): Boolean {
        if (isRemote == castRouteRemote) return false
        castRouteRemote = isRemote
        showCastControls = isRemote
        return true
    }

    fun selectSupportingTab(tab: PlayerSupportingTab, restoreOnCompact: Boolean = true) {
        supportingTab = tab
        restoreSupportingContentOnCompact = restoreOnCompact
    }

    fun updateSupportingContent(tablet: Boolean) {
        if (tablet) {
            val openSurface = when {
                showInlineTranscript -> PlayerSupportingTab.Transcript
                showChaptersSheet -> PlayerSupportingTab.Chapters
                showQueueSheet -> PlayerSupportingTab.UpNext
                else -> null
            }
            openSurface?.let { selectSupportingTab(it) }
            showInlineTranscript = false
            showChaptersSheet = false
            showQueueSheet = false
        } else if (supportingContentUsesTablet) {
            showInlineTranscript = restoreSupportingContentOnCompact && supportingTab == PlayerSupportingTab.Transcript
            showChaptersSheet = restoreSupportingContentOnCompact && supportingTab == PlayerSupportingTab.Chapters
            showQueueSheet = restoreSupportingContentOnCompact && supportingTab == PlayerSupportingTab.UpNext
            restoreSupportingContentOnCompact = false
        }
        supportingContentUsesTablet = tablet
    }
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
                state.showFullscreenTranscript,
                state.showInlineTranscript,
                state.isSyncEnabled,
                state.isAudioOnly,
                state.showRemoveDownloadDialog,
                state.supportingTab == PlayerSupportingTab.Chapters,
                state.supportingTab == PlayerSupportingTab.Transcript,
                state.supportingTab == PlayerSupportingTab.Notes,
                state.supportingContentUsesTablet,
                state.restoreSupportingContentOnCompact,
                // Appended later; lists saved before them restore with defaults.
                state.quickControlMode == QuickControlMode.SPEED,
                state.quickControlMode == QuickControlMode.SLEEP,
                state.showCastControls,
                state.castRouteRemote,
                state.notesExpanded,
            )
        },
        restore = { values ->
            FullPlayerUiState().apply {
                showQueueSheet = values.getOrElse(0) { false }
                showChaptersSheet = values.getOrElse(1) { false }
                showSpeedSheet = values.getOrElse(2) { false }
                showSleepSheet = values.getOrElse(3) { false }
                showShareSheet = values.getOrElse(4) { false }
                showFullscreenTranscript = values.getOrElse(5) { false }
                showInlineTranscript = values.getOrElse(6) { false }
                isSyncEnabled = values.getOrElse(7) { true }
                isAudioOnly = values.getOrElse(8) { false }
                showRemoveDownloadDialog = values.getOrElse(9) { false }
                supportingTab = when {
                    values.getOrElse(10) { false } -> PlayerSupportingTab.Chapters
                    values.getOrElse(11) { false } -> PlayerSupportingTab.Transcript
                    values.getOrElse(12) { false } -> PlayerSupportingTab.Notes
                    else -> PlayerSupportingTab.UpNext
                }
                supportingContentUsesTablet = values.getOrElse(13) { false }
                restoreSupportingContentOnCompact = values.getOrElse(14) { false }
                quickControlMode = when {
                    values.getOrElse(15) { false } -> QuickControlMode.SPEED
                    values.getOrElse(16) { false } -> QuickControlMode.SLEEP
                    else -> QuickControlMode.ACTIONS
                }
                showCastControls = values.getOrElse(17) { false }
                castRouteRemote = values.getOrElse(18) { false }
                notesExpanded = values.getOrElse(19) { false }
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
    }.collectAsStateWithLifecycle(initialValue = rememberInitialPlayerState(playbackRepository))

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

    // Measured from the pane the player is in, so insets the shell already reserves are not added twice.
    val statusBarPadding = boxlorePaneTopInset()
    val navBarPadding = boxlorePaneBottomInset()

    val localUi = rememberSaveable(episode.id, saver = FullPlayerUiStateSaver) { FullPlayerUiState() }
    val ui = LocalPlayerPresentationStore.current?.uiFor(episode.id, localUi) ?: localUi
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    val fullscreenRequest = LocalPlayerFullscreenRequest.current.takeIf { tablet }
    val playerActions = fullscreenRequest?.let { request -> actions.copy(onFullscreenVideoChange = { request(PlayerFullscreenOverlay.Video, it) }) } ?: actions
    SideEffect { ui.updateSupportingContent(tablet) }

    val queueSnackbarHostState = remember { SnackbarHostState() }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chaptersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isVideoPodcast = episode.enclosureType?.startsWith("video/") == true
    val isVideo = isVideoPodcast && !ui.isAudioOnly && !state.playbackRoute.isRemote

    val isDownloaded by remember(episode.id) { downloadRepository.isDownloaded(episode.id) }
        .collectAsStateWithLifecycle(initialValue = false)
    val isDownloading by remember(episode.id) { downloadRepository.isDownloading(episode.id) }
        .collectAsStateWithLifecycle(initialValue = false)

    BackHandler(enabled = ui.showFullscreenTranscript) {
        ui.showFullscreenTranscript = false
        fullscreenRequest?.invoke(PlayerFullscreenOverlay.Transcript, false)
    }
    BackHandler(enabled = ui.showInlineTranscript && !ui.showFullscreenTranscript) {
        ui.showInlineTranscript = false
    }

    LaunchedEffect(state.currentTranscript.isEmpty()) {
        if (state.currentTranscript.isEmpty()) ui.showInlineTranscript = false
    }

    val resolvedDark = LocalEffectiveDarkTheme.current
    SideEffect {
        window?.takeUnless { display.isContextPane }?.let { win ->
            val insetsController =
                androidx.core.view.WindowCompat
                    .getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !resolvedDark
            insetsController.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    val style by dependencies.userPrefs.fullPlayerStyleStream.collectAsStateWithLifecycle(
        initialValue = dependencies.userPrefs.cachedFullPlayerStyle,
    )
    // One Cast picker above the phone and tablet layouts, so its sheet and Android 17's permission
    // answer survive a fold, unfold or resize that swaps the layout.
    val castPicker = rememberCastPickerState()
    CompositionLocalProvider(LocalCastPicker provides castPicker) {
        if (tablet) {
            TabletFullPlayerBody(
                model = FullPlayerBodyModel(state, episode, podcast, nextEpisode, isVideo, isVideoPodcast, FullPlayerDownloadState(isDownloaded, isDownloading)),
                dependencies = dependencies,
                display = display,
                actions = playerActions,
                flows = FullPlayerFlows(positionFlow, bufferedPositionFlow),
                ui = ui,
                resources = TabletPlayerResources(
                    FullPlayerBodyResources(scope, queueSnackbarHostState, statusBarPadding, navBarPadding, modifier),
                    PlayerQueueSheetResources(scope, queueSnackbarHostState, queueSheetState, playerQueueText()),
                    minimalPlayerActions(dependencies, state, ui, playerActions),
                    useMinimalPlayer(style, isVideoPodcast),
                ),
            )
        } else if (useMinimalPlayer(style, isVideoPodcast)) {
            MinimalPlayerContent(
                state = state,
                display = display,
                flows = PlayerProgressFlows(positionFlow, bufferedPositionFlow),
                actions = minimalPlayerActions(dependencies, state, ui, playerActions),
                modifier = modifier,
            )
            PlayerSnackbarHost(queueSnackbarHostState, display.visibleHeight, navBarPadding)
        } else {
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
                actions = playerActions,
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
        }
    }

    // ------------------------------------------------------------------
    // Sheets
    // ------------------------------------------------------------------

    MaterialTheme(colorScheme = display.colorScheme) {
        CastPickerSheetHost(castPicker, isCasting = state.playbackRoute.isRemote)
    }

    FullPlayerModalSheets(
        dependencies = dependencies,
        model =
        FullPlayerModalModel(
            state,
            episode,
            podcast,
            colorScheme,
            positionFlow,
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
        actions = playerActions,
    )

    // ------------------------------------------------------------------
    // Fullscreen transcript overlay
    // ------------------------------------------------------------------

    FullPlayerFullscreenOverlays(
        dependencies = dependencies,
        model = FullPlayerOverlayModel(state, episode, colorScheme, positionFlow),
        display = display,
        actions = playerActions,
        ui = ui,
    )
}

private fun minimalPlayerActions(
    dependencies: FullPlayerDependencies,
    state: PlayerState,
    ui: FullPlayerUiState,
    actions: FullPlayerActions,
) = MinimalPlayerActions(
    onPlayPause = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("play_pause")
        togglePlayback(state, dependencies.playbackRepository)
    },
    onReplay = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("previous")
        dependencies.playbackRepository.skipBackward()
    },
    onForward = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("next")
        dependencies.playbackRepository.skipForward()
    },
    onSleep = { ui.showSleepSheet = true },
    onSpeed = { ui.showSpeedSheet = true },
    onQueue = { ui.showQueueSheet = true },
    onSeek = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("seek")
        dependencies.playbackRepository.seekTo(it)
    },
    onCollapse = actions.onCollapse,
    onPodcastInfo = {
        state.currentPodcast?.let {
            actions.onCollapse()
            actions.onPodcastInfoClick(it)
        }
    },
)

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
        val windowSize = boxloreConfigurationWindowSize()
        val screenWidth = windowSize.width
        val screenHeight = windowSize.height
        val finalMaxHeight = screenHeight - resources.statusBarPadding - resources.navBarPadding

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(top = resources.statusBarPadding, bottom = resources.navBarPadding)
                // Sideways phones: Share, Cast and the controls stay clear of side system buttons and cutouts.
                .boxloreHorizontalSafePadding(),
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
                if (usesSidewaysPhoneColumns(screenWidth.value, screenHeight.value, LocalBoxloreWindowLayout.current.usesTabletLayout)) {
                    FullPlayerSidewaysContent(model, dependencies, display, actions, flows, ui, FullPlayerScrollResources(resources.scope, layoutValues))
                    return@Box
                }
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
        PlayerSnackbarHost(resources.snackbarHostState, display.visibleHeight, resources.navBarPadding)
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
        Spacer(modifier = Modifier.height(resources.layout.topSpacing))
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
        val (metadataTopSpacing, metadataBottomSpacing) =
            PlayerMetadataStylingLogic.resolveMetadataSpacing(
                isTranscriptMode = ui.showInlineTranscript,
                isCompact = resources.layout.isCompact,
            )
        Spacer(modifier = Modifier.height(metadataTopSpacing))
        PlayerMetadata(
            episode = model.episode,
            podcast = model.podcast,
            colorScheme = display.colorScheme,
            actions = actions,
            isTranscriptMode = ui.showInlineTranscript,
        )
        Spacer(modifier = Modifier.height(metadataBottomSpacing))
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

/**
 * Sideways phones: cover, title and show on the left; seekbar, play controls, quick actions, Up next
 * and notes on the right. The same pieces as the upright player, so the tray, notes and Cast toggle
 * carry over when the phone turns.
 */
@Composable
private fun FullPlayerSidewaysContent(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: FullPlayerScrollResources,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(display.isExpanded) {
        if (!display.isExpanded && scrollState.value > 0) scrollState.scrollTo(0)
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        val columnWidth = ((maxWidth - SIDEWAYS_GAP) / 2).coerceAtMost(SIDEWAYS_COLUMN_MAX)
        val heroSize = sidewaysHeroSize(columnWidth, maxHeight)
        val heroDimensions = if (model.isVideo) HeroDimensions(columnWidth - 16.dp, (columnWidth - 16.dp) * (9f / 16f)) else HeroDimensions(heroSize, heroSize)
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SIDEWAYS_GAP, Alignment.CenterHorizontally),
        ) {
            Column(
                Modifier.width(columnWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                FullPlayerHeroSection(
                    model = FullPlayerHeroModel(model.state, model.episode, model.podcast, model.nextEpisode, model.isVideo, heroDimensions),
                    positionFlow = flows.position,
                    playbackRepository = dependencies.playbackRepository,
                    display = display,
                    actions = actions,
                    ui = ui,
                    isCompact = true,
                )
                Spacer(Modifier.height(12.dp))
                PlayerMetadata(model.episode, model.podcast, display.colorScheme, actions, isTranscriptMode = ui.showInlineTranscript)
            }
            Column(
                Modifier.width(columnWidth).fillMaxHeight().verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                FullPlayerControls(
                    model = FullPlayerControlModel(model.state, model.episode, model.podcast, display.colorScheme, model.download.isDownloaded, model.download.isDownloading, isCompact = true),
                    dependencies = dependencies,
                    flows = flows,
                    ui = ui,
                    scope = resources.scope,
                    primaryControlHeight = 80.dp,
                )
                Spacer(Modifier.height(14.dp))
                FullPlayerSupportingContent(model.state, model.episode, model.nextEpisode, display.colorScheme, dependencies.playbackRepository, actions, ui)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val SIDEWAYS_GAP = 24.dp
private val SIDEWAYS_COLUMN_MAX = 480.dp

internal data class FullPlayerHeroModel(
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
internal fun FullPlayerHeroSection(
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
    val castPicker = LocalCastPicker.current
    LaunchedEffect(isRemote) {
        if (ui.followCastRoute(isRemote) && isRemote) actions.onFullscreenVideoChange(false)
    }
    val showCastControls = ui.showCastControls || (isRemote && !ui.castRouteRemote)
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
                onChangeCastDevice = { castPicker?.onCastClick?.invoke() },
            )
        }
        CastHeroModeToggle(
            visible = isRemote && heroMode != CastHeroDisplayMode.TRANSCRIPT,
            showCastControls = showCastControls,
            colorScheme = display.colorScheme,
            onToggle = { ui.showCastControls = !showCastControls },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
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
                    dimensions = castHeroDimensions(model.dimensions, boxloreConfigurationWindowSize().width, model.state.playbackRoute.canControlVolume),
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
                contentDescription = stringResource(if (showCastControls) R.string.player_show_artwork else R.string.player_show_cast_controls),
            )
        }
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
            fallbackArtworkUrls = listOf(model.episode.podcastImageUrl, model.podcast.imageUrl, model.podcast.fallbackImageUrl),
            nextFallbackArtworkUrls = listOf(model.nextEpisode?.podcastImageUrl),
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
