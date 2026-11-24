package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.model.SleepTimerConstants
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.formatTime
import cx.aswin.boxlore.feature.player.v2.logic.downloadLabel
import cx.aswin.boxlore.feature.player.v2.logic.formatSpeedLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun QuickActionsGrid(
    playback: SecondaryPlaybackState,
    availability: SecondaryAvailabilityState,
    library: SecondaryLibraryState,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    navigation: QuickControlNavigation,
    clickActions: SecondaryClickActions
) {
    val chaptersBusy = availability.isChaptersLoading ||
        availability.autoChaptersState == AutoTranscriptState.GENERATING
    val transcriptBusy = availability.autoTranscriptState == AutoTranscriptState.GENERATING
    Column {
        QuickActionsTopRow(playback, library, colorScheme, shapes, navigation, clickActions)
        Spacer(Modifier.height(3.dp))
        QuickActionsBottomRow(
            availability,
            library,
            colorScheme,
            shapes,
            clickActions,
            chaptersBusy,
            transcriptBusy
        )
    }
}

@Composable
internal fun QuickActionsTopRow(
    playback: SecondaryPlaybackState,
    library: SecondaryLibraryState,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    navigation: QuickControlNavigation,
    clickActions: SecondaryClickActions
) {
    val speedIsCustom = speedChipIsActive(playback.playbackSpeed, playback.audio)
    val trimHint = playback.audio.trimSilence && !playback.audio.isCasting
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        UtilityAction(
            icon = Icons.Outlined.Speed,
            label = if (trimHint) {
                stringResource(R.string.player_speed_trim_on, formatSpeedLabel(playback.playbackSpeed))
            } else {
                formatSpeedLabel(playback.playbackSpeed)
            },
            state = UtilityActionState(
                active = speedIsCustom,
                status = formatSpeedLabel(playback.playbackSpeed).takeIf { speedIsCustom }
            ),
            colorScheme = colorScheme,
            shape = shapes.topStart,
            onClick = navigation.onSpeedClick,
            modifier = Modifier.weight(1f)
        )
        SleepAction(
            sleepTimerEnd = playback.sleepTimerEnd,
            sleepAtEndOfEpisode = playback.sleepAtEndOfEpisode,
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = navigation.onSleepClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = if (library.isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
            label = stringResource(downloadLabel(library.isDownloaded, library.isDownloading)),
            state = UtilityActionState(
                active = library.isDownloaded,
                loading = library.isDownloading
            ),
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = clickActions.onDownloadClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
            label = stringResource(R.string.player_queue),
            colorScheme = colorScheme,
            shape = shapes.topEnd,
            onClick = clickActions.onQueueClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun QuickActionsBottomRow(
    availability: SecondaryAvailabilityState,
    library: SecondaryLibraryState,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    clickActions: SecondaryClickActions,
    chaptersBusy: Boolean,
    transcriptBusy: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        UtilityAction(
            icon = if (library.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = stringResource(if (library.isLiked) R.string.player_liked else R.string.player_like),
            state = UtilityActionState(active = library.isLiked),
            colorScheme = colorScheme,
            shape = shapes.bottomStart,
            onClick = clickActions.onLikeClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            label = stringResource(R.string.player_chapters),
            state = UtilityActionState(
                enabled = !chaptersBusy,
                loading = chaptersBusy,
                subdued = !availability.hasChapters && !chaptersBusy
            ),
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = clickActions.onChaptersClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = Icons.Outlined.Subtitles,
            label = stringResource(R.string.player_transcript),
            state = UtilityActionState(
                active = availability.isTranscriptVisible,
                enabled = availability.hasTranscript && !transcriptBusy,
                loading = transcriptBusy,
                subdued = !availability.hasTranscript && !transcriptBusy
            ),
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = clickActions.onTranscriptClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = if (library.isPlayed) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
            label = stringResource(if (library.isPlayed) R.string.player_played else R.string.player_complete),
            state = UtilityActionState(active = library.isPlayed),
            colorScheme = colorScheme,
            shape = shapes.bottomEnd,
            onClick = clickActions.onMarkPlayedClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun InlineSpeedSelector(
    currentSpeed: Float,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    onBack: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    audio: InlineSpeedAudio = InlineSpeedAudio()
) {
    TrackScreenView("player_speed")
    val speeds = cx.aswin.boxlore.core.playback.ShowPlaybackPolicy.SPEED_OPTIONS
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
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = stringResource(R.string.player_back_to_quick_actions),
                colorScheme = colorScheme,
                shape = shapes.topStart,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            UtilityAction(
                icon = Icons.Rounded.Remove,
                label = stringResource(R.string.player_slower),
                state = UtilityActionState(
                    enabled = currentIndex > 0,
                    subdued = currentIndex <= 0
                ),
                colorScheme = colorScheme,
                shape = shapes.middle,
                onClick = { onSpeedSelected(speeds[(currentIndex - 1).coerceAtLeast(0)]) },
                modifier = Modifier.weight(1f)
            )
            CurrentSpeedLabel(
                label = formatSpeedLabel(currentSpeed),
                colorScheme = colorScheme,
                shape = shapes.middle,
                modifier = Modifier.weight(1f)
            )
            UtilityAction(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.player_faster),
                state = UtilityActionState(
                    enabled = currentIndex < speeds.lastIndex,
                    subdued = currentIndex >= speeds.lastIndex
                ),
                colorScheme = colorScheme,
                shape = shapes.topEnd,
                onClick = { onSpeedSelected(speeds[(currentIndex + 1).coerceAtMost(speeds.lastIndex)]) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            presets.forEach { speed ->
                LabelAction(
                    label = formatSpeedLabel(speed),
                    active = kotlin.math.abs(currentSpeed - speed) < 0.001f,
                    selected = kotlin.math.abs(currentSpeed - speed) < 0.001f,
                    colorScheme = colorScheme,
                    shape = shapes.middle,
                    onClick = {
                        onSpeedSelected(speed)
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        InlineSpeedAudioRow(
            audio = audio.state,
            colorScheme = colorScheme,
            shapes = InlineSpeedAudioShapes(start = shapes.bottomStart, end = shapes.bottomEnd, full = InlineSpeedAudioBottomShape),
            onUseAppSpeed = audio.onUseAppSpeed,
            onTrimSilenceChange = audio.onTrimSilenceChange
        )
    }
}

@Composable
internal fun InlineSleepSelector(
    sleepTimerEnd: Long?,
    sleepAtEndOfEpisode: Boolean,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    onBack: () -> Unit,
    onDurationSelected: (Int) -> Unit,
    presetMinutes: Int? = null
) {
    TrackScreenView("player_sleep_timer")
    val timerActive = sleepAtEndOfEpisode ||
        (sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis())
    // The length the listener picked stays selected while it counts down; no guessing from what is left.
    val remainingMinutes = presetMinutes.takeIf { timerActive && !sleepAtEndOfEpisode }
    // The selected length shows how long is left under its name.
    val countdown = rememberSleepCountdown(sleepTimerEnd).takeIf { it.isNotEmpty() && remainingMinutes != null }
    val selectDurationAndBack: (Int) -> Unit = { minutes ->
        onDurationSelected(minutes)
        onBack()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            UtilityAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = stringResource(R.string.player_back_to_quick_actions),
                colorScheme = colorScheme,
                shape = shapes.topStart,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            LabelAction(
                label = stringResource(R.string.player_off),
                active = !timerActive,
                selected = !timerActive,
                colorScheme = colorScheme,
                shape = shapes.middle,
                onClick = { selectDurationAndBack(0) },
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 15,
                label = sleepChipLabel(15),
                active = !sleepAtEndOfEpisode && remainingMinutes == 15,
                status = countdown.takeIf { remainingMinutes == 15 },
                colorScheme = colorScheme,
                shape = shapes.middle,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 30,
                label = sleepChipLabel(30),
                active = !sleepAtEndOfEpisode && remainingMinutes == 30,
                status = countdown.takeIf { remainingMinutes == 30 },
                colorScheme = colorScheme,
                shape = shapes.topEnd,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(45 to shapes.bottomStart, 60 to shapes.middle, 120 to shapes.middle).forEach { (minutes, shape) ->
                SleepDurationAction(
                    minutes = minutes,
                    label = sleepChipLabel(minutes),
                    active = remainingMinutes == minutes,
                    status = countdown.takeIf { remainingMinutes == minutes },
                    colorScheme = colorScheme,
                    shape = shape,
                    onDurationSelected = selectDurationAndBack,
                    modifier = Modifier.weight(1f)
                )
            }
            SleepDurationAction(
                minutes = SleepTimerConstants.END_OF_EPISODE_MINUTES,
                label = stringResource(R.string.player_sleep_end),
                active = sleepAtEndOfEpisode,
                colorScheme = colorScheme,
                shape = shapes.bottomEnd,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
internal fun SleepDurationAction(
    minutes: Int,
    label: String,
    active: Boolean,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null
) {
    LabelAction(
        label = label,
        active = active,
        selected = active,
        status = status,
        colorScheme = colorScheme,
        shape = shape,
        onClick = { onDurationSelected(minutes) },
        modifier = modifier
    )
}

@Composable
@Suppress("LongParameterList")
internal fun LabelAction(
    label: String,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set for one choice among several (a speed or sleep length): announced as selected or not. */
    selected: Boolean? = null,
    /** A second line under the label, such as the time left on the selected sleep length. */
    status: String? = null
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
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold)
    Box(
        modifier = modifier
            .height(56.dp)
            .quickActionPress(enabled = true, shape = shape, onClick = onClick)
            .then(if (selected != null) Modifier.choiceSemantics(selected) else Modifier)
            .clip(shape)
            .background(container)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Long names in some languages shrink a little, and end with "…" only as a last resort.
            Text(
                text = label,
                style = labelStyle,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = labelStyle.fontSize)
            )
            if (status != null) {
                val statusStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.bold)
                Text(
                    text = status,
                    style = statusStyle,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = statusStyle.fontSize)
                )
            }
        }
    }
}

/** The current speed between Slower and Faster: a value that screen readers announce as it changes, not a button. */
@Composable
internal fun CurrentSpeedLabel(
    label: String,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.player_playback_speed)
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(colorScheme.primary)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = label
                liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
            color = colorScheme.onPrimary,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

internal data class UtilityActionState(
    val active: Boolean = false,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val subdued: Boolean = false,
    val status: String? = null
)

@Composable
internal fun UtilityAction(
    icon: ImageVector,
    label: String,
    colorScheme: ColorScheme,
    state: UtilityActionState = UtilityActionState(),
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetContainer = if (state.active) colorScheme.primary else colorScheme.surfaceContainerHigh
    val targetContent = when {
        state.active -> colorScheme.onPrimary
        state.subdued -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
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
            .quickActionPress(enabled = state.enabled, shape = shape, onClick = onClick)
            .clip(shape)
            .background(container)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.loading) {
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
                modifier = Modifier.size(if (state.status == null) 27.dp else 21.dp)
            )
            if (state.status != null) {
                val statusStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.bold)
                Text(
                    text = state.status,
                    style = statusStyle,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = statusStyle.fontSize)
                )
            }
        }
    }
}

/**
 * A tray tile's press: the whole tile (its background comes after this in the chain) presses with the
 * shared control feedback, with hover and keyboard focus on tablets like other boxlore controls.
 */
internal fun Modifier.quickActionPress(
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
): Modifier = expressiveClickable(enabled = enabled, shape = shape, feedback = ExpressivePressFeedback.Control, onClick = onClick)

@Composable
internal fun SleepAction(
    sleepTimerEnd: Long?,
    sleepAtEndOfEpisode: Boolean,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingTime = rememberSleepCountdown(sleepTimerEnd)
    val hasCountdown = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()
    val active = hasCountdown || sleepAtEndOfEpisode
    val status = when {
        sleepAtEndOfEpisode -> stringResource(R.string.player_sleep_end)
        hasCountdown && remainingTime.isNotEmpty() -> remainingTime
        else -> null
    }
    UtilityAction(
        icon = Icons.Outlined.Bedtime,
        label = stringResource(R.string.player_sleep),
        state = UtilityActionState(active = active, status = status),
        colorScheme = colorScheme,
        shape = shape,
        onClick = onClick,
        modifier = modifier
    )
}

/** The time left on a running sleep timer, such as "24:59", ticking every second; empty when none runs. */
@Composable
private fun rememberSleepCountdown(sleepTimerEnd: Long?): String {
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
    return remainingTime
}

/** A sleep chip's short length, such as "15m" or "1h", in the screen's language. */
@Composable
private fun sleepChipLabel(minutes: Int): String =
    LocaleFormats.duration(minutes * 60L, currentLocale(), LocaleFormats.DurationWidth.NARROW)

/** One choice among several: announced as a radio button that is selected or not. */
private fun Modifier.choiceSemantics(isSelected: Boolean): Modifier = semantics {
    selected = isSelected
    role = Role.RadioButton
}
