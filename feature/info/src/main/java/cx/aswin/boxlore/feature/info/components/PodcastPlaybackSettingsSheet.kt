package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.info.R
import kotlin.math.roundToInt

data class PodcastPlaybackSettingsState(
    val podcastTitle: String,
    val isSubscribed: Boolean,
    val globalSkipBeginningMs: Long,
    val globalSkipEndingMs: Long,
    val skipBeginningOverrideMs: Long?,
    val skipEndingOverrideMs: Long?,
    val globalPlaybackSpeed: Float = 1f,
    val playbackSpeedOverride: Float? = null,
    val globalTrimSilence: Boolean = false,
    val trimSilenceOverride: Boolean? = null,
) {
    /** "Use app defaults" is on while the show keeps none of its own settings. */
    val usesDefaults: Boolean
        get() = listOf(skipBeginningOverrideMs, skipEndingOverrideMs, playbackSpeedOverride, trimSilenceOverride).all { it == null }
}

data class PodcastPlaybackSettingsActions(
    val onUseAppDefaultsChange: (Boolean) -> Unit,
    val onSkipBeginningOverrideChange: (Long) -> Unit,
    val onSkipEndingOverrideChange: (Long) -> Unit,
    val onDismissRequest: () -> Unit,
    val onPlaybackSpeedOverrideChange: (Float?) -> Unit = {},
    val onTrimSilenceOverrideChange: (Boolean?) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastPlaybackSettingsSheet(
    state: PodcastPlaybackSettingsState,
    actions: PodcastPlaybackSettingsActions,
) {
    TrackScreenView("podcast_playback_settings")
    val usesDefaults = state.usesDefaults
    ModalBottomSheet(onDismissRequest = actions.onDismissRequest) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                // Scrolls on phones too: with speed and trim silence it can outgrow a short screen.
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.podcast_info_playback_for_show),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
            )
            Text(
                text = state.podcastTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The whole row is the switch, in its own rounded panel.
            SettingsPanel {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(
                            value = usesDefaults,
                            enabled = state.isSubscribed,
                            role = Role.Switch,
                            onValueChange = actions.onUseAppDefaultsChange,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag(USE_APP_DEFAULTS_ROW_TAG),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.podcast_playback_use_app_defaults), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text =
                            if (state.isSubscribed) {
                                appDefaultsSummary(state).map { it.asString() }.joinToString(" · ")
                            } else {
                                stringResource(R.string.podcast_playback_subscribe_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The row handles the tap, so the switch never toggles twice.
                    Switch(checked = usesDefaults, enabled = state.isSubscribed, onCheckedChange = null)
                }
            }
            SettingsPanel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Each has its own "App" choice, so picking one never freezes the show's skips.
                    PodcastSpeedChoice(
                        state = state,
                        enabled = state.isSubscribed,
                        onChange = actions.onPlaybackSpeedOverrideChange,
                    )
                    PodcastTrimSilenceChoice(
                        state = state,
                        enabled = state.isSubscribed,
                        onChange = actions.onTrimSilenceOverrideChange,
                    )
                    PodcastDurationSlider(
                        title = stringResource(R.string.podcast_playback_skip_beginning),
                        valueMs = state.skipBeginningOverrideMs ?: state.globalSkipBeginningMs,
                        enabled = state.isSubscribed && !usesDefaults,
                        onValueCommitted = actions.onSkipBeginningOverrideChange,
                    )
                    PodcastDurationSlider(
                        title = stringResource(R.string.podcast_playback_skip_ending),
                        valueMs = state.skipEndingOverrideMs ?: state.globalSkipEndingMs,
                        enabled = state.isSubscribed && !usesDefaults,
                        onValueCommitted = actions.onSkipEndingOverrideChange,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.podcast_playback_seek_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One of the sheet's solid rounded panels. */
@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) { content() }
}

internal const val USE_APP_DEFAULTS_ROW_TAG = "podcast_playback_use_app_defaults"

@Composable
private fun PodcastDurationSlider(
    title: String,
    valueMs: Long,
    enabled: Boolean,
    onValueCommitted: (Long) -> Unit,
) {
    val valueSeconds = (valueMs / 1_000L).toInt().coerceIn(0, 300)
    var pendingValue by remember(valueSeconds) { mutableFloatStateOf(valueSeconds.toFloat()) }
    val snappedSeconds = ((pendingValue / 5f).roundToInt() * 5).coerceIn(0, 300)
    val label = if (snappedSeconds == 0) stringResource(R.string.podcast_playback_off) else pluralStringResource(R.plurals.podcast_playback_seconds, snappedSeconds, snappedSeconds)

    val sliderDescription = stringResource(R.string.podcast_playback_slider_description, title, label)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = pendingValue,
            onValueChange = { pendingValue = it },
            onValueChangeFinished = { onValueCommitted(snappedSeconds * 1_000L) },
            enabled = enabled,
            valueRange = 0f..300f,
            steps = 0,
            modifier =
            Modifier.semantics {
                contentDescription = sliderDescription
            },
        )
    }
}

/** A skip's length as the summary line types it: "Off" or "30s". */
internal fun durationLabel(valueMs: Long): UiText = if (valueMs <= 0L) {
    UiText.res(R.string.podcast_playback_off)
} else {
    (valueMs / 1_000L).toInt().let { UiText.plural(R.plurals.podcast_playback_seconds_short, it, it) }
}
