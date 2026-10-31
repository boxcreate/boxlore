package cx.aswin.boxlore.feature.history.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.history.R

/**
 * A show of a month: cover, name and facts, a segment per episode (finished in primary, the rest
 * primaryContainer), then a Resume button for its latest unfinished episode or "All N finished".
 */
@Composable
internal fun TimelineShowCard(
    show: TimelineShow,
    listenedText: String?,
    onResume: (TimelineEpisode) -> Unit,
    onOpenShow: (TimelineShow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OptimizedImage(
                url = show.imageUrl,
                proxyWidth = 160,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
            )
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    show.podcastName,
                    style = TimelineType.header,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(showMeta(show, listenedText), style = TimelineType.meta, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = { onOpenShow(show) }) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.history_open_show, show.podcastName),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        TimelineShowSegments(show)
        val resume = show.resume
        if (resume != null) {
            TimelineResumeButton(resume) { onResume(resume) }
        } else if (show.allFinished) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
                Text(
                    pluralStringResource(R.plurals.history_show_all_finished, show.episodeCount, show.episodeCount),
                    style = TimelineType.label,
                    color = colors.tertiary,
                )
            }
        }
    }
}

@Composable
private fun showMeta(show: TimelineShow, listenedText: String?): String {
    val count =
        if (show.isBrief) {
            pluralStringResource(R.plurals.history_brief_count, show.episodeCount, show.episodeCount)
        } else {
            pluralStringResource(R.plurals.history_episode_count, show.episodeCount, show.episodeCount)
        }
    var meta = count
    // "N finished" only when some but not all are: all finished reads under the bar.
    if (show.finishedCount in 1 until show.episodeCount) {
        meta = stringResource(R.string.history_meta_join, meta, pluralStringResource(R.plurals.history_finished_count, show.finishedCount, show.finishedCount))
    }
    if (listenedText != null) meta = stringResource(R.string.history_meta_join, meta, listenedText)
    return meta
}

@Composable
private fun TimelineShowSegments(show: TimelineShow) {
    val description = stringResource(R.string.history_show_finished_of, show.finishedCount, show.episodeCount)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        show.segments.forEach { finished ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

/** 56dp, 18dp corners, surfaceContainerHigh: a 40dp play disc, "Resume {title}", and the time left. */
@Composable
private fun TimelineResumeButton(episode: TimelineEpisode, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val label = stringResource(R.string.history_resume, episode.item.episodeTitle)
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .expressiveClickable(shape = shape, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
        }
        Text(label, style = TimelineType.label, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (episode.item.durationMs > 0L) {
            Text(timeLeftText(episode.remainingMs), style = TimelineType.meta, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}
