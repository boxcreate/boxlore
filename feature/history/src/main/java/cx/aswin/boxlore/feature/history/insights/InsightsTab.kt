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
internal class InsightsStorySlots(val header: LazyListScope.() -> Unit = {}, val footer: LazyListScope.() -> Unit = {})

/** The Insights list's test tag. */
const val INSIGHTS_LIST_TAG = "insights_list"

/**
 * The Insights tab: stories, not stat tiles (boards A4 and A5); while boxlore has timed fewer
 * than five days of listening, what history alone can say (A6). [stories] hold the monthly
 * story's entry card and the earlier months; [listState] is held by the screen, so the
 * tab keeps its place while Timeline is open.
 */
@Composable
internal fun InsightsTab(
    content: InsightsContent,
    period: ListeningPeriod,
    today: LocalDate,
    callbacks: InsightsCallbacks,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    stories: InsightsStorySlots = InsightsStorySlots(),
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag(INSIGHTS_LIST_TAG),
    ) {
        if (content.hasEnoughTiming) stories.header(this)
        item(key = "period") { InsightsPeriodSelector(period, callbacks.onPeriodChange) }
        if (content.hasEnoughTiming) {
            fullInsights(content, period, today, callbacks)
            stories.footer(this)
        } else {
            earlyInsights(content.early)
        }
    }
}

private fun LazyListScope.fullInsights(content: InsightsContent, period: ListeningPeriod, today: LocalDate, callbacks: InsightsCallbacks) {
    item(key = "time") { TimeListenedCard(content.time, period) }
    content.type?.let { type -> item(key = "type") { ListeningTypeCard(type) } }
    if (content.topShows.isNotEmpty()) item(key = "top_shows") { TopShowsCard(content.topShows) }
    if (content.parts.isNotEmpty()) item(key = "when") { WhenYouListenCard(content.parts) }
    if (content.outputs.isNotEmpty()) item(key = "where") { WhereYouListenCard(content.outputs) }
    content.weekdays?.let { weekdays -> item(key = "weekdays") { BigDayCard(weekdays, period) } }
    item(key = "mosaic") { MonthMosaicCard(content.mosaic, today, callbacks.onOpenDay) }
    item(key = "records") { RecordsSection(content.records) }
    if (content.genres.isNotEmpty()) item(key = "genres") { GenresCard(content.genres) }
    content.sources?.let { sources -> sources.newShare?.let { newShare -> item(key = "fresh") { NewOrCatchingUpCard(sources, newShare) } } }
    item(key = "milestone") { MilestoneCard(content.milestone) }
    if (content.inProgress.count > 0) item(key = "in_progress") { InProgressCard(content.inProgress, callbacks.onReviewInProgress) }
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
                    stringResource(R.string.history_early_finished),
                    style = InsightsType.body16.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            FlowRow(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightsPill(null, stringResource(R.string.history_early_in_progress, early.inProgress))
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
    val description = stringResource(R.string.history_early_clock_progress, early.timedDays, InsightsLogic.TIMED_DAYS_FOR_INSIGHTS)
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
