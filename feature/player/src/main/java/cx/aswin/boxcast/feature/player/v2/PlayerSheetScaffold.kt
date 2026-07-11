package cx.aswin.boxcast.feature.player.v2

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import cx.aswin.boxcast.core.designsystem.theme.LocalEffectiveDarkTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class PlayerSheetValue { Collapsed, Expanded }

/**
 * v2 player sheet: an [AnchoredDraggableState]-driven bottom sheet that morphs between
 * a mini bar and the immersive full player. Springs everywhere, velocity-aware settling,
 * nested-scroll handoff with the full player content, and predictive-back collapse.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetScaffold(
    playbackRepository: PlaybackRepository,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    userPrefs: UserPreferencesRepository,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    expandTrigger: Long = 0L,
    onEpisodeInfoClick: (cx.aswin.boxcast.core.model.Episode) -> Unit = {},
    onPodcastInfoClick: (cx.aswin.boxcast.core.model.Podcast) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by playbackRepository.playerState.collectAsState()
    val episode = state.currentEpisode
    val podcast = state.currentPodcast

    if (episode == null) return

    var isFullscreenVideo by rememberSaveable(inputs = arrayOf(episode.id)) { mutableStateOf(false) }

    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val window = (context as? android.app.Activity)?.window

    // Tooltips
    val hasSeenSwipeDismissTip by userPrefs.hasSeenSwipeDismissTip.collectAsState(initial = true)
    val hasSeenSwipeMinimizeTip by userPrefs.hasSeenSwipeMinimizeTip.collectAsState(initial = true)

    val effectiveDarkTheme = LocalEffectiveDarkTheme.current
    SideEffect {
        window?.let { win ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !effectiveDarkTheme
            insetsController.isAppearanceLightNavigationBars = !effectiveDarkTheme
        }
    }

    // Artwork-seeded color scheme
    val colorScheme = rememberPlayerColorScheme(episode.imageUrl)

    // ------------------------------------------------------------------
    // Anchored draggable sheet state
    // ------------------------------------------------------------------

    val sheetState = remember(density) {
        AnchoredDraggableState(
            initialValue = PlayerSheetValue.Collapsed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 180.dp.toPx() } },
            snapAnimationSpec = spring(
                dampingRatio = 1f,
                stiffness = 75f
            ),
            decayAnimationSpec = exponentialDecay()
        )
    }

    LaunchedEffect(sheetCollapsedTargetY) {
        sheetState.updateAnchors(
            DraggableAnchors {
                PlayerSheetValue.Collapsed at sheetCollapsedTargetY
                PlayerSheetValue.Expanded at 0f
            }
        )
    }

    val sheetOffset by remember(sheetState, sheetCollapsedTargetY) {
        derivedStateOf {
            val raw = sheetState.offset
            if (raw.isNaN()) sheetCollapsedTargetY else raw.coerceIn(0f, sheetCollapsedTargetY)
        }
    }
    val expansionFraction by remember(sheetCollapsedTargetY) {
        derivedStateOf {
            if (sheetCollapsedTargetY <= 0f) 0f
            else (1f - sheetOffset / sheetCollapsedTargetY).coerceIn(0f, 1f)
        }
    }
    val isExpanded = sheetState.currentValue == PlayerSheetValue.Expanded

    fun expandSheet() {
        if (sheetState.isAnimationRunning || sheetOffset <= 0.5f) return
        scope.launch { sheetState.animateTo(PlayerSheetValue.Expanded) }
    }

    fun collapseSheet() {
        if (sheetState.isAnimationRunning || sheetOffset >= sheetCollapsedTargetY - 0.5f) return
        scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) }
    }

    // Session + haptics on settled-state transitions
    LaunchedEffect(sheetState) {
        var previous = sheetState.settledValue
        snapshotFlow { sheetState.settledValue }.collect { value ->
            if (value != previous) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (value == PlayerSheetValue.Expanded) {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                        "expanded", podcast?.id, episode.id, podcast?.title, episode.title
                    )
                    cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.startSession(
                        podcast?.id, episode.id, podcast?.title, episode.title
                    )
                } else {
                    cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.endSession()
                }
                previous = value
            }
        }
    }

    // External expansion trigger (notification tap, deep link, resume)
    LaunchedEffect(expandTrigger) {
        if (expandTrigger > 0L && !sheetState.isAnimationRunning) {
            sheetState.animateTo(PlayerSheetValue.Expanded)
        }
    }

    // Predictive back scrubs the collapse
    PredictiveBackHandler(enabled = isExpanded && !isFullscreenVideo) { progress ->
        try {
            progress.collect { backEvent ->
                // Scrub the sheet up to 20% of its travel while the gesture is in progress
                val target = sheetCollapsedTargetY * 0.2f * backEvent.progress
                sheetState.dispatchRawDelta(target - sheetState.requireOffset())
            }
            // Gesture committed — collapse
            sheetState.animateTo(PlayerSheetValue.Collapsed)
        } catch (e: CancellationException) {
            // Gesture cancelled — spring back to expanded
            sheetState.animateTo(PlayerSheetValue.Expanded)
            throw e
        }
    }

    // Nested-scroll handoff: the full player's inner scroll drives the sheet when
    // pulling down from the top or when the sheet sits between anchors.
    val sheetNestedScrollConnection = remember(sheetState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Dragging up while the sheet isn't fully expanded: move the sheet first
                return if (delta < 0 && sheetState.requireOffset() > 0f) {
                    Offset(0f, sheetState.dispatchRawDelta(delta))
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Downward overscroll left over by the content: collapse the sheet
                return Offset(0f, sheetState.dispatchRawDelta(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (available.y < 0 && sheetState.requireOffset() > 0f) {
                    sheetState.settle(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (available.y != 0f && !sheetState.isAnimationRunning) {
                    sheetState.settle(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Geometry derived from the expansion fraction
    // ------------------------------------------------------------------

    val sheetHeight by remember(containerHeight) {
        derivedStateOf { lerp(MiniPlayerHeight, containerHeight, expansionFraction) }
    }
    val topCornerRadius by remember {
        derivedStateOf { lerp(26.dp, 0.dp, expansionFraction) }
    }
    val bottomCornerRadius by remember {
        derivedStateOf { lerp(14.dp, 0.dp, expansionFraction) }
    }
    val horizontalPadding by remember(collapsedStateHorizontalPadding) {
        derivedStateOf { lerp(collapsedStateHorizontalPadding, 0.dp, expansionFraction) }
    }
    val sheetElevation by remember {
        derivedStateOf { lerp(3.dp, 16.dp, expansionFraction) }
    }
    val miniAlpha by remember {
        derivedStateOf { (1f - expansionFraction * 2f).coerceIn(0f, 1f) }
    }
    val fullAlpha by remember {
        derivedStateOf { ((expansionFraction - 0.25f).coerceIn(0f, 0.75f) / 0.75f) }
    }
    val fullEntranceOffsetPx = remember(density) { with(density) { 24.dp.toPx() } }
    val fullTranslationY by remember {
        derivedStateOf { lerp(fullEntranceOffsetPx, 0f, fullAlpha) }
    }

    // ------------------------------------------------------------------
    // Sheet UI
    // ------------------------------------------------------------------

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, sheetOffset.roundToInt()) }
            .graphicsLayer { clip = false }
            .height(sheetHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .height(sheetHeight)
                .shadow(
                    elevation = sheetElevation,
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    ),
                    clip = false
                )
                .background(
                    color = miniSheetColor(colorScheme),
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    )
                )
                .clip(
                    RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    )
                )
                .anchoredDraggable(
                    state = sheetState,
                    orientation = Orientation.Vertical,
                    enabled = !isFullscreenVideo
                )
                .clickable(
                    enabled = !isExpanded && !isFullscreenVideo,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expandSheet()
                }
        ) {
            Crossfade(
                targetState = colorScheme,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "playerColorScheme"
            ) { scheme ->
                Box(modifier = Modifier.fillMaxSize()) {
                    // MINI PLAYER
                    MiniPlayerV2(
                        episode = episode,
                        podcastTitle = podcast?.title ?: "",
                        podcastImageUrl = podcast?.imageUrl,
                        isPlaying = state.isPlaying,
                        isLoading = state.isLoading,
                        position = state.position,
                        duration = state.duration,
                        colorScheme = scheme,
                        onPlayPause = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                "play_pause", podcast?.id, episode.id, podcast?.title, episode.title
                            )
                            if (state.isPlaying) {
                                playbackRepository.pause()
                            } else {
                                playbackRepository.resume(
                                    android.os.Bundle().apply { putString("entry_point", "resume_mini_player") }
                                )
                            }
                        },
                        onReplay = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                "previous", podcast?.id, episode.id, podcast?.title, episode.title
                            )
                            playbackRepository.skipBackward()
                        },
                        onForward = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                "next", podcast?.id, episode.id, podcast?.title, episode.title
                            )
                            playbackRepository.skipForward()
                        },
                        onDismiss = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                "dismissed", podcast?.id, episode.id, podcast?.title, episode.title
                            )
                            playbackRepository.clearSession()
                        },
                        backgroundColor = miniSheetColor(scheme),
                        showSwipeTip = !hasSeenSwipeDismissTip && !state.isPlaying,
                        onSwipeTipDismissed = { scope.launch { userPrefs.markSwipeDismissTipSeen() } },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .height(MiniPlayerHeight)
                            .fillMaxWidth()
                            .graphicsLayer { alpha = miniAlpha }
                            .zIndex(if (expansionFraction < 0.5f) 1f else 0f)
                    )

                    // FULL PLAYER
                    Box(
                        modifier = Modifier
                            .height(containerHeight) // Fixed height prevents layout thrash while morphing
                            .graphicsLayer {
                                alpha = fullAlpha
                                translationY = fullTranslationY
                            }
                            .zIndex(if (expansionFraction >= 0.5f) 1f else 0f)
                            .offset {
                                if (expansionFraction <= 0.01f) IntOffset(0, 10000) else IntOffset.Zero
                            }
                    ) {
                        FullPlayerV2(
                            playbackRepository = playbackRepository,
                            downloadRepository = downloadRepository,
                            colorScheme = scheme,
                            isFullscreenVideo = isFullscreenVideo,
                            onFullscreenVideoChange = { isFullscreenVideo = it },
                            onCollapse = { collapseSheet() },
                            onEpisodeInfoClick = onEpisodeInfoClick,
                            onPodcastInfoClick = onPodcastInfoClick,
                            sheetNestedScrollConnection = sheetNestedScrollConnection,
                            isExpanded = expansionFraction >= 0.5f,
                            showSwipeMinimizeTip = !hasSeenSwipeMinimizeTip,
                            onSwipeMinimizeTipDismissed = { scope.launch { userPrefs.markSwipeMinimizeTipSeen() } }
                        )
                    }
                }
            }
        }
    }
}
