package cx.aswin.boxlore.feature.player.v2

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.player.ChaptersSheetContent
import cx.aswin.boxlore.feature.player.FullscreenTranscriptScreen
import cx.aswin.boxlore.feature.player.QueueSheetActions
import cx.aswin.boxlore.feature.player.QueueSheetContent
import cx.aswin.boxlore.feature.player.formatTime
import cx.aswin.boxlore.feature.player.v2.logic.ResponsiveHeroLayout
import cx.aswin.boxlore.feature.player.v2.logic.calculateResponsiveHeroLayout
import cx.aswin.boxlore.feature.player.v2.logic.resolveQueuePodcast
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * v2 full player: immersive artwork-tinted canvas, wavy seekbar, expressive control
 * deck, and below-the-fold Up Next + Show Notes cards.
 */
data class FullPlayerDependencies(
    val playbackRepository: PlaybackRepository,
    val downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository
)

data class FullPlayerDisplay(
    val colorScheme: ColorScheme,
    val isFullscreenVideo: Boolean,
    val sheetNestedScrollConnection: NestedScrollConnection,
    val isExpanded: Boolean,
    val showSwipeMinimizeTip: Boolean = false
)

data class FullPlayerActions(
    val onFullscreenVideoChange: (Boolean) -> Unit,
    val onCollapse: () -> Unit,
    val onEpisodeInfoClick: (Episode) -> Unit,
    val onPodcastInfoClick: (Podcast) -> Unit,
    val onSwipeMinimizeTipDismissed: () -> Unit = {}
)

@Stable
private class FullPlayerUiState {
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

private val FullPlayerUiStateSaver = listSaver<FullPlayerUiState, Boolean>(
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
            state.isAudioOnly
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
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerV2(
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    modifier: Modifier = Modifier
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

    val positionFlow = remember(playbackRepository) {
        playbackRepository.playerState.map { it.position }.distinctUntilChanged()
    }
    val bufferedPositionFlow = remember(playbackRepository) {
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
    val isVideo = isVideoPodcast && !ui.isAudioOnly

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
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !resolvedDark
            insetsController.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    val canGenerateTranscript = state.currentTranscript.isEmpty() &&
        state.autoTranscriptState != AutoTranscriptState.GENERATING &&
        state.autoTranscriptState != AutoTranscriptState.COMPLETED

    FullPlayerBody(
        model = FullPlayerBodyModel(
            state = state,
            episode = episode,
            podcast = podcast,
            nextEpisode = nextEpisode,
            isVideo = isVideo,
            isVideoPodcast = isVideoPodcast,
            download = FullPlayerDownloadState(isDownloaded, isDownloading)
        ),
        dependencies = dependencies,
        display = display,
        actions = actions,
        flows = FullPlayerFlows(positionFlow, bufferedPositionFlow),
        ui = ui,
        resources = FullPlayerBodyResources(
            scope,
            queueSnackbarHostState,
            statusBarPadding,
            navBarPadding,
            modifier
        )
    )

    // ------------------------------------------------------------------
    // Sheets
    // ------------------------------------------------------------------

    FullPlayerModalSheets(
        dependencies = dependencies,
        model = FullPlayerModalModel(
            state,
            episode,
            podcast,
            colorScheme,
            positionFlow,
            canGenerateTranscript
        ),
        ui = ui,
        resources = FullPlayerModalResources(
            scope,
            queueSnackbarHostState,
            queueSheetState,
            chaptersSheetState,
            context
        )
    )

    // ------------------------------------------------------------------
    // Fullscreen transcript overlay
    // ------------------------------------------------------------------

    FullPlayerFullscreenOverlays(
        dependencies = dependencies,
        model = FullPlayerOverlayModel(state, episode, colorScheme, positionFlow),
        display = display,
        actions = actions,
        ui = ui
    )
}

internal data class FullPlayerDownloadState(
    val isDownloaded: Boolean,
    val isDownloading: Boolean
)

internal data class FullPlayerModalModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val colorScheme: ColorScheme,
    val positionFlow: Flow<Long>,
    val canGenerateTranscript: Boolean
)

internal data class FullPlayerOverlayModel(
    val state: PlayerState,
    val episode: Episode,
    val colorScheme: ColorScheme,
    val positionFlow: Flow<Long>
)

@Composable
private fun FullPlayerBody(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: FullPlayerBodyResources
) {
    Box(
        modifier = resources.modifier
            .fillMaxSize()
            .playerCanvas(display.colorScheme)
    ) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val finalMaxHeight = screenHeight - resources.statusBarPadding - resources.navBarPadding

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = resources.statusBarPadding, bottom = resources.navBarPadding)
        ) {
            PlayerTopBar(
                colorScheme = display.colorScheme,
                showSwipeMinimizeTip = display.showSwipeMinimizeTip,
                isExpanded = display.isExpanded,
                onSwipeMinimizeTipDismissed = actions.onSwipeMinimizeTipDismissed,
                onCollapse = {
                    cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("collapsed")
                    actions.onCollapse()
                },
                onShare = { ui.showShareSheet = true }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(display.sheetNestedScrollConnection)
            ) {
                val layoutValues = calculateResponsiveHeroLayout(screenWidth, finalMaxHeight, model.isVideo)
                FullPlayerScrollableContent(
                    model = model,
                    dependencies = dependencies,
                    display = display,
                    actions = actions,
                    flows = flows,
                    ui = ui,
                    resources = FullPlayerScrollResources(
                        resources.scope,
                        layoutValues
                    )
                )
            }
        }
        SnackbarHost(
            hostState = resources.snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
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
    resources: FullPlayerScrollResources
) {
    val scrollState = rememberScrollState()
    // Reset scroll when collapsing so reopen always starts at the top.
    LaunchedEffect(display.isExpanded) {
        if (!display.isExpanded && scrollState.value > 0) {
            scrollState.scrollTo(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 6.dp else 14.dp))
        FullPlayerHeroSection(
            model = FullPlayerHeroModel(
                state = model.state,
                episode = model.episode,
                podcast = model.podcast,
                nextEpisode = model.nextEpisode,
                isVideo = model.isVideo,
                isVideoPodcast = model.isVideoPodcast,
                dimensions = resources.layout.dimensions
            ),
            positionFlow = flows.position,
            playbackRepository = dependencies.playbackRepository,
            display = display,
            actions = actions,
            ui = ui,
            isCompact = resources.layout.isCompact
        )
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 10.dp else 16.dp))
        PlayerMetadata(model.episode, model.podcast, display.colorScheme, actions)
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 8.dp else 14.dp))
        FullPlayerControls(
            model = FullPlayerControlModel(
                state = model.state,
                episode = model.episode,
                podcast = model.podcast,
                colorScheme = display.colorScheme,
                isDownloaded = model.download.isDownloaded,
                isDownloading = model.download.isDownloading,
                isCompact = resources.layout.isCompact
            ),
            dependencies = dependencies,
            flows = flows,
            ui = ui,
            scope = resources.scope
        )
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 14.dp else 20.dp))
        FullPlayerSupportingContent(
            state = model.state,
            episode = model.episode,
            nextEpisode = model.nextEpisode,
            colorScheme = display.colorScheme,
            playbackRepository = dependencies.playbackRepository,
            actions = actions,
            ui = ui
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
    val isVideoPodcast: Boolean,
    val dimensions: HeroDimensions
)

internal data class FullPlayerFlows(
    val position: Flow<Long>,
    val bufferedPosition: Flow<Long>
)

internal data class FullPlayerControlModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val colorScheme: ColorScheme,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val isCompact: Boolean
)

@Composable
private fun FullPlayerHeroSection(
    model: FullPlayerHeroModel,
    positionFlow: Flow<Long>,
    playbackRepository: PlaybackRepository,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    ui: FullPlayerUiState,
    isCompact: Boolean
) {
    AnimatedContent(
        targetState = ui.showInlineTranscript,
        transitionSpec = {
            (fadeIn(tween(240)) + scaleIn(tween(280), initialScale = 0.96f)) togetherWith
                (fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.96f))
        },
        contentAlignment = Alignment.Center,
        label = "artworkTranscriptHero",
        modifier = Modifier.fillMaxWidth()
    ) { transcriptVisible ->
        if (transcriptVisible) {
            InlineTranscriptHero(
                content = InlineTranscriptContent(
                    transcript = model.state.currentTranscript,
                    positionFlow = positionFlow,
                    transcriptUrl = model.episode.transcriptUrl,
                    artworkUrl = model.episode.imageUrl?.takeIf { it.isNotBlank() }
                        ?: model.podcast.imageUrl
                ),
                colorScheme = display.colorScheme,
                isSyncEnabled = ui.isSyncEnabled,
                actions = InlineTranscriptActions(
                    onSyncEnabledChange = { ui.isSyncEnabled = it },
                    onSeek = { seekPosition ->
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.setSeekSource("transcript_tap")
                        playbackRepository.seekTo(seekPosition)
                    },
                    onShowArtwork = { ui.showInlineTranscript = false },
                    onFullscreen = { ui.showFullscreenTranscript = true }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(model.dimensions.height, if (isCompact) 220.dp else 250.dp))
            )
        } else {
            ArtworkPlayerHero(model, positionFlow, playbackRepository, display)
        }
    }
    if (model.isVideoPodcast && !ui.showInlineTranscript) {
        VideoModeButtons(
            isAudioOnly = ui.isAudioOnly,
            colorScheme = display.colorScheme,
            onAudioOnlyChange = { ui.isAudioOnly = it },
            onFullscreenClick = { actions.onFullscreenVideoChange(true) }
        )
    }
}

@Composable
private fun ArtworkPlayerHero(
    model: FullPlayerHeroModel,
    positionFlow: Flow<Long>,
    playbackRepository: PlaybackRepository,
    display: FullPlayerDisplay
) {
    PlayerHero(
        artwork = PlayerHeroArtwork(
            episodeId = model.episode.id,
            artworkUrl = model.episode.imageUrl?.takeIf { it.isNotBlank() } ?: model.podcast.imageUrl,
            nextArtworkUrl = model.nextEpisode?.imageUrl?.takeIf { it.isNotBlank() }
                ?: model.nextEpisode?.podcastImageUrl?.takeIf { it.isNotBlank() },
            nextEpisodeTitle = model.nextEpisode?.title,
            chapterArtFlow = remember(model.state.currentChapters, positionFlow) {
                chapterArtFlow(positionFlow, model.state.currentChapters)
            }
