package cx.aswin.boxcast.feature.player.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.SleepTimerConstants
import cx.aswin.boxcast.feature.player.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Full-width Material 3 Expressive button group. Coordinated segment shapes and
 * deliberate weights create one visual object with a dominant play/pause action.
 */
@Composable
fun PrimaryControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSources = remember { List(3) { MutableInteractionSource() } }
    val replayPressed by interactionSources[0].collectIsPressedAsState()
    val playPressed by interactionSources[1].collectIsPressedAsState()
    val forwardPressed by interactionSources[2].collectIsPressedAsState()
    val pressedIndex = when {
        replayPressed -> 0
        playPressed -> 1
        forwardPressed -> 2
        else -> null
    }
    var latchedIndex by remember { mutableStateOf<Int?>(null) }
    var interactionToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionToken) {
        if (interactionToken > 0) {
            delay(110)
            latchedIndex = null
        }
    }
    val activeIndex = pressedIndex ?: latchedIndex
    val baseWeights = remember { listOf(1f, 1.18f, 1f) }
    fun targetWeight(index: Int): Float {
        val active = activeIndex ?: return baseWeights[index]
        val extra = baseWeights[active] * 0.14f
        return if (index == active) {
            baseWeights[index] + extra
        } else {
            val otherTotal = baseWeights.sum() - baseWeights[active]
            baseWeights[index] - extra * (baseWeights[index] / otherTotal)
        }
    }
    val softGroupSpring = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 65f
    )
    val replayWeight by animateFloatAsState(targetWeight(0), softGroupSpring, label = "replayWidth")
    val playWeight by animateFloatAsState(targetWeight(1), softGroupSpring, label = "playWidth")
    val forwardWeight by animateFloatAsState(targetWeight(2), softGroupSpring, label = "forwardWidth")
    val replayShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 34.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 14.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 34.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 14.dp, smoothnessAsPercentBR = 60
    )
    val forwardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 14.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 34.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 14.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 34.dp, smoothnessAsPercentBR = 60
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = "Replay 10 seconds",
            colorScheme = colorScheme,
            shape = replayShape,
            iconSize = 40.dp,
            interactionSource = interactionSources[0],
            onClick = {
                latchedIndex = 0
                interactionToken++
                onReplay()
            },
            modifier = Modifier
                .weight(replayWeight)
                .height(88.dp)
        )
        MorphingPlayButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            colorScheme = colorScheme,
            interactionSource = interactionSources[1],
            onClick = {
                latchedIndex = 1
                interactionToken++
                onPlayPause()
            },
            modifier = Modifier
                .weight(playWeight)
                .height(104.dp)
        )
        TransportButton(
            icon = Icons.Rounded.Forward30,
            contentDescription = "Forward 30 seconds",
            colorScheme = colorScheme,
            shape = forwardShape,
            iconSize = 40.dp,
            interactionSource = interactionSources[2],
            onClick = {
                latchedIndex = 2
                interactionToken++
                onForward()
            },
            modifier = Modifier
                .weight(forwardWeight)
                .height(88.dp)
        )
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    colorScheme: ColorScheme,
    quiet: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape,
    iconSize: androidx.compose.ui.unit.Dp,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (quiet) colorScheme.surface.copy(alpha = 0.42f)
                else colorScheme.primary.copy(alpha = 0.15f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (quiet) colorScheme.onSurfaceVariant else colorScheme.primary
        )
    }
}

@Composable
private fun MorphingPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var showLoader by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(500)
            showLoader = true
        } else {
            showLoader = false
        }
    }
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 30.dp else 46.dp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 65f
        ),
        label = "playCorner"
    )
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = corner, smoothnessAsPercentTL = 60,
        cornerRadiusTR = corner, smoothnessAsPercentTR = 60,
        cornerRadiusBL = corner, smoothnessAsPercentBL = 60,
        cornerRadiusBR = corner, smoothnessAsPercentBR = 60
    )
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(colorScheme.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showLoader) {
            BoxLoreLoader.CircularWavy(
                size = 40.dp,
                color = colorScheme.onPrimary
            )
        } else {
            Crossfade(targetState = isPlaying, label = "playPauseCrossfade") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(44.dp),
                    tint = colorScheme.onPrimary
                )
            }
        }
    }
}

private enum class QuickControlMode { ACTIONS, SPEED, SLEEP }

/** Two visible rows expose every player feature without an overflow or horizontal scroll. */
@Composable
fun SecondaryRail(
    playbackSpeed: Float,
    sleepTimerEnd: Long?,
    sleepAtEndOfEpisode: Boolean,
    hasChapters: Boolean,
    hasTranscript: Boolean,
    isTranscriptVisible: Boolean,
    isChaptersLoading: Boolean,
    autoTranscriptState: AutoTranscriptState,
    autoChaptersState: AutoTranscriptState,
    isLiked: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPlayed: Boolean,
    colorScheme: ColorScheme,
    onSpeedSelected: (Float) -> Unit,
    onSleepTimerSelected: (Int) -> Unit,
    onQueueClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onTranscriptClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMarkPlayedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var controlMode by rememberSaveable { mutableStateOf(QuickControlMode.ACTIONS) }
    val isChaptersBusy = isChaptersLoading || autoChaptersState == AutoTranscriptState.GENERATING
    val isTranscriptBusy = autoTranscriptState == AutoTranscriptState.GENERATING
    val groupTrayShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 32.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 32.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 32.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 32.dp, smoothnessAsPercentBR = 60
    )
    val groupTopStartShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 24.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupMiddleShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupTopEndShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 24.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupBottomStartShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 24.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupBottomEndShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 24.dp, smoothnessAsPercentBR = 60
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupTrayShape)
            .background(colorScheme.primary.copy(alpha = 0.12f))
            .padding(8.dp)
    ) {
        AnimatedContent(
            targetState = controlMode,
            transitionSpec = {
                val slideSpring = spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 0.84f,
                    stiffness = 240f
                )
                val transition = if (targetState == QuickControlMode.ACTIONS) {
                    (
                        slideInHorizontally(slideSpring) { fullWidth -> -fullWidth / 5 } +
                            fadeIn(tween(180)) +
                            scaleIn(tween(220), initialScale = 0.98f)
                        ) togetherWith (
                        slideOutHorizontally(tween(150)) { fullWidth -> fullWidth / 6 } +
                            fadeOut(tween(130)) +
                            scaleOut(tween(150), targetScale = 0.98f)
                        )
                } else {
                    (
                        slideInHorizontally(slideSpring) { fullWidth -> fullWidth / 5 } +
                            fadeIn(tween(180)) +
                            scaleIn(tween(220), initialScale = 0.98f)
                        ) togetherWith (
                        slideOutHorizontally(tween(150)) { fullWidth -> -fullWidth / 6 } +
                            fadeOut(tween(130)) +
                            scaleOut(tween(150), targetScale = 0.98f)
                        )
                }
                transition.using(SizeTransform(clip = false))
            },
            label = "quickControlMode"
        ) { mode ->
            when (mode) {
                QuickControlMode.ACTIONS -> Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        UtilityAction(
                            icon = Icons.Rounded.Speed,
                            label = formatSpeedLabel(playbackSpeed),
                            active = kotlin.math.abs(playbackSpeed - 1f) > 0.001f,
                            status = formatSpeedLabel(playbackSpeed)
                                .takeIf { kotlin.math.abs(playbackSpeed - 1f) > 0.001f },
                            colorScheme = colorScheme,
                            shape = groupTopStartShape,
                            onClick = { controlMode = QuickControlMode.SPEED },
                            modifier = Modifier.weight(1f)
                        )
                        SleepAction(
                            sleepTimerEnd = sleepTimerEnd,
                            colorScheme = colorScheme,
                            shape = groupMiddleShape,
                            onClick = { controlMode = QuickControlMode.SLEEP },
                            modifier = Modifier.weight(1f)
                        )
                        UtilityAction(
                            icon = if (isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                            label = when {
                                isDownloading -> "Saving"
                                isDownloaded -> "Saved"
                                else -> "Download"
                            },
                            active = isDownloaded,
                            loading = isDownloading,
                            colorScheme = colorScheme,
                            shape = groupMiddleShape,
                            onClick = onDownloadClick,
                            modifier = Modifier.weight(1f)
                        )
                        UtilityAction(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            label = "Queue",
                            colorScheme = colorScheme,
                            shape = groupTopEndShape,
                            onClick = onQueueClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        UtilityAction(
                            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            label = if (isLiked) "Liked" else "Like",
                            active = isLiked,
                            colorScheme = colorScheme,
                            shape = groupBottomStartShape,
                            onClick = onLikeClick,
                            modifier = Modifier.weight(1f)
                        )
                        UtilityAction(
                            icon = Icons.AutoMirrored.Rounded.Toc,
                            label = "Chapters",
                            enabled = !isChaptersBusy,
                            loading = isChaptersBusy,
                            subdued = !hasChapters && !isChaptersBusy,
                            colorScheme = colorScheme,
                            shape = groupMiddleShape,
                            onClick = onChaptersClick,
                            modifier = Modifier.weight(1f)
                        )
                        UtilityAction(
                            icon = Icons.Rounded.Description,
                            label = "Transcript",
                            active = isTranscriptVisible,
                            enabled = hasTranscript && !isTranscriptBusy,
                            loading = isTranscriptBusy,
                            subdued = !hasTranscript && !isTranscriptBusy,
                            colorScheme = colorScheme,
                            shape = groupMiddleShape,
                            onClick = onTranscriptClick,
                            modifier = Modifier.weight(1f)
                        )
                        UtilityAction(
                            icon = if (isPlayed) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
                            label = if (isPlayed) "Played" else "Complete",
                            active = isPlayed,
                            colorScheme = colorScheme,
                            shape = groupBottomEndShape,
                            onClick = onMarkPlayedClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                QuickControlMode.SPEED -> InlineSpeedSelector(
                    currentSpeed = playbackSpeed,
                    colorScheme = colorScheme,
                    topStartShape = groupTopStartShape,
                    topEndShape = groupTopEndShape,
                    middleShape = groupMiddleShape,
                    bottomStartShape = groupBottomStartShape,
                    bottomEndShape = groupBottomEndShape,
                    onBack = { controlMode = QuickControlMode.ACTIONS },
                    onSpeedSelected = onSpeedSelected
                )

                QuickControlMode.SLEEP -> InlineSleepSelector(
                    sleepTimerEnd = sleepTimerEnd,
                    sleepAtEndOfEpisode = sleepAtEndOfEpisode,
                    colorScheme = colorScheme,
                    topStartShape = groupTopStartShape,
                    topEndShape = groupTopEndShape,
                    middleShape = groupMiddleShape,
                    bottomStartShape = groupBottomStartShape,
                    bottomEndShape = groupBottomEndShape,
                    onBack = { controlMode = QuickControlMode.ACTIONS },
                    onDurationSelected = onSleepTimerSelected
                )
            }
        }
    }
}

@Composable
private fun InlineSpeedSelector(
    currentSpeed: Float,
    colorScheme: ColorScheme,
    topStartShape: androidx.compose.ui.graphics.Shape,
    topEndShape: androidx.compose.ui.graphics.Shape,
    middleShape: androidx.compose.ui.graphics.Shape,
    bottomStartShape: androidx.compose.ui.graphics.Shape,
    bottomEndShape: androidx.compose.ui.graphics.Shape,
    onBack: () -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = remember {
        listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    }
    val currentIndex = speeds.indices.minByOrNull { index ->
        kotlin.math.abs(speeds[index] - currentSpeed)
    } ?: speeds.indexOf(1f)
    val presets = listOf(0.8f, 1f, 1.5f, 2f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            UtilityAction(
                icon = Icons.Rounded.ArrowBack,
                label = "Back to quick actions",
                colorScheme = colorScheme,
                shape = topStartShape,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            UtilityAction(
                icon = Icons.Rounded.Remove,
                label = "Slower",
                enabled = currentIndex > 0,
                subdued = currentIndex <= 0,
                colorScheme = colorScheme,
                shape = middleShape,
                onClick = { onSpeedSelected(speeds[(currentIndex - 1).coerceAtLeast(0)]) },
                modifier = Modifier.weight(1f)
            )
            LabelAction(
                label = formatSpeedLabel(currentSpeed),
                active = true,
                enabled = false,
                colorScheme = colorScheme,
                shape = middleShape,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
            UtilityAction(
                icon = Icons.Rounded.Add,
                label = "Faster",
                enabled = currentIndex < speeds.lastIndex,
                subdued = currentIndex >= speeds.lastIndex,
                colorScheme = colorScheme,
                shape = topEndShape,
                onClick = { onSpeedSelected(speeds[(currentIndex + 1).coerceAtMost(speeds.lastIndex)]) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            presets.forEachIndexed { index, speed ->
                LabelAction(
                    label = formatSpeedLabel(speed),
                    active = kotlin.math.abs(currentSpeed - speed) < 0.001f,
                    colorScheme = colorScheme,
                    shape = when (index) {
                        0 -> bottomStartShape
                        presets.lastIndex -> bottomEndShape
                        else -> middleShape
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InlineSleepSelector(
    sleepTimerEnd: Long?,
    sleepAtEndOfEpisode: Boolean,
    colorScheme: ColorScheme,
    topStartShape: androidx.compose.ui.graphics.Shape,
    topEndShape: androidx.compose.ui.graphics.Shape,
    middleShape: androidx.compose.ui.graphics.Shape,
    bottomStartShape: androidx.compose.ui.graphics.Shape,
    bottomEndShape: androidx.compose.ui.graphics.Shape,
    onBack: () -> Unit,
    onDurationSelected: (Int) -> Unit
) {
    val timerActive = sleepAtEndOfEpisode ||
        (sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis())
    val remainingMinutes = sleepTimerEnd
        ?.let { kotlin.math.round((it - System.currentTimeMillis()) / 60_000.0).toInt() }
        ?.coerceAtLeast(0)
    val selectDurationAndBack: (Int) -> Unit = { minutes ->
        onDurationSelected(minutes)
        onBack()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            UtilityAction(
                icon = Icons.Rounded.ArrowBack,
                label = "Back to quick actions",
                colorScheme = colorScheme,
                shape = topStartShape,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            LabelAction(
                label = "Off",
                active = !timerActive,
                colorScheme = colorScheme,
                shape = middleShape,
                onClick = { selectDurationAndBack(0) },
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 15,
                label = "15m",
                active = !sleepAtEndOfEpisode && remainingMinutes == 15,
                colorScheme = colorScheme,
                shape = middleShape,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 30,
                label = "30m",
                active = !sleepAtEndOfEpisode && remainingMinutes == 30,
                colorScheme = colorScheme,
                shape = topEndShape,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SleepDurationAction(
                minutes = 45,
                label = "45m",
                active = !sleepAtEndOfEpisode && remainingMinutes == 45,
                colorScheme = colorScheme,
                shape = bottomStartShape,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 60,
                label = "1h",
                active = !sleepAtEndOfEpisode && remainingMinutes == 60,
                colorScheme = colorScheme,
                shape = middleShape,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 120,
                label = "2h",
                active = !sleepAtEndOfEpisode && remainingMinutes == 120,
                colorScheme = colorScheme,
                shape = middleShape,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = SleepTimerConstants.END_OF_EPISODE_MINUTES,
                label = "End",
                active = sleepAtEndOfEpisode,
                colorScheme = colorScheme,
                shape = bottomEndShape,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SleepDurationAction(
    minutes: Int,
    label: String,
    active: Boolean,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LabelAction(
        label = label,
        active = active,
        colorScheme = colorScheme,
        shape = shape,
        onClick = { onDurationSelected(minutes) },
        modifier = modifier
    )
}

@Composable
private fun LabelAction(
    label: String,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (active) colorScheme.primary else colorScheme.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "labelActionContainer"
    )
    val content by animateColorAsState(
        targetValue = if (active) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "labelActionContent"
    )
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(container)
            .quickActionClickable(enabled = enabled, shape = shape, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1
        )
    }
}

@Composable
private fun UtilityAction(
    icon: ImageVector,
    label: String,
    colorScheme: ColorScheme,
    active: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    subdued: Boolean = false,
    status: String? = null,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetContainer = if (active) colorScheme.primary else colorScheme.surfaceContainerHigh
    val targetContent = when {
        active -> colorScheme.onPrimary
        subdued -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> colorScheme.onSurfaceVariant
    }
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(180),
        label = "utilityActionContainer"
    )
    val content by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(180),
        label = "utilityActionContent"
    )
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(container)
            .quickActionClickable(enabled = enabled, shape = shape, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            BoxLoreLoader.CircularWavy(
                modifier = Modifier.size(24.dp),
                size = 24.dp,
                color = colorScheme.primary
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(if (status == null) 27.dp else 21.dp)
            )
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = content,
                    maxLines = 1
                )
            }
        }
    }
}

private fun Modifier.quickActionClickable(
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.snapTo(0.92f)
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    scale.animateTo(
                        targetValue = 1.035f,
                        animationSpec = spring(
                            dampingRatio = 0.62f,
                            stiffness = 420f
                        )
                    )
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 280f
                        )
                    )
                }
            }
        }
    }
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            clip = true
            this.shape = shape
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
private fun SleepAction(
    sleepTimerEnd: Long?,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remainingTime by remember { mutableStateOf("") }
    LaunchedEffect(sleepTimerEnd) {
        if (sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()) {
            while (isActive) {
                val left = sleepTimerEnd - System.currentTimeMillis()
                if (left <= 0) {
                    remainingTime = ""
                    break
                }
                remainingTime = formatTime(left)
                delay(1000)
            }
        } else {
            remainingTime = ""
        }
    }
    val active = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()
    UtilityAction(
        icon = Icons.Rounded.NightsStay,
        label = if (active && remainingTime.isNotEmpty()) remainingTime else "Sleep",
        active = active,
        status = remainingTime.takeIf { active && it.isNotEmpty() },
        colorScheme = colorScheme,
        shape = shape,
        onClick = onClick,
        modifier = modifier
    )
}

private fun formatSpeedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    currentSpeed: Float,
    colorScheme: ColorScheme,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Playback speed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            val speeds = listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(speeds) { speed ->
                    val selected = speed == currentSpeed
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh)
                            .clickable { onSpeedSelected(speed) }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatSpeedLabel(speed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(
    sleepTimerEnd: Long?,
    colorScheme: ColorScheme,
    onDurationSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timerActive = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            val options = listOf(
                0 to "Off",
                15 to "15 min",
                30 to "30 min",
                45 to "45 min",
                60 to "1 hr",
                120 to "2 hr",
                SleepTimerConstants.END_OF_EPISODE_MINUTES to "End of episode"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { (minutes, label) ->
                    val selected = minutes == 0 && !timerActive
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh)
                            .clickable { onDurationSelected(minutes) }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
