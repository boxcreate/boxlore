package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.info.R

internal data class EpisodeActionRailState(
    val isPlaying: Boolean,
    val isPlaybackLoading: Boolean,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val isQueued: Boolean,
    val isCompleted: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

internal data class EpisodeActionRailCallbacks(
    val onMainActionClick: () -> Unit,
    val onLikeClick: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onQueueClick: () -> Unit,
)

@Composable
internal fun EpisodeActionRail(state: EpisodeActionRailState, callbacks: EpisodeActionRailCallbacks, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Independent actions form one connected row; Play has more room than the tonal toggles.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            EpisodeSecondaryActions(state, callbacks)
            EpisodePlayButton(state, callbacks.onMainActionClick, Modifier.weight(2.1f))
        }
        if (state.positionMs > 0 && state.durationMs > 0 && !state.isCompleted) EpisodeProgressSummary(state.positionMs, state.durationMs)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EpisodePlayButton(state: EpisodeActionRailState, onClick: () -> Unit, modifier: Modifier) {
    val playLabel = stringResource(
        when {
            state.isPlaybackLoading -> R.string.episode_info_loading
            state.isPlaying -> R.string.episode_info_pause
            state.isCompleted -> R.string.episode_info_play
            state.positionMs > 0 -> R.string.episode_info_resume
            else -> R.string.episode_info_play
        }
    )
    val textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold)
    val textWidth = rememberTextMeasurer().measure(playLabel, style = textStyle).size.width
    val density = LocalDensity.current
    BoxWithConstraints(modifier.height(56.dp)) {
        // On narrow screens or with larger text, retain a labelled icon rather than clipping a word.
        val showLabel = with(density) { textWidth.toDp() + 48.dp <= maxWidth }
        Button(
            onClick = onClick,
            enabled = !state.isPlaybackLoading,
            shapes = ButtonDefaults.shapes(
                shape = RoundedCornerShape(topStart = 9.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 9.dp),
                pressedShape = RoundedCornerShape(20.dp),
            ),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .semantics { contentDescription = playLabel },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.isPlaybackLoading) {
                        BoxLoreLoader.CircularWavy(size = 24.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(26.dp))
                    }
                    if (showLabel) Text(playLabel, style = textStyle, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EpisodeListeningProgress(positionMs: Long, durationMs: Long) {
    val progress by animateFloatAsState((positionMs.toFloat() / durationMs).coerceIn(0f, 1f), label = "episode_listening_progress")
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 4.dp,
        drawStopIndicator = {},
    )
}

@Composable
private fun RowScope.EpisodeSecondaryActions(state: EpisodeActionRailState, callbacks: EpisodeActionRailCallbacks) {
    RailAction(
        state.isLiked,
        if (state.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        stringResource(if (state.isLiked) R.string.episode_info_unlike else R.string.episode_info_like),
        callbacks.onLikeClick,
        modifier = Modifier.weight(1f),
        leading = true,
    )
    RailAction(
        state.isDownloaded,
        if (state.isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
        stringResource(if (state.isDownloaded) R.string.episode_info_remove_download else R.string.episode_info_download),
        callbacks.onDownloadClick,
        modifier = Modifier.weight(1f),
        loading = state.isDownloading,
    )
    RailAction(
        state.isQueued,
        if (state.isQueued) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
        stringResource(if (state.isQueued) R.string.episode_info_remove_queue else R.string.episode_info_add_queue),
        callbacks.onQueueClick,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun EpisodeProgressSummary(positionMs: Long, durationMs: Long) {
    val lessMinute = stringResource(R.string.episode_info_less_minute)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.episode_info_played_time, formatEpisodeDuration((positionMs / 1_000).toInt()).ifEmpty { lessMinute }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.episode_info_time_left, formatEpisodeDuration(((durationMs - positionMs).coerceAtLeast(0) / 1_000).toInt()).ifEmpty { lessMinute }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        EpisodeListeningProgress(positionMs, durationMs)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RailAction(active: Boolean, icon: ImageVector, actionLabel: String, onClick: () -> Unit, modifier: Modifier, leading: Boolean = false, loading: Boolean = false) {
    val downloading = stringResource(R.string.episode_info_downloading)
    val shapes = if (leading) {
        ButtonGroupDefaults.connectedLeadingButtonShapes(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 9.dp, bottomEnd = 9.dp, bottomStart = 28.dp),
            pressedShape = RoundedCornerShape(18.dp),
        )
    } else {
        ButtonGroupDefaults.connectedMiddleButtonShapes(
            shape = RoundedCornerShape(9.dp),
            pressedShape = RoundedCornerShape(18.dp),
        )
    }
    TonalToggleButton(
        checked = active,
        onCheckedChange = { onClick() },
        enabled = !loading,
        shapes = shapes,
        colors = ToggleButtonDefaults.tonalToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.height(56.dp).semantics {
            contentDescription = actionLabel
            if (loading) stateDescription = downloading
        },
    ) {
        if (loading) {
            BoxLoreLoader.CircularWavy(size = 22.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(icon, null, modifier = Modifier.size(23.dp))
        }
    }
}
