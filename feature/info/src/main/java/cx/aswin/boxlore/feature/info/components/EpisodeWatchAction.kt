package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.RemoveDownloadConfirmationDialog
import cx.aswin.boxlore.core.designsystem.components.SendToWatchIcon
import cx.aswin.boxlore.core.designsystem.components.WatchEpisodeActions
import cx.aswin.boxlore.core.designsystem.components.WatchEpisodeCopy
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.R
import kotlinx.coroutines.delay

/** How the episode page's header makes room for its watch button. */
internal object EpisodeWatchHeader {
    /** One header button with its gap. */
    val ButtonSlot: Dp = 48.dp

    /**
     * The extra room the title leaves at its end when the watch button sits beside Share, so a
     * long episode name stops before the button instead of running under it.
     */
    fun titleEndExtra(hasWatchButton: Boolean): Dp = if (hasWatchButton) ButtonSlot else 0.dp

    /** What a tap does, and what the page says once the watch has, or has not, been told. */
    fun messageAfter(copy: WatchEpisodeCopy, handedOver: Boolean): Int =
        when {
            !handedOver -> R.string.episode_info_watch_out_of_reach
            copy == WatchEpisodeCopy.None -> R.string.episode_info_watch_sending
            else -> R.string.episode_info_watch_removed
        }

    /** Sending is one tap; taking an episode off the watch, or stopping its download, is asked first. */
    fun asksFirst(copy: WatchEpisodeCopy): Boolean = copy != WatchEpisodeCopy.None

    fun label(copy: WatchEpisodeCopy): Int =
        when (copy) {
            WatchEpisodeCopy.None -> R.string.episode_info_send_to_watch
            WatchEpisodeCopy.Downloading -> R.string.episode_info_downloading_on_watch
            WatchEpisodeCopy.OnWatch -> R.string.episode_info_on_watch
        }
}

/**
 * The header's watch button, shown only while a watch has linked: it sends the episode to the
 * watch, and removes it again once the watch has it.
 */
@Composable
internal fun EpisodeWatchButton(episode: Episode, actions: WatchEpisodeActions, onMessage: (Int) -> Unit, modifier: Modifier = Modifier) {
    val copy = actions.copyOf(episode.id, episode.audioUrl)
    val label = stringResource(EpisodeWatchHeader.label(copy))
    var asking by rememberSaveable(episode.id) { mutableStateOf(false) }
    val told: (Boolean) -> Unit = { handedOver -> onMessage(EpisodeWatchHeader.messageAfter(copy, handedOver)) }
    if (asking) {
        RemoveDownloadConfirmationDialog(
            episodeTitle = episode.title,
            fromWatch = true,
            onConfirm = {
                asking = false
                actions.remove(episode, told)
            },
            onDismiss = { asking = false },
        )
    }
    IconButton(
        onClick = { if (EpisodeWatchHeader.asksFirst(copy)) asking = true else actions.send(episode, told) },
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        SendToWatchIcon(copy)
    }
}

/** A short line that says what just happened on the watch, then leaves by itself. */
@Composable
internal fun EpisodeWatchMessage(message: Int?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(SHOWN_MILLIS)
            onShown()
        }
    }
    // The words stay while the pill fades out.
    var shown by remember { mutableStateOf(message) }
    if (message != null) shown = message
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Watch, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(shown?.let { stringResource(it) }.orEmpty(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private const val SHOWN_MILLIS = 3_000L
