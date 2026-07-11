package cx.aswin.boxcast.feature.player.v2.full

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.data.DownloadRepository
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.QueueMath
import cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState
import cx.aswin.boxcast.core.designsystem.components.ShareBottomSheet
import cx.aswin.boxcast.feature.player.FullscreenTranscriptScreen
import cx.aswin.boxcast.feature.player.v2.sheets.ChaptersSheetV2
import cx.aswin.boxcast.feature.player.v2.sheets.PlayerOverlaySheet
import cx.aswin.boxcast.feature.player.v2.sheets.PlayerSheetsHost
import cx.aswin.boxcast.feature.player.v2.sheets.QueueSheetActions
import cx.aswin.boxcast.feature.player.v2.sheets.QueueSheetV2
import cx.aswin.boxcast.feature.player.v2.sheets.SpeedSleepSheet
import cx.aswin.boxcast.feature.player.v2.sheets.TranscriptSheetV2
import cx.aswin.boxcast.feature.player.v2.video.VideoPlayerOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerV2(
    playbackRepository: PlaybackRepository,
    downloadRepository: DownloadRepository,
    colorScheme: ColorScheme,
    isFullscreenVideo: Boolean,
    onFullscreenVideoChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onEpisodeInfoClick: (Episode) -> Unit,
    onPodcastInfoClick: (Podcast) -> Unit,
    showSwipeMinimizeTip: Boolean,
    onSwipeMinimizeTipDismissed: () -> Unit,
    showTitleTip: Boolean,
    onTitleTipDismissed: () -> Unit,
    isExpanded: Boolean,
    prewarmOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
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
    val scope = rememberCoroutineScope()
    val controlTint = colorScheme.primary

    val containerColor = colorScheme.primaryContainer.copy(alpha = 0.6f).compositeOver(colorScheme.surface)
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var activeOverlay by remember { mutableStateOf(PlayerOverlaySheet.None) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showFullscreenTranscript by remember(episode.id) { mutableStateOf(false) }
    var isSyncEnabled by remember(episode.id) { mutableStateOf(true) }
    var skipDirection by remember { mutableIntStateOf(0) }
    var showGenerateConfirmation by remember { mutableStateOf(false) }
    val queueSnackbarHostState = remember { SnackbarHostState() }

    val isDownloaded by remember(episode.id) {
        downloadRepository.isDownloaded(episode.id)
    }.collectAsState(initial = false)
    val isDownloading by remember(episode.id) {
        downloadRepository.isDownloading(episode.id)
    }.collectAsState(initial = false)

    LaunchedEffect(state.autoTranscriptState) {
        if (state.autoTranscriptState == AutoTranscriptState.NOT_GENERATED) {
            showGenerateConfirmation = true
        }
    }

    BackHandler(enabled = showFullscreenTranscript) { showFullscreenTranscript = false }

    if (isFullscreenVideo) {
        VideoPlayerOverlay(
            playbackRepository = playbackRepository,
            colorScheme = colorScheme,
            onExitFullscreen = { onFullscreenVideoChange(false) },
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor)
                .padding(top = statusBarPadding, bottom = navBarPadding),
        ) {
            PlayerTopBar(
                colorScheme = colorScheme,
                showSwipeMinimizeTip = showSwipeMinimizeTip,
                isExpanded = isExpanded,
                onSwipeMinimizeTipDismissed = onSwipeMinimizeTipDismissed,
                onCollapse = onCollapse,
                onShare = { showShareSheet = true },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .then(
                        if (prewarmOnly) Modifier.graphicsLayer { alpha = 0f } else Modifier,
                    ),
            ) {
                PlayerHeroSection(
                    episode = episode,
                    podcast = podcast,
                    queue = state.queue,
                    positionFlow = positionFlow,
                    chapters = state.currentChapters,
                    colorScheme = colorScheme,
                    onSkipPrevious = {
                        skipDirection = -1
                        playbackRepository.skipToPreviousEpisode()
                    },
                    onSkipNext = {
                        skipDirection = 1
                        playbackRepository.skipToNextEpisode()
                    },
                    skipDirection = skipDirection.takeIf { it != 0 },
                    onEpisodeTitleClick = {
                        onTitleTipDismissed()
                        onCollapse()
                        onEpisodeInfoClick(episode)
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.duration > 0) {
                    PlayerSeekBar(
                        positionFlow = positionFlow,
                        durationMs = state.duration,
                        bufferedPositionFlow = bufferedPositionFlow,
                        onSeek = {
                            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("seek")
                            playbackRepository.seekTo(it)
                        },
                        color = controlTint,
                        chapters = state.currentChapters,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                PlayerControlDeck(
                    isPlaying = state.isPlaying,
                    isLoading = state.isLoading,
                    colorScheme = colorScheme,
                    controlTint = controlTint,
                    onPlayPause = {
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("play_pause")
                        if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
                    },
                    onReplay10 = {
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("previous")
                        playbackRepository.skipBackward()
                    },
                    onForward30 = {
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("next")
                        playbackRepository.skipForward()
                    },
                    onSkipPreviousEpisode = {
                        skipDirection = -1
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("skip_previous_episode")
                        playbackRepository.skipToPreviousEpisode()
                    },
                    onSkipNextEpisode = {
                        skipDirection = 1
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("skip_next_episode")
                        playbackRepository.skipToNextEpisode()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { activeOverlay = PlayerOverlaySheet.SpeedSleep },
                        label = { Text("${state.playbackSpeed}x") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Speed, contentDescription = null, Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = colorScheme.surfaceContainerHigh,
                        ),
                    )
                    AssistChip(
                        onClick = { activeOverlay = PlayerOverlaySheet.SpeedSleep },
                        label = {
                            Text(
                                if (state.sleepTimerEnd != null || state.sleepAtEndOfEpisode) "Timer on"
                                else "Sleep",
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Timer, contentDescription = null, Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = colorScheme.surfaceContainerHigh,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                UpNextPeekRow(
                    queue = state.queue,
                    currentEpisodeId = episode.id,
                    colorScheme = colorScheme,
                    onOpenQueue = { activeOverlay = PlayerOverlaySheet.Queue },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                NotesPreviewSection(
                    descriptionHtml = episode.description,
                    colorScheme = colorScheme,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                PlayerActionRail(
                    isLiked = state.isLiked,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    colorScheme = colorScheme,
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
                    onQueueClick = {
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("queue")
                        activeOverlay = PlayerOverlaySheet.Queue
                    },
                    hasChapters = !episode.chaptersUrl.isNullOrEmpty() || state.currentChapters.isNotEmpty(),
                    isChaptersLoading = state.isChaptersLoading,
                    autoTranscriptState = state.autoTranscriptState,
                    autoChaptersState = state.autoChaptersState,
                    onChaptersClick = {
                        activeOverlay = PlayerOverlaySheet.Chapters
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("chapters_sheet")
                    },
                    onTranscriptClick = {
                        activeOverlay = PlayerOverlaySheet.Transcript
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("transcript_view")
                    },
                    onMarkPlayedClick = {
                        scope.launch {
                            playbackRepository.toggleCompletion(
                                episode,
                                podcast.id,
                                podcast.title,
                                podcast.imageUrl,
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = queueSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )

        PlayerSheetsHost(
            activeSheet = activeOverlay,
            onDismiss = { activeOverlay = PlayerOverlaySheet.None },
            isPlayerFullyExpanded = isExpanded,
            colorScheme = colorScheme,
            modifier = Modifier.fillMaxSize(),
            queueContent = {
                QueueSheetV2(
                    queue = state.queue.drop(1),
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
                                        genre = ep.podcastGenre ?: "",
                                    )
                                } else {
                                    podcast
                                }
                                playbackRepository.playFromQueueIndex(ep.id, freshQueue, episodePodcast)
                                activeOverlay = PlayerOverlaySheet.None
                            }
                        },
                        onRemoveEpisode = { ep ->
                            scope.launch {
                                val removed = playbackRepository.removeFromQueue(ep.id, deferSkipSignal = true)
                                if (removed != null) {
                                    val result = queueSnackbarHostState.showSnackbar(
                                        message = "Removed from queue",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        playbackRepository.undoQueueRemoval(removed)
                                    } else {
                                        playbackRepository.confirmQueueRemoval(removed)
                                    }
                                }
                            }
                        },
                        onClose = { activeOverlay = PlayerOverlaySheet.None },
                        onMove = { fromUi, toUi ->
                            playbackRepository.moveQueueItem(
                                QueueMath.uiIndexToQueueIndex(fromUi),
                                QueueMath.uiIndexToQueueIndex(toUi),
                            )
                        },
                        onDragEnd = { episodeId, fromUi, toUi ->
                            scope.launch {
                                playbackRepository.persistQueueOrder(
                                    movedEpisodeId = episodeId,
                                    fromQueueIndex = QueueMath.uiIndexToQueueIndex(fromUi),
                                    toQueueIndex = QueueMath.uiIndexToQueueIndex(toUi),
                                )
                            }
                        },
                    ),
                )
            },
            chaptersContent = {
                ChaptersSheetV2(
                    chapters = state.currentChapters,
                    positionFlow = positionFlow,
                    colorScheme = colorScheme,
                    onSeek = { playbackRepository.seekTo(it) },
                    onClose = { activeOverlay = PlayerOverlaySheet.None },
                    chaptersUrl = episode.chaptersUrl,
                    isChaptersLoading = state.isChaptersLoading,
                    hasTranscript = state.currentTranscript.isNotEmpty(),
                    onGenerateChapters = { playbackRepository.generateAutoChapters() },
                )
            },
            transcriptContent = {
                TranscriptSheetV2(
                    transcript = state.currentTranscript,
                    positionFlow = positionFlow,
                    colorScheme = colorScheme,
                    isSyncEnabled = isSyncEnabled,
                    onSyncEnabledChange = { isSyncEnabled = it },
                    onSeek = { playbackRepository.seekTo(it) },
                    onClose = { activeOverlay = PlayerOverlaySheet.None },
                    transcriptUrl = episode.transcriptUrl,
                )
            },
            speedSleepContent = {
                SpeedSleepSheet(
                    playbackSpeed = state.playbackSpeed,
                    sleepTimerEnd = state.sleepTimerEnd,
                    colorScheme = colorScheme,
                    onSpeedChange = {
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("speed_change", value = it.toString())
                        playbackRepository.setPlaybackSpeed(it)
                    },
                    onSleepClick = { minutes ->
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("sleep_timer", value = minutes?.toString() ?: "off")
                        playbackRepository.setSleepTimer(minutes)
                    },
                    onClose = { activeOverlay = PlayerOverlaySheet.None },
                )
            },
        )

        if (showFullscreenTranscript) {
            FullscreenTranscriptScreen(
                transcript = state.currentTranscript,
                positionFlow = positionFlow,
                isPlaying = state.isPlaying,
                isLoading = state.isLoading,
                durationMs = state.duration,
                colorScheme = colorScheme,
                isSyncEnabled = isSyncEnabled,
                onSyncEnabledChange = { isSyncEnabled = it },
                onSeek = { playbackRepository.seekTo(it) },
                onPlayPause = {
                    if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
                },
                onClose = { showFullscreenTranscript = false },
                transcriptUrl = episode.transcriptUrl,
            )
        }

        if (showShareSheet) {
            val context = LocalContext.current
            ShareBottomSheet(
                id = episode.id,
                type = "episode",
                title = episode.title,
                subtitle = podcast.title,
                onDismissRequest = { showShareSheet = false },
                durationMs = episode.duration * 1000L,
                currentPositionMs = playbackRepository.playerState.value.position,
                showTimestampOption = true,
                onShare = { _, _, timestamp ->
                    cx.aswin.boxcast.core.data.ShareManager.shareEpisode(context, episode, podcast.title, timestamp)
                },
            )
        }

        if (showGenerateConfirmation) {
            GenerateTranscriptDialog(
                colorScheme = colorScheme,
                autoTranscriptLimitLeft = state.autoTranscriptLimitLeft,
                onDismiss = { showGenerateConfirmation = false },
                onConfirm = {
                    showGenerateConfirmation = false
                    playbackRepository.generateAutoTranscript()
                },
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    colorScheme: ColorScheme,
    showSwipeMinimizeTip: Boolean,
    isExpanded: Boolean,
    onSwipeMinimizeTipDismissed: () -> Unit,
    onCollapse: () -> Unit,
    onShare: () -> Unit,
) {
    var tipVisible by remember { mutableStateOf(showSwipeMinimizeTip) }
    LaunchedEffect(showSwipeMinimizeTip, isExpanded) {
        if (showSwipeMinimizeTip && isExpanded) {
            delay(3500)
            tipVisible = false
            onSwipeMinimizeTipDismissed()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable {
                    cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("collapsed")
                    onCollapse()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse", tint = colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (tipVisible && isExpanded) "↓ Swipe down to minimize" else "Now Playing",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (tipVisible && isExpanded) {
                colorScheme.primary.copy(alpha = 0.8f)
            } else {
                colorScheme.onSurface.copy(alpha = 0.7f)
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}

