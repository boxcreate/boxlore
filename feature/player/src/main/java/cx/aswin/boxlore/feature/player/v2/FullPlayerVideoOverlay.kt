package cx.aswin.boxlore.feature.player.v2

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreFoldSafeViewport
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.fitVisibleHeight
import cx.aswin.boxlore.feature.player.formatTime
import cx.aswin.boxlore.feature.player.v2.logic.FullscreenVideoPhase
import cx.aswin.boxlore.feature.player.v2.logic.fullscreenVideoOrientation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Immersive fullscreen video overlay with auto-hiding HUD and rotation toggle. */
internal data class VideoFullscreenContent(
    val visible: Boolean,
    val episodeTitle: String,
    val isPlaying: Boolean,
    val durationMs: Long,
    val seekBackwardSeconds: Int,
    val seekForwardSeconds: Int,
    val positionFlow: kotlinx.coroutines.flow.Flow<Long>,
    val controller: androidx.media3.common.Player?,
    /** The part of the player's container on screen; the overlay fits it so its controls stay visible. */
    val visibleHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified
)

internal data class VideoFullscreenActions(
    val onExit: () -> Unit,
    val onPlayPause: () -> Unit,
    val onReplay: () -> Unit,
    val onForward: () -> Unit,
    val onSeek: (Long) -> Unit
)

@Composable
internal fun VideoFullscreenOverlay(
    content: VideoFullscreenContent,
    colorScheme: ColorScheme,
    actions: VideoFullscreenActions
) {
    var isLandscape by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val activity = LocalActivity.current

    FullscreenVideoSystemUi(
        visible = content.visible,
        isLandscape = isLandscape,
        activity = activity,
        onExit = actions.onExit
    )

    AnimatedVisibility(
        visible = content.visible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)),
        modifier = Modifier.fitVisibleHeight(content.visibleHeight).fillMaxSize()
    ) {
        LaunchedEffect(controlsVisible, content.isPlaying) {
            if (controlsVisible && content.isPlaying) {
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
                VideoPlayerView(content.controller)

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .boxloreFoldSafeViewport()
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            // Bars are hidden here, but a camera cutout still sits over the picture.
                            .windowInsetsPadding(WindowInsets.displayCutout)
                    ) {
                        VideoHudTopBar(
                            episodeTitle = content.episodeTitle,
                            onExit = actions.onExit,
                            onRotate = { isLandscape = !isLandscape },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        VideoTransportControls(
                            isPlaying = content.isPlaying,
                            seekBackwardSeconds = content.seekBackwardSeconds,
                            seekForwardSeconds = content.seekForwardSeconds,
                            colorScheme = colorScheme,
                            actions = actions,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        VideoProgressControls(
                            content = content,
                            colorScheme = colorScheme,
                            onSeek = actions.onSeek,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullscreenVideoSystemUi(
    visible: Boolean,
    isLandscape: Boolean,
    activity: android.app.Activity?,
    onExit: () -> Unit
) {
    if (!visible) return
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    // Read once per full-screen session, before this overlay requests anything.
    val originalOrientation = remember(activity) { activity?.requestedOrientation }
    // Runs again when the layout changes while the video is open: unfolding to the inner screen gives
    // rotation back at once, and folding to the cover turns it sideways again.
    DisposableEffect(isLandscape, tablet) {
        fullscreenVideoOrientation(FullscreenVideoPhase.Enter, isLandscape, tablet, originalOrientation)
            ?.let { activity?.requestedOrientation = it }
        val window = activity?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(videoOverlayBarTypes())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            fullscreenVideoOrientation(FullscreenVideoPhase.Exit, isLandscape, tablet, originalOrientation)
                ?.let { activity?.requestedOrientation = it }
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .show(videoOverlayBarTypes())
            }
        }
    }
    BackHandler(onBack = onExit)
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun VideoPlayerView(controller: androidx.media3.common.Player?) {
    if (controller == null) return
    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
    DisposableEffect(controller) {
        onDispose { playerViewRef?.player = null }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            androidx.media3.ui.PlayerView(context).apply {
                player = controller
                useController = false
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

@Composable
internal fun VideoHudTopBar(
    episodeTitle: String,
    onExit: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(
                onClick = onExit,
                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = stringResource(R.string.player_exit_fullscreen), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = episodeTitle.replace("+", " "),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onRotate,
            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(Icons.Rounded.ScreenRotation, contentDescription = stringResource(R.string.player_toggle_orientation), tint = Color.White)
        }
    }
}

@Composable
internal fun VideoTransportControls(
    isPlaying: Boolean,
    seekBackwardSeconds: Int,
    seekForwardSeconds: Int,
    colorScheme: ColorScheme,
    actions: VideoFullscreenActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoSeekButton(
            seconds = seekBackwardSeconds,
            forward = false,
            contentDescription =
            cx.aswin.boxlore.feature.player.seekDurationContentDescription(
                seekBackwardSeconds,
                forward = false,
            ),
            onClick = actions.onReplay,
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .expressiveClickable(shape = CircleShape, feedback = ExpressivePressFeedback.Control, onClick = actions.onPlayPause)
                .background(colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.minimal_pause else R.string.minimal_play),
                modifier = Modifier.size(36.dp),
                tint = colorScheme.onPrimaryContainer
            )
        }
        VideoSeekButton(
            seconds = seekForwardSeconds,
            forward = true,
            contentDescription =
            cx.aswin.boxlore.feature.player.seekDurationContentDescription(
                seekForwardSeconds,
                forward = true,
            ),
            onClick = actions.onForward,
        )
    }
}

@Composable
internal fun VideoSeekButton(
    seconds: Int,
    forward: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(Color.White.copy(alpha = 0.15f), CircleShape)
    ) {
        cx.aswin.boxlore.feature.player.SeekDurationIcon(
            seconds = seconds,
            forward = forward,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
internal fun VideoProgressControls(
    content: VideoFullscreenContent,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val positionMs by content.positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    var draggedPosition by remember { mutableStateOf<Float?>(null) }
    val displayedPosition = draggedPosition?.toLong() ?: positionMs
    val seekLabel = stringResource(R.string.player_seek)
    if (content.durationMs <= 0) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Slider(
            value = draggedPosition ?: positionMs.toFloat(),
            onValueChange = { draggedPosition = it },
            onValueChangeFinished = {
                draggedPosition?.let { onSeek(it.toLong()) }
                draggedPosition = null
            },
            valueRange = 0f..content.durationMs.toFloat(),
            // Named like the player's seekbar, with the time instead of a bare percentage.
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = seekLabel
                    stateDescription = "${formatTime(displayedPosition)} / ${formatTime(content.durationMs)}"
                },
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(displayedPosition),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.semiBold),
                color = Color.White
            )
            Text(
                text = "-" + formatTime((content.durationMs - displayedPosition).coerceAtLeast(0)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.semiBold),
                color = Color.White
            )
        }
    }
}

/**
 * Status and navigation bars only. `systemBars()` also includes system overlays, which androidx.core
 * maps through an API 34 method that some devices reporting API 34 do not have.
 */
internal fun videoOverlayBarTypes(): Int = androidx.core.view.WindowInsetsCompat.Type.statusBars() or
    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
