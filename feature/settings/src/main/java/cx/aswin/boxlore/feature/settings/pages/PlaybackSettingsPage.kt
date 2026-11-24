package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsChoiceRow
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsDurationSliderIcon
import cx.aswin.boxlore.feature.settings.components.SettingsDurationSliderRow
import cx.aswin.boxlore.feature.settings.components.SettingsDurationSliderValue
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsInfoTip
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow

/** Current values shown on [PlaybackSettingsPage]. Also used by [cx.aswin.boxlore.feature.settings.SettingsScreen]. */
data class PlaybackUiState(
    val skipBehavior: String,
    val skipBeginningMs: Long,
    val skipEndingMs: Long,
    val seekBackwardMs: Long,
    val seekForwardMs: Long,
    val hideCompletedInHome: Boolean,
    val hideCompletedInSubs: Boolean,
    val hideCompletedInShowDetails: Boolean,
    val restartForgottenEpisodes: Boolean = true,
    val sameShowQueueOnly: Boolean = false,
    val trimSilenceEnabled: Boolean = false,
)

/** Callbacks for [PlaybackSettingsPage], grouped to keep the page's parameter count small. */
data class PlaybackActions(
    val onSetSkipBehavior: (String) -> Unit,
    val onSetSkipBeginningMs: (Long) -> Unit,
    val onSetSkipEndingMs: (Long) -> Unit,
    val onSetSeekBackwardMs: (Long) -> Unit,
    val onSetSeekForwardMs: (Long) -> Unit,
    val onSetHideCompletedInHome: (Boolean) -> Unit,
    val onSetHideCompletedInSubs: (Boolean) -> Unit,
    val onSetHideCompletedInShowDetails: (Boolean) -> Unit,
    val onSetRestartForgottenEpisodes: (Boolean) -> Unit = {},
    val onSetSameShowQueueOnly: (Boolean) -> Unit = {},
    val onSetTrimSilenceEnabled: (Boolean) -> Unit = {},
)

@Composable
internal fun PlaybackSettingsPage(
    state: PlaybackUiState,
    actions: PlaybackActions,
    onBack: () -> Unit,
) {
    TrackScreenView("playback_settings")
    SettingsScaffold(
        title = stringResource(R.string.settings_page_playback),
        onBack = onBack,
    ) {
        SettingsGroup(
            title = stringResource(R.string.settings_playback_trimming_title),
        ) {
            SettingsDurationSliderRow(
                title = stringResource(R.string.settings_playback_skip_beginning),
                supportingText = stringResource(R.string.settings_playback_skip_beginning_description),
                value =
                SettingsDurationSliderValue(
                    seconds = (state.skipBeginningMs / 1_000L).toInt(),
                    range = 0..300,
                    stepSeconds = 5,
                ),
                onValueCommitted = { actions.onSetSkipBeginningMs(it * 1_000L) },
                icon = SettingsDurationSliderIcon(Icons.Rounded.FastForward),
            )
            SettingsDivider()
            SettingsDurationSliderRow(
                title = stringResource(R.string.settings_playback_skip_ending),
                supportingText = stringResource(R.string.settings_playback_skip_ending_description),
                value =
                SettingsDurationSliderValue(
                    seconds = (state.skipEndingMs / 1_000L).toInt(),
                    range = 0..300,
                    stepSeconds = 5,
                ),
                onValueCommitted = { actions.onSetSkipEndingMs(it * 1_000L) },
                icon = SettingsDurationSliderIcon(Icons.AutoMirrored.Rounded.LastPage),
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_playback_trim_silence),
                supportingText = stringResource(R.string.settings_playback_trim_silence_description),
                checked = state.trimSilenceEnabled,
                onCheckedChange = actions.onSetTrimSilenceEnabled,
                icon = Icons.Rounded.GraphicEq,
            )
        }
        SettingsInfoTip(
            text = stringResource(R.string.settings_playback_trimming_footer),
        )

        SettingsGroup(
            title = stringResource(R.string.settings_playback_seek_title),
            footer = stringResource(R.string.settings_playback_seek_footer),
        ) {
            SettingsDurationSliderRow(
                title = stringResource(R.string.settings_playback_seek_backward),
                value =
                SettingsDurationSliderValue(
                    seconds = (state.seekBackwardMs / 1_000L).toInt(),
                    range = 5..120,
                    stepSeconds = 5,
                ),
                onValueCommitted = { actions.onSetSeekBackwardMs(it * 1_000L) },
                icon = SettingsDurationSliderIcon(Icons.Rounded.Replay),
            )
            SettingsDivider()
            SettingsDurationSliderRow(
                title = stringResource(R.string.settings_playback_seek_forward),
                value =
                SettingsDurationSliderValue(
                    seconds = (state.seekForwardMs / 1_000L).toInt(),
                    range = 5..120,
                    stepSeconds = 5,
                ),
                onValueCommitted = { actions.onSetSeekForwardMs(it * 1_000L) },
                icon =
                SettingsDurationSliderIcon(
                    image = Icons.Rounded.Replay,
                    mirrored = true,
                ),
            )
        }

        SettingsGroup(
            modifier = Modifier.selectableGroup(),
            title = stringResource(R.string.settings_playback_skip_title),
            footer = stringResource(R.string.settings_playback_skip_footer),
        ) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_playback_skip_only),
                supportingText = stringResource(R.string.settings_playback_skip_only_description),
                selected = state.skipBehavior == "just_skip",
                onClick = { actions.onSetSkipBehavior("just_skip") },
            )
            SettingsDivider()
            SettingsChoiceRow(
                title = stringResource(R.string.settings_playback_mark_complete),
                supportingText = stringResource(R.string.settings_playback_mark_complete_description),
                selected = state.skipBehavior == "mark_completed_skip",
                onClick = { actions.onSetSkipBehavior("mark_completed_skip") },
            )
        }

        SettingsGroup(
            title = stringResource(R.string.settings_playback_resume_title),
            footer =
            stringResource(R.string.settings_playback_resume_footer),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_playback_restart_forgotten),
                supportingText = stringResource(R.string.settings_playback_restart_forgotten_description),
                checked = state.restartForgottenEpisodes,
                onCheckedChange = actions.onSetRestartForgottenEpisodes,
                icon = Icons.Rounded.Update,
            )
        }

        SettingsGroup(
            title = stringResource(R.string.settings_playback_queue_title),
            footer =
            stringResource(R.string.settings_playback_queue_footer),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_playback_smart_queue),
                supportingText = stringResource(R.string.settings_playback_smart_queue_description),
                checked = !state.sameShowQueueOnly,
                onCheckedChange = { enabled -> actions.onSetSameShowQueueOnly(!enabled) },
                icon = Icons.Rounded.QueueMusic,
            )
        }

        SettingsGroup(
            title = stringResource(R.string.settings_playback_hide_completed_title),
            footer = stringResource(R.string.settings_playback_hide_completed_footer),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_playback_hide_home),
                supportingText = stringResource(R.string.settings_playback_hide_home_description),
                checked = state.hideCompletedInHome,
                onCheckedChange = actions.onSetHideCompletedInHome,
                icon = Icons.Rounded.Home,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_playback_hide_latest),
                supportingText = stringResource(R.string.settings_playback_hide_latest_description),
                checked = state.hideCompletedInSubs,
                onCheckedChange = actions.onSetHideCompletedInSubs,
                icon = Icons.Rounded.NewReleases,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_playback_hide_podcast_pages),
                supportingText = stringResource(R.string.settings_playback_hide_podcast_pages_description),
                checked = state.hideCompletedInShowDetails,
                onCheckedChange = actions.onSetHideCompletedInShowDetails,
                icon = Icons.Rounded.Podcasts,
            )
        }
    }
}
