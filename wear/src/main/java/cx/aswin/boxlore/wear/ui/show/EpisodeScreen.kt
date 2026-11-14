package cx.aswin.boxlore.wear.ui.show

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.data.WatchDownloadProblem
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.ui.components.Artwork
import cx.aswin.boxlore.wear.ui.components.LocalPlayingEpisode
import cx.aswin.boxlore.wear.ui.components.NameWithTail
import cx.aswin.boxlore.wear.ui.components.PlayingEpisode
import cx.aswin.boxlore.wear.ui.components.ScreenFit
import cx.aswin.boxlore.wear.ui.components.rememberScreenScale

/** What the episode page's main button does. */
internal enum class EpisodeButton {
    Play,
    Resume,
    Pause,
    ;

    companion object {
        /** Pause while this very episode plays; otherwise Resume once it was started, Play before that. */
        fun of(episode: WatchEpisode, playing: PlayingEpisode?): EpisodeButton =
            when {
                playing?.id == episode.id && playing.isPlaying -> Pause
                (episode.minutesLeft ?: 0) > 0 -> Resume
                else -> Play
            }
    }
}

/** What the listener can do with one episode. */
internal class EpisodeActions(
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onToggleDownload: () -> Unit,
    val onPlayNext: () -> Unit,
    val onMarkPlayed: () -> Unit,
)

private val StartShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 9.dp, bottomEnd = 9.dp)
private val MiddleShape = RoundedCornerShape(9.dp)
private val EndShape = RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp, topEnd = 20.dp, bottomEnd = 20.dp)

@Composable
internal fun EpisodeScreen(
    episode: WatchEpisode,
    actions: EpisodeActions,
    problem: WatchDownloadProblem? = null,
    onProblemSeen: () -> Unit = {},
) {
    DownloadProblemDialog(problem, onProblemSeen)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val button = EpisodeButton.of(episode, LocalPlayingEpisode.current)
    val (label, icon) =
        when (button) {
            EpisodeButton.Play -> R.string.wear_play to Icons.Rounded.PlayArrow
            EpisodeButton.Resume -> R.string.wear_resume to Icons.Rounded.PlayArrow
            EpisodeButton.Pause -> R.string.wear_pause to Icons.Rounded.Pause
        }
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = if (button == EpisodeButton.Pause) actions.onPause else actions.onPlay) {
                if (!ScreenFit.isCompact(rememberScreenScale())) Icon(icon, contentDescription = null)
                Text(text = stringResource(label), maxLines = 1, softWrap = false)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Artwork(url = episode.artworkUrl, modifier = Modifier.size(32.dp), shape = RoundedCornerShape(9.dp))
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = if (ScreenFit.isCompact(rememberScreenScale())) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val left = episode.minutesLeft
                    NameWithTail(
                        name = episode.showTitle,
                        tail =
                            when {
                                left == 0 -> stringResource(R.string.wear_played)
                                left != null -> stringResource(R.string.wear_minutes_left, left)
                                else -> stringResource(R.string.wear_minutes, episode.durationMinutes)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.92f),
                    )
                }
            }
            item {
                EpisodeActionRow(episode, actions, Modifier.transformedHeight(this, spec))
            }
        }
    }
}

/** Says why a download did not start, and what the listener can do about it. */
@Composable
private fun DownloadProblemDialog(problem: WatchDownloadProblem?, onSeen: () -> Unit) {
    val overLimit = problem != WatchDownloadProblem.WatchFull
    AlertDialog(
        visible = problem != null,
        onDismissRequest = onSeen,
        edgeButton = { AlertDialogDefaults.EdgeButton(onClick = onSeen) },
        title = {
            Text(
                text = stringResource(if (overLimit) R.string.wear_over_limit_title else R.string.wear_watch_full_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(if (overLimit) R.string.wear_over_limit_body else R.string.wear_watch_full_body),
                textAlign = TextAlign.Center,
            )
        },
    )
}

/** How one action tile reads: its icon, its label, what a screen reader says, and whether it is lit. */
private class TileLook(val icon: ImageVector, val label: String, val spoken: String = label, val lit: Boolean = false)

@Composable
private fun EpisodeActionRow(episode: WatchEpisode, actions: EpisodeActions, modifier: Modifier) {
    val first = remember { MutableInteractionSource() }
    val second = remember { MutableInteractionSource() }
    val third = remember { MutableInteractionSource() }
    var confirmRemove by remember { mutableStateOf(false) }
    val stored = episode.download != WatchDownload.None
    ButtonGroup(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        ActionTile(
            look = downloadLook(episode.download),
            shape = StartShape,
            source = first,
            // Starting a download is one tap; throwing one away asks first.
            onClick = { if (stored) confirmRemove = true else actions.onToggleDownload() },
            modifier = Modifier.animateWidth(first),
        )
        ActionTile(
            look = TileLook(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.wear_play_next)),
            shape = MiddleShape,
            source = second,
            onClick = actions.onPlayNext,
            modifier = Modifier.animateWidth(second),
        )
        ActionTile(
            look = TileLook(Icons.Rounded.Check, stringResource(R.string.wear_played), stringResource(R.string.wear_mark_played)),
            shape = EndShape,
            source = third,
            onClick = actions.onMarkPlayed,
            modifier = Modifier.animateWidth(third),
        )
    }
    AlertDialog(
        visible = confirmRemove,
        onDismissRequest = { confirmRemove = false },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    confirmRemove = false
                    actions.onToggleDownload()
                },
            )
        },
        title = { Text(text = stringResource(R.string.wear_remove_download_title), textAlign = TextAlign.Center) },
        text = { Text(text = stringResource(R.string.wear_remove_download_body), textAlign = TextAlign.Center) },
    )
}

@Composable
private fun downloadLook(download: WatchDownload): TileLook =
    when (download) {
        WatchDownload.None -> TileLook(Icons.Rounded.Download, stringResource(R.string.wear_download))
        is WatchDownload.InProgress -> {
            val percent = stringResource(R.string.wear_percent, (download.fraction * 100).toInt())
            TileLook(Icons.Rounded.Downloading, percent, stringResource(R.string.wear_downloading_percent, percent), lit = true)
        }
        WatchDownload.Done ->
            TileLook(
                icon = Icons.Rounded.DownloadDone,
                label = stringResource(R.string.wear_downloaded),
                spoken = stringResource(R.string.wear_downloaded_remove),
                lit = true,
            )
    }

@Composable
private fun ActionTile(
    look: TileLook,
    shape: Shape,
    source: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val content = if (look.lit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp).semantics { contentDescription = look.spoken },
        shape = shape,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = content,
            ),
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 3.dp, bottom = 5.dp),
        interactionSource = source,
    ) {
        // A small watch has no room for three labels in a row: the tiles keep their icons, and
        // only a running download keeps its text, because the percentage is the news.
        val compact = ScreenFit.isCompact(rememberScreenScale())
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!compact || look.icon != Icons.Rounded.Downloading) {
                Icon(imageVector = look.icon, contentDescription = null, modifier = Modifier.size(if (compact) 24.dp else 20.dp))
            }
            if (!compact || look.icon == Icons.Rounded.Downloading) {
                Text(
                    text = look.label,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
