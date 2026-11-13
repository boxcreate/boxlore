package cx.aswin.boxlore.wear.ui.lists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.ui.WatchFormat
import cx.aswin.boxlore.wear.ui.components.ArtworkWithPlaying
import cx.aswin.boxlore.wear.ui.components.EmptyMessage
import cx.aswin.boxlore.wear.ui.components.LocalPlayingEpisode
import cx.aswin.boxlore.wear.ui.components.RowDetail
import cx.aswin.boxlore.wear.ui.components.ScreenFit
import cx.aswin.boxlore.wear.ui.components.WatchRow
import cx.aswin.boxlore.wear.ui.components.rememberScreenScale

/** What an episode list says when it has nothing in it. */
internal data class ListEmpty(val icon: ImageVector, val title: Int, val body: Int? = null)

/** Which line sits under each episode title. */
internal enum class EpisodeDetail { Length, TimeLeft, LengthAndSize }

/** One scrolling list of episodes under a title: Jump back in, New episodes, Up next and Downloads. */
@Composable
internal fun EpisodeListScreen(
    title: String,
    episodes: List<WatchEpisode>,
    detail: EpisodeDetail,
    empty: ListEmpty,
    onEpisode: (String) -> Unit,
    subtitle: String? = null,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(title)
                }
            }
            if (subtitle != null && episodes.isNotEmpty()) {
                item {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    )
                }
            }
            if (episodes.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = empty.icon,
                        title = stringResource(empty.title),
                        body = empty.body?.let { stringResource(it) },
                    )
                }
            }
            items(episodes, key = { it.id }) { episode ->
                WatchRow(
                    title = episode.title,
                    onClick = { onEpisode(episode.id) },
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    leading = {
                        ArtworkWithPlaying(
                            url = episode.artworkUrl,
                            playing = LocalPlayingEpisode.current?.takeIf { it.id == episode.id },
                            modifier = Modifier.size(if (ScreenFit.isCompact(rememberScreenScale())) 28.dp else 34.dp),
                        )
                    },
                    secondary = { EpisodeDetailLine(episode, detail) },
                    trailing = downloadRing(episode.download),
                )
            }
        }
    }
}

@Composable
private fun EpisodeDetailLine(episode: WatchEpisode, detail: EpisodeDetail) {
    val downloading = episode.download is WatchDownload.InProgress
    val left = episode.minutesLeft
    val text =
        when {
            downloading -> stringResource(R.string.wear_downloading)
            detail == EpisodeDetail.TimeLeft && left != null -> stringResource(R.string.wear_minutes_left, left)
            detail == EpisodeDetail.LengthAndSize && episode.storedBytes > 0 ->
                stringResource(
                    R.string.wear_date_and_minutes,
                    stringResource(R.string.wear_minutes, episode.durationMinutes),
                    WatchFormat.megabytes(episode.storedBytes, LocalConfiguration.current.locales[0]),
                )
            // A length nobody knows yet, as for an episode the phone only queued, is left out rather than shown as 0.
            episode.durationMinutes <= 0 -> null
            else -> stringResource(R.string.wear_minutes, episode.durationMinutes)
        }
    // The time or length comes first so it is never cut; the show's name takes what is left.
    val show = episode.showTitle
    RowDetail(
        text =
            when {
                text == null -> show
                downloading || show.isBlank() -> text
                else -> stringResource(R.string.wear_date_and_minutes, text, show)
            },
        emphasised = downloading,
        icon = Icons.Rounded.DownloadDone.takeIf { episode.download == WatchDownload.Done },
        iconLabel = stringResource(R.string.wear_downloaded),
    )
}

/** A small ring at the end of the row while an episode is still downloading. */
private fun downloadRing(download: WatchDownload): (@Composable () -> Unit)? =
    (download as? WatchDownload.InProgress)?.let { progress ->
        {
            CircularProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.size(22.dp),
                strokeWidth = 3.dp,
                colors = ProgressIndicatorDefaults.colors(indicatorColor = MaterialTheme.colorScheme.tertiary),
            )
        }
    }
