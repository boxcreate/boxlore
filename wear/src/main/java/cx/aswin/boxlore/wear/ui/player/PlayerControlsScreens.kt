package cx.aswin.boxlore.wear.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.model.PlaybackSpeeds
import cx.aswin.boxlore.wear.model.SleepTimerChoice
import cx.aswin.boxlore.wear.ui.components.IconDisc
import cx.aswin.boxlore.wear.ui.components.ScreenFit
import cx.aswin.boxlore.wear.ui.components.WatchRow
import cx.aswin.boxlore.wear.ui.components.rememberScreenScale

/** The list behind the player's three-dot button. */
@Composable
internal fun PlayerMoreScreen(
    nowPlaying: NowPlaying,
    onSpeed: () -> Unit,
    onSleepTimer: () -> Unit,
    onSwitchPlace: (() -> Unit)?,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val onPhone = nowPlaying.place == PlaybackPlace.Phone
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(text = nowPlaying.episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            item {
                WatchRow(
                    title = stringResource(R.string.wear_playback_speed),
                    onClick = onSpeed,
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    leading = { IconDisc(Icons.Rounded.Speed) },
                    trailing = { ValueText(speedLabel(nowPlaying.speed)) },
                )
            }
            item {
                WatchRow(
                    title = stringResource(R.string.wear_sleep_timer),
                    onClick = onSleepTimer,
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    leading = { IconDisc(Icons.Rounded.Bedtime) },
                    trailing = { ValueText(sleepLabel(nowPlaying.sleepTimer)) },
                )
            }
            if (onSwitchPlace != null) {
                item {
                    WatchRow(
                        title = stringResource(if (onPhone) R.string.wear_play_on_watch else R.string.wear_play_on_phone),
                        onClick = onSwitchPlace,
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        leading = { IconDisc(if (onPhone) Icons.Rounded.Watch else Icons.Rounded.Smartphone) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueText(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun speedLabel(speed: Float): String = stringResource(R.string.wear_speed_value, PlaybackSpeeds.label(speed))

@Composable
internal fun sleepLabel(choice: SleepTimerChoice): String =
    when (choice) {
        SleepTimerChoice.Off -> stringResource(R.string.wear_sleep_off)
        SleepTimerChoice.EndOfEpisode -> stringResource(R.string.wear_sleep_end_of_episode)
        SleepTimerChoice.Hour1 -> stringResource(R.string.wear_sleep_hours, 1)
        SleepTimerChoice.Hours2 -> stringResource(R.string.wear_sleep_hours, 2)
        else -> stringResource(R.string.wear_sleep_minutes, choice.minutes ?: 0)
    }

private val TimerRows =
    listOf(
        listOf(SleepTimerChoice.Minutes15, SleepTimerChoice.Minutes30, SleepTimerChoice.Minutes45),
        listOf(SleepTimerChoice.Hour1, SleepTimerChoice.Hours2, SleepTimerChoice.Off),
    )

/** The phone player's sleep timer choices; the picked one is filled and squarer. */
@Composable
internal fun SleepTimerScreen(selected: SleepTimerChoice, onSelect: (SleepTimerChoice) -> Unit) {
    val endOfEpisode = selected == SleepTimerChoice.EndOfEpisode
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = { onSelect(SleepTimerChoice.EndOfEpisode) },
                colors = if (endOfEpisode) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(stringResource(R.string.wear_sleep_end_of_episode), textAlign = TextAlign.Center)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.wear_sleep_timer)) }
            }
            TimerRows.forEach { row ->
                item {
                    // On a small watch the outer buttons would reach past the curve of the screen.
                    val inset = if (ScreenFit.isCompact(rememberScreenScale())) 12.dp else 0.dp
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = inset), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { choice -> TimerChoiceButton(choice, choice == selected, onSelect, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerChoiceButton(
    choice: SleepTimerChoice,
    picked: Boolean,
    onSelect: (SleepTimerChoice) -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = { onSelect(choice) },
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(if (picked) 14.dp else 23.dp),
        colors = if (picked) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = sleepLabel(choice), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

/** Steps through the phone player's speeds. */
@Composable
internal fun SpeedScreen(speed: Float, onSpeed: (Float) -> Unit) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = stringResource(R.string.wear_playback_speed), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { onSpeed(PlaybackSpeeds.step(speed, up = false)) },
                    enabled = speed > PlaybackSpeeds.all.first(),
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.wear_speed_slower))
                }
                Text(
                    text = speedLabel(speed),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.size(width = 72.dp, height = 40.dp),
                )
                FilledTonalIconButton(
                    onClick = { onSpeed(PlaybackSpeeds.step(speed, up = true)) },
                    enabled = speed < PlaybackSpeeds.all.last(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.wear_speed_faster))
                }
            }
        }
    }
}
