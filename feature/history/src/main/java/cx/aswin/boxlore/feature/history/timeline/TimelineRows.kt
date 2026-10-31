package cx.aswin.boxlore.feature.history.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.historyTextStyle

/** Type of the Timeline: 14/18 medium titles, 12/16 facts with 0.3 tracking, 16/22 headers, 22/28 month names. */
internal object TimelineType {
    val title @Composable get() = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = GoogleSansWeight.semiBold)
    val meta @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
    val status @Composable get() = historyTextStyle(12f, 16f, GoogleSansWeight.semiBold)
    val header @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = GoogleSansWeight.semiBold, letterSpacing = 0.sp)
    val month @Composable get() = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = GoogleSansWeight.regular)
    val label @Composable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = GoogleSansWeight.semiBold, letterSpacing = 0.sp)
}

/** A 4dp progress track: surfaceVariant with the played part in primary. */
@Composable
internal fun TimelineProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** The primary play disc on a cover: [size] across, a [iconSize] play icon in onPrimary. */
@Composable
internal fun TimelinePlayDisc(contentDescription: String, size: Int, iconSize: Int, onClick: () -> Unit, modifier: Modifier = Modifier,) {
    Box(
        modifier =
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .expressiveClickable(shape = CircleShape, feedback = ExpressivePressFeedback.Control, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(iconSize.dp))
    }
}

/** "29 min left" with its track, or the primary "Finished" check (and a heart when liked). */
@Composable
internal fun TimelineEpisodeStatus(episode: TimelineEpisode, modifier: Modifier = Modifier) {
    if (episode.isFinished) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.history_row_finished), style = TimelineType.status, color = MaterialTheme.colorScheme.primary)
            if (episode.item.isLiked) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = stringResource(R.string.history_row_liked),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimelineProgressTrack(episode.progress, Modifier.weight(1f))
            if (episode.item.durationMs > 0L) {
                Text(timeLeftText(episode.remainingMs), style = TimelineType.status, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (episode.item.isLiked) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = stringResource(R.string.history_row_liked),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Callbacks of an episode row. */
internal data class TimelineEpisodeActions(
    val onOpen: (TimelineEpisode) -> Unit,
    val onPlay: (TimelineEpisode) -> Unit,
    val onGoToShow: (TimelineEpisode) -> Unit,
    val onRemove: (TimelineEpisode) -> Unit,
)

/**
 * An episode of a day: surfaceContainerLow, 20dp corners; the 60dp cover (with a play disc while
 * unfinished), title, "Show · 8:12 pm", the status line, and a ⋮ menu (go to show, remove).
 */
@Composable
internal fun TimelineEpisodeRow(episode: TimelineEpisode, actions: TimelineEpisodeActions, modifier: Modifier = Modifier) {
    val item = episode.item
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .expressiveClickable(shape = shape) { actions.onOpen(episode) }
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(60.dp)) {
            OptimizedImage(
                url = item.episodeImageUrl,
                fallbackUrls = listOf(item.podcastImageUrl),
                proxyWidth = 128,
                contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)),
            )
            if (episode.isUnfinished) {
                TimelinePlayDisc(
                    contentDescription = stringResource(R.string.history_resume, item.episodeTitle),
                    size = 26,
                    iconSize = 14,
                    onClick = { actions.onPlay(episode) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.episodeTitle, style = TimelineType.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.history_meta_join, item.podcastName, clockTime(item.lastPlayedAt)),
                style = TimelineType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TimelineEpisodeStatus(episode)
        }
        TimelineRowMenu(episode, actions)
    }
}

@Composable
private fun TimelineRowMenu(episode: TimelineEpisode, actions: TimelineEpisodeActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.width(40.dp).height(48.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.history_more_options_for, episode.item.episodeTitle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_go_to_show)) },
                onClick = {
                    open = false
                    actions.onGoToShow(episode)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_remove_from_history)) },
                onClick = {
                    open = false
                    actions.onRemove(episode)
                },
            )
        }
    }
}

/** A Jump back in card: 148dp cover with a 44dp play disc, the time-left line, title and show. */
@Composable
internal fun JumpBackInCard(episode: TimelineEpisode, actions: TimelineEpisodeActions, modifier: Modifier = Modifier) {
    val item = episode.item
    val coverShape = RoundedCornerShape(20.dp)
    Column(
        // Rounded at the top only: the press layer clips to its shape, and the cover's corners are the card's.
        modifier = modifier.width(148.dp).expressiveClickable(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) { actions.onOpen(episode) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(148.dp)) {
            OptimizedImage(
                url = item.episodeImageUrl,
                fallbackUrls = listOf(item.podcastImageUrl),
                proxyWidth = 320,
                contentDescription = null,
                modifier = Modifier.size(148.dp).clip(coverShape),
            )
            TimelinePlayDisc(
                contentDescription = stringResource(R.string.history_resume, item.episodeTitle),
                size = 44,
                iconSize = 20,
                onClick = { actions.onPlay(episode) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
            )
        }
        TimelineEpisodeStatus(episode)
        Text(item.episodeTitle, style = TimelineType.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.podcastName, style = TimelineType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A day's header: 16/22 title on the left, the day's listening time on the right (none before timing began). */
@Composable
internal fun TimelineDayHeader(title: String, listenedMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp).semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = TimelineType.header, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
        if (listenedMs > 0L) {
            Spacer(Modifier.width(12.dp))
            Text(durationText(listenedMs), style = TimelineType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A month's header: the 22/28 month name and its facts, with the Days|Shows toggle on the right
 * when [grouping] is given (the first month).
 */
@Composable
internal fun TimelineMonthHeader(
    title: String,
    meta: String,
    grouping: TimelineGrouping?,
    onGroupingChange: (TimelineGrouping) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TimelineType.month, color = MaterialTheme.colorScheme.onSurface)
            Text(meta, style = TimelineType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (grouping != null) TimelineGroupingToggle(grouping, onGroupingChange)
    }
}

/** Days|Shows: two 40dp halves, 2dp apart, outer corners 20 and inner 6; the chosen one primary. */
@Composable
internal fun TimelineGroupingToggle(grouping: TimelineGrouping, onChange: (TimelineGrouping) -> Unit) {
    val label = stringResource(R.string.history_group_by)
    Row(modifier = Modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TimelineGroupingButton(
            text = stringResource(R.string.history_group_days),
            selected = grouping == TimelineGrouping.DAYS,
            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp),
        ) { onChange(TimelineGrouping.DAYS) }
        TimelineGroupingButton(
            text = stringResource(R.string.history_group_shows),
            selected = grouping == TimelineGrouping.SHOWS,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp),
        ) { onChange(TimelineGrouping.SHOWS) }
    }
}

@Composable
private fun TimelineGroupingButton(text: String, selected: Boolean, shape: RoundedCornerShape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .expressiveClickable(shape = shape, feedback = ExpressivePressFeedback.Control, onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TimelineType.label, color = if (selected) colors.onPrimary else colors.onSurfaceVariant)
    }
}

/** A 48dp chip: surfaceContainerHigh, or primaryContainer when [selected]. */
@Composable
internal fun TimelineChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit,) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .expressiveClickable(shape = RoundedCornerShape(24.dp), feedback = ExpressivePressFeedback.Control, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) colors.onPrimaryContainer else colors.onSurface,
        ) { content() }
    }
}
