package cx.aswin.boxlore.feature.history.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.history.R
import java.time.LocalDate
import java.time.YearMonth

/** What the Timeline tab shows and how: built content, the chosen filter and grouping, the search text and today. */
internal data class TimelineTabState(
    val content: TimelineContent,
    val filter: TimelineFilter,
    val grouping: TimelineGrouping,
    val query: String,
    val today: LocalDate,
)

/** The Timeline tab's callbacks. */
internal data class TimelineTabCallbacks(
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (TimelineFilter) -> Unit,
    val onGroupingChange: (TimelineGrouping) -> Unit,
    val onOpenCalendar: () -> Unit,
    val onShowAllInProgress: () -> Unit,
    val onOpenShow: (podcastId: String) -> Unit,
    val episode: TimelineEpisodeActions,
)

/** One item of the Timeline list, with the day it belongs to (for the fast scroller and Jump to a day). */
internal sealed interface TimelineEntry {
    val key: String
    val date: LocalDate?

    data object Search : TimelineEntry {
        override val key = "search"
        override val date: LocalDate? = null
    }

    data object JumpBackIn : TimelineEntry {
        override val key = "jump_back_in"
        override val date: LocalDate? = null
    }

    data object Chips : TimelineEntry {
        override val key = "chips"
        override val date: LocalDate? = null
    }

    data class Month(val month: TimelineMonth, val isFirst: Boolean,) : TimelineEntry {
        override val key = "month_${month.month}"
        override val date: LocalDate = month.days.firstOrNull()?.date ?: month.month.atEndOfMonth()
    }

    data class Day(val day: TimelineDay) : TimelineEntry {
        override val key = "day_${day.date}"
        override val date: LocalDate = day.date
    }

    data class Episode(val episode: TimelineEpisode, override val date: LocalDate,) : TimelineEntry {
        override val key = "episode_${episode.item.episodeId}"
    }

    data class Show(val show: TimelineShow, val month: YearMonth,) : TimelineEntry {
        override val key = "show_${month}_${show.podcastId}"
        override val date: LocalDate = month.atEndOfMonth()
    }

    data object NoResults : TimelineEntry {
        override val key = "no_results"
        override val date: LocalDate? = null
    }
}

/** The list's entries for [state], top to bottom. */
internal fun timelineEntries(state: TimelineTabState): List<TimelineEntry> {
    val entries = mutableListOf<TimelineEntry>(TimelineEntry.Search)
    val showJumpBackIn = state.grouping == TimelineGrouping.DAYS && state.query.isBlank() && state.content.jumpBackIn.isNotEmpty()
    if (showJumpBackIn) entries += TimelineEntry.JumpBackIn
    entries += TimelineEntry.Chips
    state.content.months.forEachIndexed { index, month ->
        entries += TimelineEntry.Month(month, isFirst = index == 0)
        when (state.grouping) {
            TimelineGrouping.DAYS ->
                month.days.forEach { day ->
                    entries += TimelineEntry.Day(day)
                    LazyListKeyPolicy.deduplicateById(day.episodes) { it.item.episodeId }.forEach { entries += TimelineEntry.Episode(it, day.date) }
                }
            TimelineGrouping.SHOWS -> month.shows.forEach { entries += TimelineEntry.Show(it, month.month) }
        }
    }
    if (state.content.months.isEmpty()) entries += TimelineEntry.NoResults
    // An episode is listed under one day only; drop any repeat key so the list never sees one twice.
    return LazyListKeyPolicy.deduplicateById(entries) { it.key }
}

/** The index to scroll to for [target]: the first day on or before it, else the oldest day. */
internal fun timelineIndexFor(entries: List<TimelineEntry>, target: LocalDate): Int? {
    val dayIndexes = entries.withIndex().filter { (_, entry) -> entry is TimelineEntry.Day || entry is TimelineEntry.Month }
    if (dayIndexes.isEmpty()) return null
    return dayIndexes.firstOrNull { (_, entry) -> entry.date?.let { it <= target } == true }?.index ?: dayIndexes.last().index
}

/** The Timeline tab (boards A1 and A3): search, Jump back in, filter chips and each month by day or by show. */
@Composable
internal fun TimelineTab(state: TimelineTabState, callbacks: TimelineTabCallbacks, listState: LazyListState, contentPadding: PaddingValues,) {
    val entries = remember(state) { timelineEntries(state) }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.key }, contentType = { it::class }) { entry ->
                TimelineEntryContent(entry, state, callbacks)
            }
        }
        TimelineFastScroller(
            listState = listState,
            dateAt = { index -> entries.getOrNull(index)?.date },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        )
    }
}

@Composable
private fun TimelineEntryContent(entry: TimelineEntry, state: TimelineTabState, callbacks: TimelineTabCallbacks) {
    val side = Modifier.padding(horizontal = 16.dp)
    when (entry) {
        TimelineEntry.Search -> TimelineSearchField(state.query, callbacks.onQueryChange, side.padding(top = 16.dp))
        TimelineEntry.JumpBackIn -> JumpBackInSection(state.content, callbacks)
        TimelineEntry.Chips -> TimelineChipsRow(state.filter, callbacks)
        is TimelineEntry.Month -> {
            val month = entry.month
            val locale = currentLocale()
            val meta =
                when (state.grouping) {
                    TimelineGrouping.DAYS -> {
                        val episodes = pluralStringResource(R.plurals.history_episode_count, month.episodeCount, month.episodeCount)
                        if (month.listenedMs > 0L) stringResource(R.string.history_meta_join, episodes, durationText(month.listenedMs)) else episodes
                    }
                    TimelineGrouping.SHOWS ->
                        stringResource(
                            R.string.history_meta_join,
                            pluralStringResource(R.plurals.history_show_count, month.shows.size, month.shows.size),
                            stringResource(R.string.history_shows_last_played_first),
                        )
                }
            TimelineMonthHeader(
                title = TimelineTime.monthTitle(month.month, state.today, locale),
                meta = meta,
                grouping = state.grouping.takeIf { entry.isFirst },
                onGroupingChange = callbacks.onGroupingChange,
                modifier = side.padding(top = 24.dp, bottom = if (state.grouping == TimelineGrouping.SHOWS) 16.dp else 4.dp),
            )
        }
        is TimelineEntry.Day -> TimelineDayHeader(dayTitle(entry.day.date, state.today), entry.day.listenedMs, side)
        is TimelineEntry.Episode -> TimelineEpisodeRow(entry.episode, callbacks.episode, side.padding(bottom = 4.dp))
        is TimelineEntry.Show ->
            TimelineShowCard(
                show = entry.show,
                listenedText = entry.show.listenedMs.takeIf { it > 0L }?.let { durationText(it) },
                onResume = callbacks.episode.onPlay,
                onOpenShow = { callbacks.onOpenShow(it.podcastId) },
                modifier = side.padding(bottom = 12.dp),
            )
        TimelineEntry.NoResults ->
            Text(
                stringResource(R.string.history_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = side.padding(top = 32.dp),
            )
    }
}

/** 52dp, 26dp corners, surfaceContainerHighest: a search icon and "Search episodes and shows". */
@Composable
private fun TimelineSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = colors.onSurface)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.surfaceContainerHighest)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.history_search_hint), style = textStyle.copy(color = colors.onSurfaceVariant), maxLines = 1)
                    field()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.history_search_clear), tint = colors.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        },
    )
}

@Composable
private fun JumpBackInSection(content: TimelineContent, callbacks: TimelineTabCallbacks) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.history_jump_back_in), style = TimelineType.header, color = colors.onSurface)
        Text(
            stringResource(R.string.history_jump_back_in_all, content.inProgressCount),
            style = TimelineType.label,
            color = colors.primary,
            modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .expressiveClickable(shape = RoundedCornerShape(12.dp), feedback = ExpressivePressFeedback.Control, onClick = callbacks.onShowAllInProgress)
                .semantics { role = Role.Button }
                .padding(horizontal = 4.dp, vertical = 12.dp),
        )
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(content.jumpBackIn, key = { "jbi_${it.item.episodeId}" }) { episode ->
            JumpBackInCard(episode, callbacks.episode)
        }
    }
}

@Composable
private fun TimelineChipsRow(filter: TimelineFilter, callbacks: TimelineTabCallbacks) {
    val calendarLabel = stringResource(R.string.history_jump_title)
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "calendar") {
            TimelineChip(
                selected = false,
                onClick = callbacks.onOpenCalendar,
                modifier = Modifier.width(48.dp).semantics {
                    contentDescription = calendarLabel
                    role = Role.Button
                },
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        items(TimelineFilter.entries, key = { it.name }) { option ->
            val label = filterLabel(option)
            TimelineChip(
                selected = option == filter,
                onClick = { callbacks.onFilterChange(option) },
                modifier = Modifier.semantics {
                    role = Role.Tab
                    selected = option == filter
                },
            ) {
                Text(label, style = TimelineType.label, modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

@Composable
private fun filterLabel(filter: TimelineFilter): String = when (filter) {
    TimelineFilter.ALL -> stringResource(R.string.history_filter_all)
    TimelineFilter.IN_PROGRESS -> stringResource(R.string.history_filter_in_progress)
    TimelineFilter.FINISHED -> stringResource(R.string.history_filter_finished)
    TimelineFilter.LIKED -> stringResource(R.string.history_filter_liked)
}
