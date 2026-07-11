package cx.aswin.boxcast.feature.player.v2

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue

/**
 * Full-player hero: Material 3 carousel artwork (or video viewport) that reacts to playback.
 * - Scales down slightly when paused, springs back on play.
 * - Crossfades to chapter art when the active chapter carries an image.
 * - Peeks the queued episode and commits it when that card settles.
 */
@Composable
@Suppress("kotlin:S107", "kotlin:S3776")
fun PlayerHero(
    episodeId: String,
    artworkUrl: String?,
    nextArtworkUrl: String?,
    nextEpisodeTitle: String?,
    chapterArtFlow: Flow<String?>,
    isPlaying: Boolean,
    isVideo: Boolean,
    isFullscreenVideo: Boolean,
    controller: androidx.media3.common.Player?,
    width: Dp,
    height: Dp,
    isExpanded: Boolean,
    colorScheme: ColorScheme,
    onSkipNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    // Breathing motion: shrink slightly when paused, spring back on play
    val heroScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heroScale"
    )

    val chapterArt by chapterArtFlow.collectAsState(initial = null)
    val displayedArt = chapterArt?.takeIf { it.isNotBlank() } ?: artworkUrl

    val heroShape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = heroScale
            scaleY = heroScale
        },
        contentAlignment = Alignment.Center
    ) {
        if (isVideo && controller != null && !isFullscreenVideo) {
            var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
            DisposableEffect(controller) {
                onDispose { playerViewRef?.player = null }
            }
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = controller
                        useController = false // BoxLore controls instead of the default overlay
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        playerViewRef = this
                    }
                },
                update = { playerView ->
                    if (playerView.player != controller) playerView.player = controller
                    playerViewRef = playerView
                },
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .shadow(elevation = 12.dp, shape = heroShape, clip = false)
                    .clip(heroShape)
            )
        } else if (nextArtworkUrl.isNullOrBlank() || !isExpanded) {
            PlayerArtworkCard(
                artworkUrl = displayedArt,
                colorScheme = colorScheme,
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .shadow(elevation = 12.dp, shape = heroShape, clip = false)
            )
        } else {
            key(episodeId) {
                val pagerState = rememberPagerState { 2 }

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }
                        .filter { settledPage -> settledPage == 1 }
                        .first()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSkipNextEpisode()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { index ->
                        val pageOffset = (
                            (pagerState.currentPage - index) +
                                pagerState.currentPageOffsetFraction
                            ).absoluteValue.coerceIn(0f, 1f)

                        if (index == 0) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PlayerArtworkCard(
                                    artworkUrl = displayedArt,
                                    colorScheme = colorScheme,
                                    modifier = Modifier
                                        .width(width)
                                        .height(height)
                                        .graphicsLayer {
                                            val scale = 1f - (pageOffset * 0.055f)
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = 1f - (pageOffset * 0.14f)
                                        }
                                        .shadow(elevation = 12.dp, shape = heroShape, clip = false)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PlayerArtworkCard(
                                    artworkUrl = nextArtworkUrl,
                                    colorScheme = colorScheme,
                                    contentDescription = "Up next: $nextEpisodeTitle",
                                    modifier = Modifier
                                        .width(width)
                                        .height(height)
                                        .graphicsLayer {
                                            val scale = 1f - (pageOffset * 0.055f)
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = 1f - (pageOffset * 0.14f)
                                        }
                                        .shadow(elevation = 12.dp, shape = heroShape, clip = false)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(width + 88.dp)
                            .height(height)
                    ) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(32.dp),
                            shape = CircleShape,
                            color = colorScheme.surface.copy(alpha = 0.72f),
                            contentColor = colorScheme.onSurfaceVariant,
                            shadowElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = "Swipe for next episode",
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerArtworkCard(
    artworkUrl: String?,
    colorScheme: ColorScheme,
    contentDescription: String = "Album Art",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.surfaceVariant
    ) {
        Crossfade(
            targetState = artworkUrl,
            animationSpec = tween(durationMillis = 500),
            label = "heroArtCrossfade"
        ) { url ->
            OptimizedImage(
                url = url,
                proxyWidth = 800,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Builds a flow of the active chapter's artwork URL for the current position. */
fun chapterArtFlow(
    positionFlow: Flow<Long>,
    chapters: List<cx.aswin.boxcast.core.model.Chapter>
): Flow<String?> = positionFlow
    .map { pos -> chapters.lastOrNull { (it.startTime * 1000).toLong() <= pos }?.img }
    .distinctUntilChanged()

/** Video mode toggles shown under the hero for video episodes. */
@Composable
fun VideoModeButtons(
    isAudioOnly: Boolean,
    colorScheme: ColorScheme,
    onAudioOnlyChange: (Boolean) -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAudioOnly) {
                FilledTonalButton(
                    onClick = { onAudioOnlyChange(false) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Video", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                FilledTonalButton(
                    onClick = { onAudioOnlyChange(true) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Headset, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Only", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = onFullscreenClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fullscreen", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
