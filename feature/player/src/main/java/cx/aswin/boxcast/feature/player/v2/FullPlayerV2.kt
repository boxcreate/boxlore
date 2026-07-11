package cx.aswin.boxcast.feature.player.v2

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Replay10
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState
import cx.aswin.boxcast.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.player.ChaptersSheetContent
import cx.aswin.boxcast.feature.player.FullscreenTranscriptScreen
import cx.aswin.boxcast.feature.player.QueueSheetActions
import cx.aswin.boxcast.feature.player.QueueSheetContent
import cx.aswin.boxcast.feature.player.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * v2 full player: immersive artwork-tinted canvas, wavy seekbar, expressive control
 * deck, and below-the-fold Up Next + Show Notes cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerV2(
    playbackRepository: PlaybackRepository,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    colorScheme: ColorScheme,
    isFullscreenVideo: Boolean,
    onFullscreenVideoChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onEpisodeInfoClick: (Episode) -> Unit,
    onPodcastInfoClick: (Podcast) -> Unit,
    sheetNestedScrollConnection: NestedScrollConnection,
    isExpanded: Boolean,
    showSwipeMinimizeTip: Boolean = false,
    onSwipeMinimizeTipDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Position ticks are split into separate flows so only the seekbar recomposes per tick.
    val state by remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.copy(position = 0, bufferedPosition = 0) }
            .distinctUntilChanged()
    }.collectAsState(initial = playbackRepository.playerState.value)

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

    // Sheet & overlay state
    var showQueueSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showFullscreenTranscript by remember(episode.id) { mutableStateOf(false) }
    var showInlineTranscript by rememberSaveable(inputs = arrayOf(episode.id)) { mutableStateOf(false) }
    var isSyncEnabled by remember(episode.id) { mutableStateOf(true) }
    var isAudioOnly by rememberSaveable(inputs = arrayOf(episode.id)) { mutableStateOf(false) }

    val queueSnackbarHostState = remember { SnackbarHostState() }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chaptersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isVideoPodcast = episode.enclosureType?.startsWith("video/") == true
    val isVideo = isVideoPodcast && !isAudioOnly

    val isDownloaded by remember(episode.id) { downloadRepository.isDownloaded(episode.id) }
        .collectAsState(initial = false)
    val isDownloading by remember(episode.id) { downloadRepository.isDownloading(episode.id) }
        .collectAsState(initial = false)

    BackHandler(enabled = showFullscreenTranscript) { showFullscreenTranscript = false }
    BackHandler(enabled = showInlineTranscript && !showFullscreenTranscript) {
        showInlineTranscript = false
    }

    LaunchedEffect(state.currentTranscript.isEmpty()) {
        if (state.currentTranscript.isEmpty()) showInlineTranscript = false
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .playerCanvas(colorScheme)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding, bottom = navBarPadding)
        ) {
            PlayerTopBar(
                colorScheme = colorScheme,
                showSwipeMinimizeTip = showSwipeMinimizeTip,
                isExpanded = isExpanded,
                onSwipeMinimizeTipDismissed = onSwipeMinimizeTipDismissed,
                onCollapse = {
                    cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("collapsed")
                    onCollapse()
                },
                onShare = { showShareSheet = true }
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(sheetNestedScrollConnection)
            ) {
                val isCompact = maxHeight < 620.dp
                val availableForHero = maxHeight * (if (isCompact) 0.28f else 0.34f)
                val heroSize = min(maxWidth * 0.68f, availableForHero).coerceAtLeast(138.dp)
                val heroWidth: androidx.compose.ui.unit.Dp
                val heroHeight: androidx.compose.ui.unit.Dp
                if (isVideo) {
                    val targetWidth = maxWidth * 0.95f - 48.dp
                    val targetHeight = targetWidth * (9f / 16f)
                    if (targetHeight > availableForHero) {
                        heroHeight = availableForHero
                        heroWidth = availableForHero * (16f / 9f)
                    } else {
                        heroWidth = targetWidth
                        heroHeight = targetHeight
                    }
                } else {
                    heroWidth = heroSize
                    heroHeight = heroSize
                }
                var episodeTitleOverflows by remember(episode.id) { mutableStateOf(false) }
                var podcastTitleOverflows by remember(episode.id, podcast.id) { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 14.dp))

                    AnimatedContent(
                        targetState = showInlineTranscript,
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
                                transcript = state.currentTranscript,
                                positionFlow = positionFlow,
                                transcriptUrl = episode.transcriptUrl,
                                artworkUrl = episode.imageUrl?.takeIf { it.isNotBlank() }
                                    ?: podcast.imageUrl,
                                colorScheme = colorScheme,
                                isSyncEnabled = isSyncEnabled,
                                onSyncEnabledChange = { isSyncEnabled = it },
                                onSeek = { seekPos ->
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource(
                                        "transcript_tap"
                                    )
                                    playbackRepository.seekTo(seekPos)
                                },
                                onShowArtwork = { showInlineTranscript = false },
                                onFullscreen = { showFullscreenTranscript = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(maxOf(heroHeight, if (isCompact) 220.dp else 250.dp))
                            )
                        } else {
                            PlayerHero(
                                episodeId = episode.id,
                                artworkUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast.imageUrl,
                                nextArtworkUrl = nextEpisode?.imageUrl?.takeIf { it.isNotBlank() }
                                    ?: nextEpisode?.podcastImageUrl?.takeIf { it.isNotBlank() },
                                nextEpisodeTitle = nextEpisode?.title,
                                chapterArtFlow = remember(state.currentChapters, positionFlow) {
                                    chapterArtFlow(positionFlow, state.currentChapters)
                                },
                                isPlaying = state.isPlaying,
                                isVideo = isVideo,
                                isFullscreenVideo = isFullscreenVideo,
                                controller = playbackRepository.controller,
                                width = heroWidth,
                                height = heroHeight,
                                isExpanded = isExpanded,
                                colorScheme = colorScheme,
                                onSkipNextEpisode = {
                                    cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
                                        "skip_next_episode"
                                    )
                                    playbackRepository.skipToNextEpisode()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (isVideoPodcast && !showInlineTranscript) {
                        VideoModeButtons(
                            isAudioOnly = isAudioOnly,
                            colorScheme = colorScheme,
                            onAudioOnlyChange = { isAudioOnly = it },
                            onFullscreenClick = { onFullscreenVideoChange(true) }
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))

                    // Single-line marquees preserve vertical space while keeping
                    // complete long episode and podcast names discoverable.
                    Text(
                        text = episode.title.replace("+", " "),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = if (episodeTitleOverflows) TextAlign.Start else TextAlign.Center,
                        onTextLayout = {
                            if (it.hasVisualOverflow) episodeTitleOverflows = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("episode_info")
                                onCollapse()
                                onEpisodeInfoClick(episode)
                            }
                            .then(
                                if (episodeTitleOverflows) {
                                    Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                        repeatDelayMillis = 1_600,
                                        initialDelayMillis = 1_800,
                                        spacing = MarqueeSpacing.fractionOfContainer(0.18f),
                                        velocity = 26.dp
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = podcast.title.replace("+", " "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = if (podcastTitleOverflows) TextAlign.Start else TextAlign.Center,
                        onTextLayout = {
                            if (it.hasVisualOverflow) podcastTitleOverflows = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("podcast_info")
                                onCollapse()
                                onPodcastInfoClick(podcast)
                            }
                            .then(
                                if (podcastTitleOverflows) {
                                    Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                        repeatDelayMillis = 1_600,
                                        initialDelayMillis = 1_800,
                                        spacing = MarqueeSpacing.fractionOfContainer(0.18f),
                                        velocity = 24.dp
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    )

                    Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 14.dp))

                    if (state.duration > 0) {
                        PlayerSeekbar(
                            positionFlow = positionFlow,
                            bufferedPositionFlow = bufferedPositionFlow,
                            durationMs = state.duration,
                            isPlaying = state.isPlaying,
                            colorScheme = colorScheme,
                            onSeek = {
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("seek")
                                playbackRepository.seekTo(it)
                            },
                            chapters = state.currentChapters
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 18.dp))

                    PrimaryControls(
                        isPlaying = state.isPlaying,
                        isLoading = state.isLoading,
                        colorScheme = colorScheme,
                        onPlayPause = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("play_pause")
                            if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
                        },
                        onReplay = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("previous")
                            playbackRepository.skipBackward()
                        },
                        onForward = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("next")
                            playbackRepository.skipForward()
                        }
                    )

                    Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 18.dp))

                    SecondaryRail(
                        playbackSpeed = state.playbackSpeed,
                        sleepTimerEnd = state.sleepTimerEnd,
                        sleepAtEndOfEpisode = state.sleepAtEndOfEpisode,
                        hasChapters = !episode.chaptersUrl.isNullOrEmpty() || state.currentChapters.isNotEmpty(),
                        hasTranscript = state.currentTranscript.isNotEmpty(),
                        isTranscriptVisible = showInlineTranscript,
                        isChaptersLoading = state.isChaptersLoading,
                        autoTranscriptState = state.autoTranscriptState,
                        autoChaptersState = state.autoChaptersState,
                        isLiked = state.isLiked,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        isPlayed = state.isCompleted,
                        colorScheme = colorScheme,
                        onSpeedSelected = { speed ->
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
                                "speed_change",
                                value = speed.toString()
                            )
                            playbackRepository.setPlaybackSpeed(speed)
                        },
                        onSleepTimerSelected = { minutes ->
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
                                "sleep_timer",
                                value = minutes.toString()
                            )
                            playbackRepository.setSleepTimer(minutes)
                        },
                        onQueueClick = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("queue")
                            showQueueSheet = true
                        },
                        onChaptersClick = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("chapters_sheet")
                            showChaptersSheet = true
                        },
                        onTranscriptClick = {
                            if (state.currentTranscript.isNotEmpty()) {
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
                                    if (showInlineTranscript) "transcript_hide" else "transcript_inline"
                                )
                                showInlineTranscript = !showInlineTranscript
                            }
                        },
                        onLikeClick = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("like")
                            scope.launch { playbackRepository.toggleLike() }
                        },
                        onDownloadClick = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("download")
                            scope.launch {
                                if (isDownloaded || isDownloading) {
                                    downloadRepository.removeDownload(episode.id)
                                } else {
                                    downloadRepository.addDownload(episode, podcast)
                                }
                            }
                        },
                        onMarkPlayedClick = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("mark_played")
                            scope.launch {
                                playbackRepository.toggleCompletion(
                                    episode = episode,
                                    podcastId = podcast.id,
                                    podcastTitle = podcast.title,
                                    podcastImageUrl = podcast.imageUrl
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 20.dp))

                    // Below the fold: Up Next + Show Notes
                    if (nextEpisode != null) {
                        UpNextCard(
                            queuedEpisodes = state.queue.drop(1),
                            colorScheme = colorScheme,
                            onOpenQueue = {
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("queue")
                                showQueueSheet = true
                            },
                            onPlayNext = {
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("skip_next_episode")
                                playbackRepository.skipToNextEpisode()
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    NotesPreviewCard(
                        description = episode.description,
                        colorScheme = colorScheme,
                        onOpenEpisodeInfo = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("episode_info")
                            onCollapse()
                            onEpisodeInfoClick(episode)
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        SnackbarHost(
            hostState = queueSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // ------------------------------------------------------------------
    // Sheets
    // ------------------------------------------------------------------

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = queueSheetState,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            dragHandle = { SheetDragHandle(colorScheme) }
        ) {
            QueueSheetContent(
                queue = state.queue.drop(1), // Skip currently playing
                currentPodcast = podcast,
                colorScheme = colorScheme,
                actions = QueueSheetActions(
                    onPlayEpisode = { ep ->
                        scope.launch {
                            val freshQueue = playbackRepository.playerState.value.queue
                            val epPodcastId = ep.podcastId
                            val episodePodcast = if (epPodcastId != null && epPodcastId != podcast.id) {
                                Podcast(
                                    id = epPodcastId,
                                    title = ep.podcastTitle ?: "Unknown",
                                    artist = ep.podcastArtist ?: "",
                                    imageUrl = ep.podcastImageUrl ?: "",
                                    description = null,
                                    genre = ep.podcastGenre ?: ""
                                )
                            } else {
                                podcast
                            }
                            playbackRepository.playFromQueueIndex(ep.id, freshQueue, episodePodcast)
                            showQueueSheet = false
                        }
                    },
                    onRemoveEpisode = { ep ->
                        scope.launch {
                            // Defer the AUTO_FILL rejection signal until the undo window
                            // lapses, so an undone remove doesn't count as a rejection.
                            val removed = playbackRepository.removeFromQueue(ep.id, deferSkipSignal = true)
                            if (removed != null) {
                                val result = queueSnackbarHostState.showSnackbar(
                                    message = "Removed from queue",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    playbackRepository.undoQueueRemoval(removed)
                                } else {
                                    playbackRepository.confirmQueueRemoval(removed)
                                }
                            }
                        }
                    },
                    onClose = { showQueueSheet = false },
                    onMove = { fromUi, toUi ->
                        playbackRepository.moveQueueItem(
                            cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(fromUi),
                            cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(toUi)
                        )
                    },
                    onDragEnd = { episodeId, fromUi, toUi ->
                        scope.launch {
                            playbackRepository.persistQueueOrder(
                                movedEpisodeId = episodeId,
                                fromQueueIndex = cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(fromUi),
                                toQueueIndex = cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(toUi)
                            )
                        }
                    }
                )
            )
        }
    }

    if (showChaptersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChaptersSheet = false },
            sheetState = chaptersSheetState,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            dragHandle = { SheetDragHandle(colorScheme) }
        ) {
            ChaptersSheetContent(
                chapters = state.currentChapters,
                positionFlow = positionFlow,
                colorScheme = colorScheme,
                onSeek = { seekPos ->
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("chapters_list")
                    playbackRepository.seekTo(seekPos)
                    showChaptersSheet = false
                },
                onClose = { showChaptersSheet = false },
                chaptersUrl = episode.chaptersUrl,
                isChaptersLoading = state.isChaptersLoading,
                hasTranscript = state.autoTranscriptState == AutoTranscriptState.NONE ||
                    state.autoTranscriptState == AutoTranscriptState.COMPLETED,
                onGenerateChapters = { playbackRepository.generateAutoChapters() }
            )
        }
    }

    if (showSpeedSheet) {
        SpeedSheet(
            currentSpeed = state.playbackSpeed,
            colorScheme = colorScheme,
            onSpeedSelected = { speed ->
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("speed_change", value = speed.toString())
                playbackRepository.setPlaybackSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showSleepSheet) {
        SleepSheet(
            sleepTimerEnd = state.sleepTimerEnd,
            colorScheme = colorScheme,
            onDurationSelected = { minutes ->
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("sleep_timer", value = minutes.toString())
                playbackRepository.setSleepTimer(minutes)
                showSleepSheet = false
            },
            onDismiss = { showSleepSheet = false }
        )
    }

    if (showGenerateDialog && canGenerateTranscript) {
        GenerateTranscriptDialog(
            episodeDurationSec = if (state.duration > 0) state.duration / 1000 else episode.duration.toLong(),
            autoTranscriptLimitLeft = state.autoTranscriptLimitLeft,
            colorScheme = colorScheme,
            onConfirm = {
                showGenerateDialog = false
                playbackRepository.generateAutoTranscript()
            },
            onDismiss = { showGenerateDialog = false }
        )
    }

    if (showShareSheet) {
        cx.aswin.boxcast.core.designsystem.components.ShareBottomSheet(
            id = episode.id,
            type = "episode",
            title = episode.title,
            subtitle = podcast.title,
            onDismissRequest = { showShareSheet = false },
            durationMs = episode.duration * 1000L,
            currentPositionMs = playbackRepository.playerState.value.position,
            showTimestampOption = true,
            onShare = { _, _, t ->
                cx.aswin.boxcast.core.data.ShareManager.shareEpisode(context, episode, podcast.title, t)
            }
        )
    }

    // ------------------------------------------------------------------
    // Fullscreen transcript overlay
    // ------------------------------------------------------------------

    AnimatedVisibility(
        visible = showFullscreenTranscript,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        FullscreenTranscriptScreen(
            transcript = state.currentTranscript,
            positionFlow = positionFlow,
            isPlaying = state.isPlaying,
            isLoading = state.isLoading,
            durationMs = state.duration,
            colorScheme = colorScheme,
            isSyncEnabled = isSyncEnabled,
            onSyncEnabledChange = { isSyncEnabled = it },
            onSeek = { seekPos ->
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("transcript_tap")
                playbackRepository.seekTo(seekPos)
            },
            onPlayPause = {
                if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
            },
            onClose = { showFullscreenTranscript = false },
            transcriptUrl = episode.transcriptUrl
        )
    }

    // ------------------------------------------------------------------
    // Fullscreen video overlay
    // ------------------------------------------------------------------

    VideoFullscreenOverlay(
        visible = isFullscreenVideo,
        episodeTitle = episode.title,
        isPlaying = state.isPlaying,
        durationMs = state.duration,
        positionFlow = positionFlow,
        controller = playbackRepository.controller,
        colorScheme = colorScheme,
        onExit = { onFullscreenVideoChange(false) },
        onPlayPause = {
            if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
        },
        onReplay = { playbackRepository.skipBackward() },
        onForward = { playbackRepository.skipForward() },
        onSeek = { playbackRepository.seekTo(it) }
    )
}

@Composable
private fun SheetDragHandle(colorScheme: ColorScheme) {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}

@Composable
private fun PlayerTopBar(
    colorScheme: ColorScheme,
    showSwipeMinimizeTip: Boolean,
    isExpanded: Boolean,
    onSwipeMinimizeTipDismissed: () -> Unit,
    onCollapse: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        var tipVisible by remember { mutableStateOf(showSwipeMinimizeTip) }
        LaunchedEffect(showSwipeMinimizeTip, isExpanded) {
            if (showSwipeMinimizeTip && isExpanded) {
                delay(3500)
                tipVisible = false
                onSwipeMinimizeTipDismissed()
            }
        }
        AnimatedContent(
            targetState = tipVisible && isExpanded,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "topBarLabel"
        ) { isShowingTip ->
            Text(
                text = if (isShowingTip) "↓ Swipe down to minimize" else "Now Playing",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isShowingTip) colorScheme.primary.copy(alpha = 0.8f) else colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share",
                tint = colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Immersive fullscreen video overlay with auto-hiding HUD and rotation toggle. */
@Composable
private fun VideoFullscreenOverlay(
    visible: Boolean,
    episodeTitle: String,
    isPlaying: Boolean,
    durationMs: Long,
    positionFlow: kotlinx.coroutines.flow.Flow<Long>,
    controller: androidx.media3.common.Player?,
    colorScheme: ColorScheme,
    onExit: () -> Unit,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var isLandscape by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val activity = LocalContext.current as? android.app.Activity

    if (visible) {
        DisposableEffect(isLandscape) {
            activity?.requestedOrientation = if (isLandscape) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            val window = activity?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                if (window != null) {
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        BackHandler { onExit() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)),
        modifier = Modifier.fillMaxSize()
    ) {
        LaunchedEffect(controlsVisible, isPlaying) {
            if (controlsVisible && isPlaying) {
                delay(3000)
                controlsVisible = false
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            ) {
                if (controller != null) {
                    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
                    DisposableEffect(controller) {
                        onDispose { playerViewRef?.player = null }
                    }
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                player = controller
                                useController = false // Custom overlay instead
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                playerViewRef = this
                            }
                        },
                        update = { playerView ->
                            if (playerView.player != controller) playerView.player = controller
                            playerViewRef = playerView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        // Top bar: exit, title, rotation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = onExit,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = episodeTitle.replace("+", " "),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { isLandscape = !isLandscape },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.ScreenRotation, contentDescription = "Toggle Orientation", tint = Color.White)
                            }
                        }

                        // Central playback controls
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onReplay,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Replay10, contentDescription = "Replay 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.primaryContainer)
                                    .clickable(onClick = onPlayPause),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(36.dp),
                                    tint = colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = onForward,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Forward30, contentDescription = "Forward 30s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        // Bottom: progress + time labels
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            val positionMs by positionFlow.collectAsState(initial = 0L)
                            if (durationMs > 0) {
                                Slider(
                                    value = positionMs.toFloat(),
                                    onValueChange = { onSeek(it.toLong()) },
                                    valueRange = 0f..durationMs.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorScheme.primary,
                                        activeTrackColor = colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(positionMs),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "-" + formatTime((durationMs - positionMs).coerceAtLeast(0)),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
