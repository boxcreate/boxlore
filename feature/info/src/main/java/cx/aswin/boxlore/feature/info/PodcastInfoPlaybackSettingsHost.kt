package cx.aswin.boxlore.feature.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cx.aswin.boxlore.feature.info.components.PodcastPlaybackSettingsActions
import cx.aswin.boxlore.feature.info.components.PodcastPlaybackSettingsSheet
import cx.aswin.boxlore.feature.info.components.PodcastPlaybackSettingsState

/** "Playback for this show": the show's own speed, trim silence and skips, over the app's. */
@Composable
internal fun PodcastInfoPlaybackSettingsHost(
    viewModel: PodcastInfoViewModel,
    state: PodcastInfoUiState.Success,
    onDismissRequest: () -> Unit,
) {
    // The player can change this show's speed and trim silence while the page stays open.
    LaunchedEffect(viewModel) { viewModel.refreshShowPlayback() }
    val globalSkipBeginningMs by viewModel.globalSkipBeginningMs.collectAsState()
    val globalSkipEndingMs by viewModel.globalSkipEndingMs.collectAsState()
    val globalPlaybackSpeed by viewModel.globalPlaybackSpeed.collectAsState()
    val globalTrimSilence by viewModel.globalTrimSilence.collectAsState()
    PodcastPlaybackSettingsSheet(
        state =
        PodcastPlaybackSettingsState(
            podcastTitle = state.podcast.title,
            isSubscribed = state.isSubscribed,
            globalSkipBeginningMs = globalSkipBeginningMs,
            globalSkipEndingMs = globalSkipEndingMs,
            skipBeginningOverrideMs = state.podcast.skipBeginningOverrideMs,
            skipEndingOverrideMs = state.podcast.skipEndingOverrideMs,
            globalPlaybackSpeed = globalPlaybackSpeed,
            playbackSpeedOverride = state.podcast.playbackSpeedOverride,
            globalTrimSilence = globalTrimSilence,
            trimSilenceOverride = state.podcast.skipSilenceOverride,
        ),
        actions =
        PodcastPlaybackSettingsActions(
            onUseAppDefaultsChange = viewModel::setUseAppPlaybackDefaults,
            onSkipBeginningOverrideChange = viewModel::setSkipBeginningOverride,
            onSkipEndingOverrideChange = viewModel::setSkipEndingOverride,
            onDismissRequest = onDismissRequest,
            onPlaybackSpeedOverrideChange = viewModel::setPlaybackSpeedOverride,
            onTrimSilenceOverrideChange = viewModel::setTrimSilenceOverride,
        ),
    )
}
