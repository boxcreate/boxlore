package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.contrastColor
import cx.aswin.boxlore.feature.info.R

internal data class PodcastEpisodeActionsState(
    val isPlaying: Boolean,
    val isResume: Boolean,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val isQueued: Boolean,
)

internal data class PodcastEpisodeActionsCallbacks(
    val onPlay: () -> Unit,
    val onLike: () -> Unit,
    val onDownload: () -> Unit,
    val onQueue: () -> Unit,
)

@Composable
internal fun PodcastEpisodeActions(state: PodcastEpisodeActionsState, callbacks: PodcastEpisodeActionsCallbacks, accentColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            PodcastEpisodeToggle(
                active = state.isLiked,
                icon = if (state.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = stringResource(if (state.isLiked) R.string.episode_info_unlike else R.string.episode_info_like),
                onClick = callbacks.onLike,
                leading = true,
            )
            PodcastEpisodeToggle(
                active = state.isDownloaded,
                icon = if (state.isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                label = stringResource(if (state.isDownloaded) R.string.episode_info_remove_download else R.string.episode_info_download),
                onClick = callbacks.onDownload,
                loading = state.isDownloading,
            )
            PodcastEpisodeToggle(
                active = state.isQueued,
                icon = if (state.isQueued) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = stringResource(if (state.isQueued) R.string.episode_info_remove_queue else R.string.episode_info_add_queue),
                onClick = callbacks.onQueue,
            )
        }
        PodcastEpisodePlayButton(state, callbacks.onPlay, accentColor, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PodcastEpisodeToggle(active: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, leading: Boolean = false, loading: Boolean = false) {
    val shapes = if (leading) {
        ButtonGroupDefaults.connectedLeadingButtonShapes(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 24.dp),
            pressedShape = RoundedCornerShape(18.dp),
        )
    } else {
        ButtonGroupDefaults.connectedMiddleButtonShapes(
            shape = RoundedCornerShape(8.dp),
            pressedShape = RoundedCornerShape(18.dp),
        )
    }
    val downloading = stringResource(R.string.episode_info_downloading)
    val debouncedClick = rememberPodcastEpisodeControlClick(onClick)
    TonalToggleButton(
        checked = active,
        onCheckedChange = { debouncedClick() },
        enabled = !loading,
        shapes = shapes,
        colors = ToggleButtonDefaults.tonalToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = label
            if (loading) stateDescription = downloading
        },
    ) {
        if (loading) {
            BoxLoreLoader.CircularWavy(size = 20.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(icon, null, Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PodcastEpisodePlayButton(state: PodcastEpisodeActionsState, onClick: () -> Unit, accentColor: Color, modifier: Modifier) {
    val label = stringResource(
        when {
            state.isPlaying -> R.string.episode_info_pause
            state.isResume -> R.string.episode_info_resume
            else -> R.string.episode_info_play
        },
    )
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold)
    val labelWidth = rememberTextMeasurer().measure(label, style = textStyle).size.width
    val density = LocalDensity.current
    BoxWithConstraints(modifier.height(48.dp)) {
        val showLabel = with(density) { labelWidth.toDp() + 44.dp <= maxWidth }
        Button(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp),
                pressedShape = RoundedCornerShape(18.dp),
            ),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = accentColor.contrastColor()),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                if (showLabel) Text(label, style = textStyle, maxLines = 1)
            }
        }
    }
}

/** Completion and unknown progress never reserve a strip of space in an episode card. */
internal fun podcastEpisodeCardProgress(isResume: Boolean, isCompleted: Boolean, progress: Float): Float? =
    progress.takeIf { isResume && !isCompleted && it.isFinite() && it > 0f }?.coerceAtMost(1f)

@Composable
internal fun PodcastEpisodeProgress(progress: Float, timeLeft: String?, accentColor: Color) {
    val animatedProgress by animateFloatAsState(progress, label = "podcast_episode_progress")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val maxTimeWidth = maxWidth * 0.45f
        Row(
            Modifier.fillMaxWidth().heightIn(min = 16.dp).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            if (!timeLeft.isNullOrBlank()) {
                Text(
                    timeLeft,
                    modifier = Modifier.widthIn(max = maxTimeWidth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Retains the existing list control safeguard when replacing AdaptiveControlButton. */
@Composable
internal fun rememberPodcastEpisodeControlClick(onClick: () -> Unit): () -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    return {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= 500L) {
            lastClickTime = currentTime
            onClick()
        }
    }
}
