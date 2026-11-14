package cx.aswin.boxlore.wear.ui.show

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow
import cx.aswin.boxlore.wear.ui.WatchFormat
import cx.aswin.boxlore.wear.ui.components.Artwork
import cx.aswin.boxlore.wear.ui.components.EmptyMessage
import cx.aswin.boxlore.wear.ui.components.RowDetail
import cx.aswin.boxlore.wear.ui.components.WatchRow
import java.time.ZoneId

@Composable
internal fun ShowScreen(
    show: WatchShow,
    episodes: List<WatchEpisode>,
    onToggleSubscribed: () -> Unit,
    onEpisode: (String) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Artwork(url = show.artworkUrl, modifier = Modifier.size(46.dp), shape = RoundedCornerShape(13.dp))
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        // On its own page the show's name is the point: three lines before it is cut.
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                SubscribeButton(
                    subscribed = show.subscribed,
                    onClick = onToggleSubscribed,
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            if (episodes.isEmpty()) {
                item { EmptyMessage(icon = Icons.Rounded.Podcasts, title = stringResource(R.string.wear_episodes_empty)) }
            }
            items(episodes, key = { it.id }) { episode ->
                // No artwork here: every row is the same show, and the title needs the width.
                WatchRow(
                    title = episode.title,
                    onClick = { onEpisode(episode.id) },
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    secondary = { EpisodeState(episode) },
                )
            }
        }
    }
}

@Composable
private fun SubscribeButton(
    subscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val colors = if (subscribed) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
    Button(onClick = onClick, modifier = modifier, colors = colors, transformation = transformation) {
        Icon(
            imageVector = if (subscribed) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(if (subscribed) R.string.wear_subscribed else R.string.wear_subscribe),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Where the listener is with this episode: finished, part-way through, or not started. */
@Composable
private fun EpisodeState(episode: WatchEpisode) {
    val left = episode.minutesLeft
    when {
        left == 0 -> RowDetail(text = withDate(stringResource(R.string.wear_played), episode), icon = Icons.Rounded.Check)
        left != null -> RowDetail(text = withDate(stringResource(R.string.wear_minutes_left, left), episode), icon = Icons.Rounded.PlayArrow, emphasised = true)
        else -> RowDetail(text = episodeLine(episode), icon = Icons.Rounded.PlayArrow)
    }
}

/** "Played · 6 Nov": the state first, so it is never the part that is cut. */
@Composable
private fun withDate(state: String, episode: WatchEpisode): String {
    val released = episode.releasedAtMillis ?: return state
    val date = WatchFormat.shortDate(released, ZoneId.systemDefault(), LocalConfiguration.current.locales[0])
    return stringResource(R.string.wear_date_and_minutes, state, date)
}

/** "6 Nov · 56 min", or just the length when the release date is unknown. */
@Composable
private fun episodeLine(episode: WatchEpisode): String {
    val minutes = stringResource(R.string.wear_minutes, episode.durationMinutes)
    val released = episode.releasedAtMillis ?: return minutes
    val locale = LocalConfiguration.current.locales[0]
    val date = WatchFormat.shortDate(released, ZoneId.systemDefault(), locale)
    return stringResource(R.string.wear_date_and_minutes, date, minutes)
}
