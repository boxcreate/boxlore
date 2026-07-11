package cx.aswin.boxcast.feature.player.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.model.Episode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

val MiniPlayerHeight = 72.dp

/**
 * v2 mini player: squircle artwork, marquee-free two-line labels, transport buttons,
 * and a hairline wavy progress indicator. Swipe horizontally while paused to reveal
 * a dismiss pill.
 */
@Composable
fun MiniPlayerV2(
    episode: Episode,
    podcastTitle: String,
    podcastImageUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: Long,
    duration: Long,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit,
    onDismiss: () -> Unit,
    backgroundColor: Color,
    showSwipeTip: Boolean = false,
    onSwipeTipDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Swipe-to-dismiss (only while paused)
    val offsetX = remember { Animatable(0f) }
    var showConfirmPill by remember { mutableStateOf(false) }
    var swipeDirection by remember { mutableStateOf(0) }
    var autoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val dismissThreshold = with(density) { 100.dp.toPx() }

    Box(modifier = modifier) {
        // Dismiss pill revealed behind the sliding content
        AnimatedVisibility(
            visible = showConfirmPill,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (swipeDirection > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            autoHideJob?.cancel()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Sliding content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(
                        topStart = 26.dp,
                        topEnd = 26.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 26.dp,
                        topEnd = 26.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .pointerInput(isPlaying) {
                    if (isPlaying) return@pointerInput // No swipe-dismiss while playing
                    detectHorizontalDragGestures(
                        onDragStart = {
                            autoHideJob?.cancel()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            scope.launch {
                                if (kotlin.math.abs(offsetX.value) > dismissThreshold) {
                                    swipeDirection = if (offsetX.value < 0) -1 else 1
                                    showConfirmPill = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    offsetX.animateTo(
                                        targetValue = swipeDirection * dismissThreshold * 1.5f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    autoHideJob = scope.launch {
                                        delay(3000)
                                        showConfirmPill = false
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                } else {
                                    showConfirmPill = false
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                showConfirmPill = false
                                offsetX.animateTo(0f, spring())
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount)
                                if (kotlin.math.abs(offsetX.value) > dismissThreshold * 0.5f && !showConfirmPill) {
                                    swipeDirection = if (offsetX.value < 0) -1 else 1
                                    showConfirmPill = true
                                }
                                if (showConfirmPill && kotlin.math.abs(offsetX.value) < dismissThreshold * 0.3f) {
                                    showConfirmPill = false
                                    autoHideJob?.cancel()
                                }
                            }
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcastImageUrl
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(
                            AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 16.dp, smoothnessAsPercentTL = 60,
                                cornerRadiusTR = 16.dp, smoothnessAsPercentTR = 60,
                                cornerRadiusBL = 16.dp, smoothnessAsPercentBL = 60,
                                cornerRadiusBR = 16.dp, smoothnessAsPercentBR = 60
                            )
                        )
                        .background(colorScheme.surfaceVariant)
                ) {
                    OptimizedImage(
                        url = imageUrl,
                        proxyWidth = 160,
                        contentDescription = "Episode artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (isLoading) 0.62f else 1f }
                    )
                }

                Spacer(modifier = Modifier.width(11.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = episode.title.replace("+", " "),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.15).sp
                        ),
                        color = colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = podcastTitle.replace("+", " "),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            letterSpacing = 0.sp
                        ),
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (duration > 0) {
                        val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        Spacer(modifier = Modifier.height(3.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = colorScheme.primary,
                            trackColor = colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                MiniTransportButtons(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    colorScheme = colorScheme,
                    onPlayPause = onPlayPause,
                    onReplay = onReplay,
                    onForward = onForward
                )
            }
        }

        // One-time swipe tip overlay
        if (showSwipeTip && !isPlaying) {
            var tipVisible by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(4000)
                tipVisible = false
                onSwipeTipDismissed()
            }
            AnimatedVisibility(
                visible = tipVisible,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(500)),
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .align(Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "← Swipe to dismiss →",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTransportButtons(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val replayScale = remember { Animatable(1f) }
    val forwardScale = remember { Animatable(1f) }
    val playScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniSeekButton(
            scaleAnim = replayScale,
            icon = Icons.Rounded.Replay10,
            contentDescription = "Seek back 10 seconds",
            isLoading = isLoading,
            colorScheme = colorScheme,
            shape = RoundedCornerShape(13.dp),
            onClick = onReplay
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = playScale.value
                    scaleY = playScale.value
                }
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 14.dp, smoothnessAsPercentTL = 60,
                        cornerRadiusTR = 14.dp, smoothnessAsPercentTR = 60,
                        cornerRadiusBL = 14.dp, smoothnessAsPercentBL = 60,
                        cornerRadiusBR = 14.dp, smoothnessAsPercentBR = 60
                    )
                )
                .background(colorScheme.primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    enabled = !isLoading
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        playScale.snapTo(0.82f)
                        playScale.animateTo(
                            1f,
                            spring(
                                dampingRatio = 0.42f,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = isLoading,
                animationSpec = tween(220),
                label = "miniLoading"
            ) { loading ->
                if (loading) {
                    BoxLoreLoader.CircularWavy(
                        modifier = Modifier.size(24.dp),
                        color = colorScheme.onPrimary,
                        trackColor = colorScheme.onPrimary.copy(alpha = 0.24f)
                    )
                } else {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(180),
                        label = "miniPlayPause"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        MiniSeekButton(
            scaleAnim = forwardScale,
            icon = Icons.Rounded.Forward30,
            contentDescription = "Seek forward 30 seconds",
            isLoading = isLoading,
            colorScheme = colorScheme,
            shape = RoundedCornerShape(13.dp),
            onClick = onForward
        )
    }
}

@Composable
private fun MiniSeekButton(
    scaleAnim: Animatable<Float, *>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    shape: Shape,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
            .clip(shape)
            .background(colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                enabled = !isLoading
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    scaleAnim.snapTo(0.82f)
                    scaleAnim.animateTo(
                        1f,
                        spring(dampingRatio = 0.46f, stiffness = Spring.StiffnessMedium)
                    )
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = colorScheme.onPrimaryContainer.copy(alpha = if (isLoading) 0.45f else 0.82f),
            modifier = Modifier.size(21.dp)
        )
    }
}
