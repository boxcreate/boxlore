package cx.aswin.boxlore.feature.history.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.TimelineTime
import cx.aswin.boxlore.feature.history.timeline.TimelineType
import cx.aswin.boxlore.feature.history.timeline.currentLocale
import java.time.LocalDate

/** The Insights tab's callbacks. */
internal data class InsightsCallbacks(
    val onPeriodChange: (ListeningPeriod) -> Unit,
    val onOpenDay: (LocalDate) -> Unit,
    val onReviewInProgress: () -> Unit,
)

/** What the Insights list shows around its cards: the monthly story's entry card above, the earlier months below. */
internal class InsightsStorySlots(val header: LazyListScope.() -> Unit = {}, val footer: LazyListScope.() -> Unit = {}, val headerItems: Int = 0)

/** The Insights list's test tag. */
const val INSIGHTS_LIST_TAG = "insights_list"

/**
 * The Insights tab: stories, not stat tiles (boards A4 and A5); while boxlore has timed fewer
 * than five days of listening, what history alone can say (A6). [stories] hold the monthly
 * story's entry card and the earlier months; [listState] is held by the screen, so the
 * tab keeps its place while Timeline is open.
 */
@Suppress("LongParameterList") // Content, callbacks and the screen-held list state and layout.
@Composable
internal fun InsightsTab(
    content: InsightsContent,
    period: ListeningPeriod,
    today: LocalDate,
    callbacks: InsightsCallbacks,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    stories: InsightsStorySlots = InsightsStorySlots(),
    columns: Int = 1,
    place: InsightsPlace = remember { InsightsPlace() },
) {
    val cards = fullInsightCards(content, period, today, callbacks)
    val rows = InsightsLayout.rows(cards.map { it.first }, columns)
    // The story card (when there is one) and the period selector come before the cards.
    KeepFirstCardInView(listState, rows, columns, leading = stories.headerItems + 1, place)
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag(INSIGHTS_LIST_TAG),
    ) {
        if (content.hasEnoughTiming) stories.header(this)
        // Only once every card below follows it: while timing fills in, the cards are all-time.
        if (content.hasEnoughTiming) item(key = "period") { InsightsPeriodSelector(period, callbacks.onPeriodChange) }
        if (content.hasEnoughTiming) {
            val byKey = cards.toMap()
            // One list item a row, keyed by its first card, so the list's items keep their type across window sizes.
            rows.forEach { row ->
                item(key = row.first()) {
                    if (row.size == 1) {
                        byKey.getValue(row.first())()
                    } else {
                        // Paired cards start together at the top; each keeps its own height.
                        Row(Modifier.fillMaxWidth().testTag(INSIGHTS_PAIR_TAG), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                            row.forEach { key -> Box(Modifier.weight(1f)) { byKey.getValue(key)() } }
                        }
                    }
                }
            }
            stories.footer(this)
        } else {
            earlyInsights(content.early)
        }
    }
}

/** A row of two Insights cards on large tablets: its test tag. */
const val INSIGHTS_PAIR_TAG = "insights_pair"

/**
 * Where the listener is among the Insights cards: the card at the top of the list ([anchor]) and the
 * columns it was laid out in. Held by the screen and saved, so a window change while Timeline is
 * open, or the activity being recreated, still brings the listener back to the same card.
 */
internal class InsightsPlace(anchor: String? = null, var columns: Int = 0) {
    var anchor: String? = anchor

    companion object {
        val Saver: Saver<InsightsPlace, Any> = listSaver(
            save = { listOf(it.anchor.orEmpty(), it.columns) },
            restore = { InsightsPlace((it[0] as String).ifEmpty { null }, it[1] as Int) },
        )
    }
}

/**
 * Keeps the listener on their card when the number of columns changes: the card at the top (kept
 * while it shares a row with another, so going back to one column returns to it, not its partner)
 * is brought back to the top in the new layout.
 */
@Composable
private fun KeepFirstCardInView(listState: LazyListState, rows: List<List<String>>, columns: Int, leading: Int, place: InsightsPlace) {
    val currentRows by rememberUpdatedState(rows)
    LaunchedEffect(listState, place) {
        // The first card below the list's top padding: one peeking into the padding isn't the listener's.
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > 0 }?.key as? String }.collect { key ->
            if (key == null) return@collect
            val row = currentRows.getOrNull(InsightsLayout.rowOf(currentRows, key))
            val anchor = place.anchor
            place.anchor = if (anchor != null && row != null && anchor in row) anchor else key
        }
    }
    // While composing the new layout, before the list measures it: ask it to open at the row holding the card.
    if (place.columns != columns) {
        val changed = place.columns != 0
        place.columns = columns
        val row = place.anchor?.let { InsightsLayout.rowOf(rows, it) } ?: -1
        if (changed && row >= 0) listState.requestScrollToItem(leading + row)
    }
}

/** The full Insights cards in order, each with its list key. */
private fun fullInsightCards(content: InsightsContent, period: ListeningPeriod, today: LocalDate, callbacks: InsightsCallbacks): List<Pair<String, @Composable () -> Unit>> = buildList {
    if (!content.hasEnoughTiming) return@buildList
    add("time" to { TimeListenedCard(content.time, period) })
    content.type?.let { type -> add("type" to { ListeningTypeCard(type) }) }
    if (content.topShows.isNotEmpty()) add("top_shows" to { TopShowsCard(content.topShows) })
    if (content.parts.isNotEmpty()) add("when" to { WhenYouListenCard(content.parts) })
    if (content.outputs.isNotEmpty()) add("where" to { WhereYouListenCard(content.outputs) })
    content.weekdays?.let { weekdays -> add("weekdays" to { BigDayCard(weekdays, period) }) }
    add("mosaic" to { MonthMosaicCard(content.mosaic, today, callbacks.onOpenDay) })
    add("records" to { RecordsSection(content.records) })
    if (content.genres.isNotEmpty()) add("genres" to { GenresCard(content.genres) })
    content.sources?.let { sources -> sources.newShare?.let { newShare -> add("fresh" to { NewOrCatchingUpCard(sources, newShare) }) } }
    add("milestone" to { MilestoneCard(content.milestone) })
    if (content.inProgress.count > 0) add("in_progress" to { InProgressCard(content.inProgress, callbacks.onReviewInProgress) })
}

private fun LazyListScope.earlyInsights(early: EarlyInsights) {
    item(key = "early_hero") { EarlyHeroCard(early) }
    if (early.historyBeforeTiming) item(key = "early_timing") { TimingNoticeCard(early.timingSince) }
    if (early.showsByEpisodes.isNotEmpty()) item(key = "early_shows") { ShowsByEpisodesCard(early.showsByEpisodes) }
    item(key = "early_clock") { ListeningClockProgressCard(early) }
}

/** "Your listening so far" (A6): episodes finished at 45sp, with in-progress and days-played pills. */
@Composable
private fun EarlyHeroCard(early: EarlyInsights) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.primaryContainer)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 36.dp, y = (-44).dp)
                .size(150.dp)
                .rotate(12f)
                .clip(InsightsBlobShape)
                .background(colors.primary.copy(alpha = 0.14f)),
        )
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.history_early_title), style = InsightsType.label, color = colors.onPrimaryContainer)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(early.finished.toString(), style = InsightsType.hero.copy(letterSpacing = 0.sp), color = colors.onPrimaryContainer)
                Text(
                    pluralStringResource(R.plurals.history_early_finished, early.finished),
                    style = InsightsType.body16.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            FlowRow(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (early.inProgress > 0) InsightsPill(null, stringResource(R.string.history_early_in_progress, early.inProgress))
                if (early.playedDaysThisMonth > 0) {
                    InsightsPill(null, pluralStringResource(R.plurals.history_early_played_days, early.playedDaysThisMonth, early.playedDaysThisMonth))
                }
            }
        }
    }
}

/**
 * "Limited insights for now" (A6), shown only when the episode history goes back before the
 * listening time does: boxlore started saving listening time on that day, so check back after a
 * few days of listening. The listening clock card below counts the days.
 */
@Composable
private fun TimingNoticeCard(since: LocalDate?) {
    val colors = MaterialTheme.colorScheme
    // The date never breaks across lines.
    val date = since?.let { TimelineTime.shortDate(it, currentLocale()).replace(' ', '\u00A0') }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainerHigh).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.history_early_timing_title), style = TimelineType.header, color = colors.onSurface)
            Text(
                if (date != null) stringResource(R.string.history_early_timing_body, date) else stringResource(R.string.history_early_timing_body_new),
                style = InsightsType.body14,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** "Most played shows" by episodes (A6). */
@Composable
private fun ShowsByEpisodesCard(shows: List<ShowByEpisodes>) {
    val colors = MaterialTheme.colorScheme
    InsightsCard {
        InsightsCardHeader(stringResource(R.string.history_early_most_played), stringResource(R.string.history_early_by_episodes))
        shows.forEach { show ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OptimizedImage(url = show.imageUrl, proxyWidth = 128, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(show.name, style = TimelineType.label, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val count =
                        if (show.isBrief) {
                            pluralStringResource(R.plurals.history_brief_count, show.episodes, show.episodes)
                        } else {
                            pluralStringResource(R.plurals.history_episode_count, show.episodes, show.episodes)
                        }
                    val meta =
                        if (show.finished > 0) {
                            stringResource(R.string.history_meta_join, count, pluralStringResource(R.plurals.history_finished_count, show.finished, show.finished))
                        } else {
                            count
                        }
                    Text(meta, style = TimelineType.meta, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Your listening clock" (A6): five 8dp segments, one per timed day, until when you listen can show. */
@Composable
private fun ListeningClockProgressCard(early: EarlyInsights) {
    val colors = MaterialTheme.colorScheme
    val description = pluralStringResource(R.plurals.history_early_clock_progress, InsightsLogic.TIMED_DAYS_FOR_INSIGHTS, early.timedDays, InsightsLogic.TIMED_DAYS_FOR_INSIGHTS)
    InsightsCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.history_early_clock), style = TimelineType.header, color = colors.onSurface)
            Text(
                pluralStringResource(R.plurals.history_early_clock_more_days, early.timedDaysNeeded, early.timedDaysNeeded),
                style = InsightsType.body14,
                color = colors.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(InsightsLogic.TIMED_DAYS_FOR_INSIGHTS) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (index < early.timedDays) colors.primary else colors.surfaceVariant),
                )
            }
        }
    }
}
