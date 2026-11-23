package cx.aswin.boxlore.feature.history.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
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

    // The theme's titleLarge carries its weight in its font; this draws the boards' regular weight.
    val month @Composable get() = historyTextStyle(22f, 28f, GoogleSansWeight.regular)

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

/**
 * The primary play disc on a cover: [size] across, a [iconSize] play icon in onPrimary. Its touch
 * area is 48dp, centred on the disc ([modifier] places the disc itself), so a tap just beside a
 * small disc still resumes; the disc and its colour shrink together when pressed.
 */
@Composable
internal fun TimelinePlayDisc(contentDescription: String, size: Int, iconSize: Int, onClick: () -> Unit, modifier: Modifier = Modifier,) {
    val reach = maxOf(size, PLAY_DISC_TOUCH)
    // [modifier] places a [size] box; the touch box is that box grown to [reach] around the same centre
    // (laid out at [reach] and moved back by half the difference), so the disc stays exactly where it was.
    val shift = ((reach - size) / 2).dp
    Box(
        modifier
            .offset(x = shift, y = shift)
            .size(reach.dp)
            .expressiveClickable(shape = CircleShape, feedback = ExpressivePressFeedback.Control, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primary, CircleShape).testTag(PLAY_DISC_TAG), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(iconSize.dp))
        }
    }
}

/** The play disc's touch size and its visible disc's test tag. */
private const val PLAY_DISC_TOUCH = 48
const val PLAY_DISC_TAG = "history_play_disc"

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
    // Keyed by the episode, so a reused row never opens another row's menu; saved through a fold.
    val menu = rememberBoxloreMenuState(episode.item.episodeId)
    val goToShow = stringResource(R.string.history_go_to_show)
    val remove = stringResource(R.string.history_remove_from_history)
    Box {
        IconButton(onClick = menu::open, modifier = Modifier.width(40.dp).height(48.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.history_more_options_for, episode.item.episodeTitle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        BoxloreDropdownMenu(menu) {
            group { item(goToShow, onClick = { actions.onGoToShow(episode) }, leadingIcon = Icons.Rounded.Podcasts) }
            group { item(remove, onClick = { actions.onRemove(episode) }, leadingIcon = Icons.Rounded.DeleteOutline, destructive = true) }
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TimelineGroupingToggle(grouping: TimelineGrouping, onChange: (TimelineGrouping) -> Unit) {
    val label = stringResource(R.string.history_group_by)
    val options = listOf(TimelineGrouping.DAYS to stringResource(R.string.history_group_days), TimelineGrouping.SHOWS to stringResource(R.string.history_group_shows))
    // As wide as its two equal segments need (the connected selector fills whatever width it gets).
    val style = TimelineType.label
    val measurer = rememberTextMeasurer()
    val widest = with(LocalDensity.current) { options.maxOf { (_, text) -> measurer.measure(text, style).size.width }.toDp() }
    val segment = widest + GroupingPadding * 2 + 2.dp
    ConnectedOptionSelector(
        options = options,
        selected = grouping,
        onSelect = onChange,
        labelStyle = style,
        contentPadding = PaddingValues(horizontal = GroupingPadding),
        colors = historySelectorColors(),
        modifier = Modifier.width(segment * 2 + ButtonGroupDefaults.ConnectedSpaceBetween).height(40.dp).semantics { contentDescription = label },
    )
}

private val GroupingPadding = 14.dp

/** History's selectors: surfaceContainerHigh segments, the chosen one primary, as on the boards. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun historySelectorColors(): ToggleButtonColors {
    val colors = MaterialTheme.colorScheme
    return ToggleButtonDefaults.colors(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        checkedContainerColor = colors.primary,
        checkedContentColor = colors.onPrimary,
    )
}

/** A 48dp chip: surfaceContainerHigh, or primaryContainer when [selected]. */
@Composable
internal fun TimelineChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit,) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
        modifier
            .height(48.dp)
            // The press layer wraps the colour, so the whole chip shrinks as one piece.
            .expressiveClickable(shape = RoundedCornerShape(24.dp), feedback = ExpressivePressFeedback.Control, onClick = onClick)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) colors.onPrimaryContainer else colors.onSurface,
        ) { content() }
    }
}
