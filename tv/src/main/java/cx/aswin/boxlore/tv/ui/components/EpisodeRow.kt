package cx.aswin.boxlore.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.dateAndLength
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.timeLeftText

private val RowArtSize = 72.dp
private val RowArtShape = RoundedCornerShape(14.dp)
private val RowProgressWidth = 56.dp

/**
 * One episode in a list: the show page, Library and Search. Its cover, then three tiers of text so
 * the eye lands on the title first: the title, largest and brightest; a quieter line with the show
 * (when the list mixes shows), date, length and how far in the listener is; and the notes, dimmest.
 * The row the remote is on takes a light outline, opens up to two lines of title and notes, and
 * shows what OK and a long press do.
 */
@Composable
fun EpisodeRow(episode: TvEpisode, onPlay: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier, withShow: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    ContentSurface(
        onClick = onPlay,
        onLongClick = onOpen,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
        restingColor = Color.Transparent,
        focusScale = 1.015f,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(episode.artworkUrl, Modifier.size(RowArtSize), RowArtShape)
            Column(Modifier.weight(1f)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TvColors.onSurface,
                    maxLines = if (focused) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                EpisodeDetails(episode, withShow, Modifier.padding(top = 4.dp))
                if (episode.description.isNotEmpty()) {
                    Text(
                        episode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TvColors.onSurfaceMuted,
                        maxLines = if (focused) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (focused) KeyHints()
            }
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.tv_play), tint = if (focused) TvColors.onSurface else TvColors.onSurfaceMuted)
            }
        }
    }
}

/** The middle tier: show, date and length, then Played or how much is left. */
@Composable
private fun EpisodeDetails(episode: TvEpisode, withShow: Boolean, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val timing = dateAndLength(episode.releasedAtMillis, episode.durationMinutes)
        val details = if (withShow && episode.showTitle.isNotBlank()) stringResource(R.string.tv_show_and_time, episode.showTitle, timing) else timing
        Text(
            details,
            style = MaterialTheme.typography.labelMedium,
            color = TvColors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        when (val progress = EpisodeRowRules.progress(episode)) {
            RowProgress.Played -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = TvColors.primary, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.tv_played), style = MaterialTheme.typography.labelMedium, color = TvColors.onSurfaceVariant, maxLines = 1)
            }
            is RowProgress.Started -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // The focused row is the default track's colour, so the track takes the outline's.
                ProgressBar(progress.share, Modifier.width(RowProgressWidth), track = TvColors.outlineVariant)
                Text(timeLeftText(progress.minutesLeft), style = MaterialTheme.typography.labelMedium, color = TvColors.onSurfaceVariant, maxLines = 1)
            }
            RowProgress.Unstarted -> Unit
        }
    }
}

@Composable
private fun KeyHints() {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyHint(stringResource(R.string.tv_key_ok), stringResource(R.string.tv_play))
        KeyHint(stringResource(R.string.tv_key_hold_ok), stringResource(R.string.tv_hint_episode_page))
    }
}

@Composable
private fun KeyHint(key: String, action: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = TvColors.onSurface,
            modifier = Modifier.border(1.dp, TvColors.outline, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Text(action, style = MaterialTheme.typography.labelSmall, color = TvColors.onSurfaceVariant)
    }
}

/** How far in the listener is, as a list row shows it. */
sealed interface RowProgress {
    data object Unstarted : RowProgress

    data object Played : RowProgress

    data class Started(val share: Float, val minutesLeft: Int) : RowProgress
}

object EpisodeRowRules {
    /** Played wins over a saved position, so a finished episode never reads "0m left". */
    fun progress(episode: TvEpisode): RowProgress {
        val left = episode.minutesLeft
        return when {
            episode.played -> RowProgress.Played
            left != null -> RowProgress.Started(episode.progress, left)
            else -> RowProgress.Unstarted
        }
    }
}
