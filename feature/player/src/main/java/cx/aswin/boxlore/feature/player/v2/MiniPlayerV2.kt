package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.player.v2.logic.ConfirmationVisibility
import cx.aswin.boxlore.feature.player.v2.logic.confirmationTarget
import cx.aswin.boxlore.feature.player.v2.logic.confirmationVisibility
import cx.aswin.boxlore.feature.player.v2.logic.dismissDirection
import cx.aswin.boxlore.feature.player.v2.logic.shouldConfirmDismiss
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MiniPlayerContent(
    val episode: Episode,
    val podcastTitle: String,
    val podcastImageUrl: String?,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val position: Long,
    val duration: Long,
    val seekBackwardSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val showSeekButtons: Boolean = false,
)

data class MiniPlayerColors(
    val colorScheme: ColorScheme,
    val backgroundColor: Color,
)

data class MiniPlayerActions(
    val onPlayPause: () -> Unit,
    val onReplay: () -> Unit,
    val onForward: () -> Unit,
    val onDismiss: () -> Unit,
)

data class MiniPlayerSwipeTip(
    val visible: Boolean = false,
    val onDismissed: () -> Unit = {},
)

@Stable
private class MiniSwipeState {
    val offsetX = Animatable(0f)
    var showConfirmPill by mutableStateOf(false)
        private set
    var direction by mutableIntStateOf(0)
        private set
    private var autoHideJob: Job? = null

    fun onDragStart(haptics: HapticFeedback) {
        autoHideJob?.cancel()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDrag(
        scope: CoroutineScope,
        dragAmount: Float,
        threshold: Float,
    ) {
        scope.launch {
            offsetX.snapTo(offsetX.value + dragAmount)
            when (confirmationVisibility(offsetX.value, threshold, showConfirmPill)) {
                ConfirmationVisibility.SHOW -> {
                    direction = dismissDirection(offsetX.value)
                    showConfirmPill = true
                }
                ConfirmationVisibility.HIDE -> {
                    showConfirmPill = false
                    autoHideJob?.cancel()
                }
                ConfirmationVisibility.UNCHANGED -> Unit
            }
        }
    }

    fun onDragEnd(
        scope: CoroutineScope,
        threshold: Float,
        haptics: HapticFeedback,
    ) {
        scope.launch {
            if (shouldConfirmDismiss(offsetX.value, threshold)) {
                revealConfirmation(scope, threshold, haptics)
            } else {
                hideConfirmation()
            }
        }
    }

    fun onDragCancel(scope: CoroutineScope) {
        scope.launch { hideConfirmation() }
    }

    suspend fun resetForCompact() {
        autoHideJob?.cancel()
        hideConfirmation()
    }

    fun confirmDismiss(
        haptics: HapticFeedback,
        onDismiss: () -> Unit,
    ) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        autoHideJob?.cancel()
        onDismiss()
    }

    private suspend fun revealConfirmation(
        scope: CoroutineScope,
        threshold: Float,
        haptics: HapticFeedback,
    ) {
        direction = dismissDirection(offsetX.value)
        showConfirmPill = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        offsetX.animateTo(
            targetValue = confirmationTarget(offsetX.value, threshold),
            animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
        autoHideJob =
            scope.launch {
                delay(3000)
                hideConfirmation(
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            }
    }

    private suspend fun hideConfirmation(
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
    ) {
        showConfirmPill = false
        offsetX.animateTo(0f, animationSpec)
    }
}

/**
 * v2 mini player: cookie artwork, a wide title above show/seek controls,
 * a circular play button, and progress beneath the labels. Swipe horizontally while paused to reveal
 * a dismiss pill.
 */
@Composable
fun MiniPlayerV2(
    content: MiniPlayerContent,
    colors: MiniPlayerColors,
    actions: MiniPlayerActions,
    swipeTip: MiniPlayerSwipeTip = MiniPlayerSwipeTip(),
    morph: MiniPlayerMorph = MiniPlayerMorph(),
    modifier: Modifier = Modifier,
) {
    val compactFraction = morph.compactFraction
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipeState = remember { MiniSwipeState() }
    val dismissThreshold = with(density) { 100.dp.toPx() }
    val controlsEnabled = compactFraction <= 0.001f && morph.expansionFraction <= 0.001f
    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) swipeState.resetForCompact()
    }

    Box(modifier = modifier) {
        if (controlsEnabled) MiniDismissConfirmation(swipeState, haptics, actions.onDismiss)
        MiniPlayerCard(content, colors, actions, swipeState, scope, dismissThreshold, morph)
        MiniSwipeTipOverlay(
            visible = swipeTip.visible && !content.isPlaying && controlsEnabled,
            onDismissed = swipeTip.onDismissed,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MiniDismissConfirmation(
    swipeState: MiniSwipeState,
    haptics: HapticFeedback,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = swipeState.showConfirmPill,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier =
        Modifier
            .fillMaxSize()
            .zIndex(0f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (swipeState.direction > 0) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Row(
                modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable { swipeState.confirmDismiss(haptics, onDismiss) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Dismiss",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

data class MiniPlayerMorph(
    val compactFraction: Float = 0f,
    val cookieFraction: Float = 0f,
    val rotation: State<Float>? = null,
    val expansionFraction: Float = 0f,
)

@Composable
private fun MiniPlayerCard(
    content: MiniPlayerContent,
    colors: MiniPlayerColors,
    actions: MiniPlayerActions,
    swipeState: MiniSwipeState,
    scope: CoroutineScope,
    dismissThreshold: Float,
    morph: MiniPlayerMorph,
) {
    val compactFraction = morph.compactFraction
    val haptics = LocalHapticFeedback.current
    val chrome = navigationChromeMetrics(LocalNavigationStyle.current)
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .zIndex(1f)
            .offset { IntOffset((swipeState.offsetX.value * (1f - compactFraction)).toInt(), 0) }
            .graphicsLayer {
                shape = AdaptiveMiniPlayerSurfaceShape(
                    chrome.miniPlayerTopCornerRadius,
                    chrome.miniPlayerBottomCornerRadius,
                    morph.cookieFraction,
                    morph.rotation?.value ?: 0f,
                )
                clip = true
            }
            .background(color = colors.backgroundColor.copy(alpha = 1f - morph.cookieFraction))
            .then(
                if (morph.expansionFraction > 0.001f) {
                    Modifier.clearAndSetSemantics {}
                } else if (compactFraction > 0.001f) {
                    Modifier.clearAndSetSemantics {
                        contentDescription = content.episode.title
                        stateDescription = miniPlayerCompactStateDescription(content)
                        if (miniPlayerCompactDuration(content) > 0L) {
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                miniPlayerCompactProgress(content),
                                0f..1f,
                            )
                        }
                    }
                } else {
                    Modifier
                },
            )
            .miniSwipeGesture(
                content.isPlaying || compactFraction > 0.001f || morph.expansionFraction > 0.001f,
                swipeState,
                scope,
                haptics,
                dismissThreshold,
            ),
    ) {
        MiniPlayerRow(content, colors.colorScheme, actions, morph)
    }
}

private fun Modifier.miniSwipeGesture(
    isPlaying: Boolean,
    swipeState: MiniSwipeState,
    scope: CoroutineScope,
    haptics: HapticFeedback,
    dismissThreshold: Float,
): Modifier = pointerInput(isPlaying) {
    if (isPlaying) return@pointerInput
    detectHorizontalDragGestures(
        onDragStart = { swipeState.onDragStart(haptics) },
        onDragEnd = { swipeState.onDragEnd(scope, dismissThreshold, haptics) },
        onDragCancel = { swipeState.onDragCancel(scope) },
        onHorizontalDrag = { _, dragAmount ->
            swipeState.onDrag(scope, dragAmount, dismissThreshold)
        },
    )
}

@Composable
private fun MiniPlayerRow(
    content: MiniPlayerContent,
    colorScheme: ColorScheme,
    actions: MiniPlayerActions,
    morph: MiniPlayerMorph,
) {
    val compactFraction = morph.compactFraction
    val metadataAlpha = (1f - compactFraction / 0.6f).coerceIn(0f, 1f)
    val controlsEnabled = compactFraction <= 0.001f && morph.expansionFraction <= 0.001f
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        MiniPlayerArtwork(
            content = content,
            colorScheme = colorScheme,
            compactFraction = compactFraction,
            rotation = morph.rotation,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = lerp(8.dp, 6.dp, compactFraction)),
        )
        MiniPlayerCompactStatus(
            content = content,
            colorScheme = colorScheme,
            compactFraction = compactFraction,
            cookieFraction = morph.cookieFraction,
            rotation = morph.rotation,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = lerp(6.dp, 0.dp, compactFraction)).size(52.dp),
        )
        if (metadataAlpha > 0f) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 68.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                    .graphicsLayer { alpha = metadataAlpha }
                    .then(
                        if (compactFraction > 0.001f || morph.expansionFraction > 0.001f) {
                            Modifier.clearAndSetSemantics {}
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniPlayerMetadata(
                    content = content,
                    colorScheme = colorScheme,
                    motionEnabled = controlsEnabled,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
                MiniTransportControls(
                    content = content,
                    enabled = controlsEnabled,
                    colorScheme = colorScheme,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerArtwork(
    content: MiniPlayerContent,
    colorScheme: ColorScheme,
    compactFraction: Float,
    rotation: State<Float>?,
    modifier: Modifier = Modifier,
) {
    val imageUrl = content.episode.imageUrl?.takeIf { it.isNotBlank() } ?: content.podcastImageUrl
    val artworkSize = lerp(MiniPlayerArtworkSize, MiniPlayerCompactArtworkSize, compactFraction)
    Box(
        modifier =
        modifier
            .size(artworkSize)
            .graphicsLayer {
                shape = MiniPlayerArtworkShape(rotation?.value ?: 0f)
                clip = true
            }
            .background(colorScheme.surfaceVariant),
    ) {
        OptimizedImage(
            url = imageUrl,
            proxyWidth = 160,
            contentDescription = "Episode artwork",
            contentScale = ContentScale.Crop,
            modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (content.isLoading) 0.62f else 1f },
        )
    }
}

@Composable
private fun MiniPlayerMetadata(
    content: MiniPlayerContent,
    colorScheme: ColorScheme,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = content.episode.title.replace("+", " "),
            style =
            MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = GoogleSansWeight.semiBold,
                letterSpacing = (-0.15).sp,
            ),
            color = colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MiniPlayerSubtitle(content, colorScheme, Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(4.dp))
        MiniPlayerProgress(
            content = content,
            colorScheme = colorScheme,
            motionEnabled = motionEnabled,
            modifier = Modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics {},
        )
    }
}

@Composable
private fun MiniPlayerSubtitle(
    content: MiniPlayerContent,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (content.isCasting) {
            Icon(
                imageVector = Icons.Rounded.CastConnected,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = if (content.isCasting) {
                "Casting to ${content.castDeviceName ?: "Cast device"}"
            } else {
                content.podcastTitle.replace("+", " ")
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
            color = colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniSwipeTipOverlay(
    visible: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, modifier = modifier.fillMaxWidth().zIndex(2f)) {
        SwipeDismissTip(onDismissed = onDismissed)
    }
}

@Composable
private fun SwipeDismissTip(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tipVisible by remember { mutableStateOf(true) }
    var dismissalReported by remember { mutableStateOf(false) }
    val currentOnDismissed = rememberUpdatedState(onDismissed)

    fun reportDismissal() {
        if (!dismissalReported) {
            dismissalReported = true
            currentOnDismissed.value()
        }
    }

    LaunchedEffect(Unit) {
        delay(4000)
        tipVisible = false
        reportDismissal()
    }
    DisposableEffect(Unit) {
        onDispose { reportDismissal() }
    }
    AnimatedVisibility(
        visible = tipVisible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(500)),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "← Swipe to dismiss →",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = GoogleSansWeight.medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
