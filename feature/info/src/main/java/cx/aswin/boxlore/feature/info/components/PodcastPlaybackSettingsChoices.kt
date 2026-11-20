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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.playback.ShowPlaybackPolicy

/** "1.2× · Beginning 30s · Ending Off", plus "· Trim silence on" when the app trims silence. */
internal fun appDefaultsSummary(state: PodcastPlaybackSettingsState): String = buildString {
    append(ShowPlaybackPolicy.speedLabel(state.globalPlaybackSpeed))
    append(" · Beginning ${durationLabel(state.globalSkipBeginningMs)}")
    append(" · Ending ${durationLabel(state.globalSkipEndingMs)}")
    if (state.globalTrimSilence) append(" · Trim silence on")
}

/** The label for the "use the app's value" choice, e.g. "App speed (1.2×)" or "App setting (off)". */
internal fun appSpeedChoiceLabel(appSpeed: Float): String = "App speed (${ShowPlaybackPolicy.speedLabel(appSpeed)})"

internal fun appTrimSilenceChoiceLabel(appTrimSilence: Boolean): String = "App setting (${if (appTrimSilence) "on" else "off"})"

/** The value shown beside a choice's title: the show's own value, or "App" while it follows the app. */
internal fun speedChoiceValue(state: PodcastPlaybackSettingsState): String = state.playbackSpeedOverride
    ?.let(ShowPlaybackPolicy::speedLabel)
    ?: "App"

@Composable
internal fun PodcastSpeedChoice(
    state: PodcastPlaybackSettingsState,
    enabled: Boolean,
    onChange: (Float?) -> Unit,
) {
    val selected = state.playbackSpeedOverride
    ChoiceGroup(title = "Speed", value = speedChoiceValue(state)) {
        ChoiceChip(appSpeedChoiceLabel(state.globalPlaybackSpeed), selected == null, enabled) { onChange(null) }
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
            title = "Trim silence",
            value = when (selected) {
                null -> "App"
                true -> "On"
                false -> "Off"
            },
        ) {
            ChoiceChip(appTrimSilenceChoiceLabel(state.globalTrimSilence), selected == null, enabled) { onChange(null) }
            ChoiceChip("On", selected == true, enabled) { onChange(true) }
            ChoiceChip("Off", selected == false, enabled) { onChange(false) }
        }
        Text(
            text = "Skip long pauses in this show’s episodes",
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
