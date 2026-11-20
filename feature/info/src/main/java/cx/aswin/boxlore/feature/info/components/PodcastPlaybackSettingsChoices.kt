package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.playback.ShowPlaybackPolicy
import cx.aswin.boxlore.feature.info.R

/** The parts of "1.2× · Beginning 30s · Ending Off", plus "Trim silence on" when the app trims silence; shown joined by " · ". */
internal fun appDefaultsSummary(state: PodcastPlaybackSettingsState): List<UiText> = listOfNotNull(
    UiText.raw(ShowPlaybackPolicy.speedLabel(state.globalPlaybackSpeed)),
    UiText.res(R.string.podcast_playback_summary_beginning, durationLabel(state.globalSkipBeginningMs)),
    UiText.res(R.string.podcast_playback_summary_ending, durationLabel(state.globalSkipEndingMs)),
    UiText.res(R.string.podcast_playback_summary_trim_silence).takeIf { state.globalTrimSilence },
)

/** The label for the "use the app's value" choice, e.g. "App speed (1.2×)" or "App setting (off)". */
internal fun appSpeedChoiceLabel(appSpeed: Float): UiText = UiText.res(R.string.podcast_playback_app_speed, ShowPlaybackPolicy.speedLabel(appSpeed))

internal fun appTrimSilenceChoiceLabel(appTrimSilence: Boolean): UiText =
    UiText.res(if (appTrimSilence) R.string.podcast_playback_app_setting_on else R.string.podcast_playback_app_setting_off)

/** The value shown beside a choice's title: the show's own value, or "App" while it follows the app. */
internal fun speedChoiceValue(state: PodcastPlaybackSettingsState): UiText = state.playbackSpeedOverride
    ?.let { UiText.raw(ShowPlaybackPolicy.speedLabel(it)) }
    ?: UiText.res(R.string.podcast_playback_app)

@Composable
internal fun PodcastSpeedChoice(
    state: PodcastPlaybackSettingsState,
    enabled: Boolean,
    onChange: (Float?) -> Unit,
) {
    val selected = state.playbackSpeedOverride
    ChoiceGroup(title = stringResource(R.string.podcast_playback_speed), value = speedChoiceValue(state).asString()) {
        ChoiceChip(appSpeedChoiceLabel(state.globalPlaybackSpeed).asString(), selected == null, enabled) { onChange(null) }
        ShowPlaybackPolicy.SPEED_OPTIONS.forEach { speed ->
            ChoiceChip(ShowPlaybackPolicy.speedLabel(speed), selected == speed, enabled) { onChange(speed) }
        }
    }
}

@Composable
internal fun PodcastTrimSilenceChoice(
    state: PodcastPlaybackSettingsState,
    enabled: Boolean,
    onChange: (Boolean?) -> Unit,
) {
    val selected = state.trimSilenceOverride
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChoiceGroup(
            title = stringResource(R.string.podcast_playback_trim_silence),
            value = stringResource(
                when (selected) {
                    null -> R.string.podcast_playback_app
                    true -> R.string.podcast_playback_on
                    false -> R.string.podcast_playback_off
                },
            ),
        ) {
            ChoiceChip(appTrimSilenceChoiceLabel(state.globalTrimSilence).asString(), selected == null, enabled) { onChange(null) }
            ChoiceChip(stringResource(R.string.podcast_playback_on), selected == true, enabled) { onChange(true) }
            ChoiceChip(stringResource(R.string.podcast_playback_off), selected == false, enabled) { onChange(false) }
        }
        Text(
            text = stringResource(R.string.podcast_playback_trim_silence_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceGroup(title: String, value: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { chips() }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { if (!selected) onClick() },
        label = { Text(label) },
        enabled = enabled,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp).semantics { role = Role.RadioButton },
    )
}
