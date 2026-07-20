package cx.aswin.boxlore.feature.library.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.ListeningDayActivity
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningTimeBucket
import cx.aswin.boxlore.feature.library.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max

@Composable
fun HistoryActivityGraph(
    dailyActivity: List<ListeningDayActivity>,
    modifier: Modifier = Modifier,
) {
    if (dailyActivity.isEmpty()) return

    val weeks =
        remember(dailyActivity) {
            val byDay = dailyActivity.associateBy { it.localDay }
            val minDay = dailyActivity.minOf { it.localDay }
            val maxDay = dailyActivity.maxOf { it.localDay }
            val firstMonday =
                LocalDate.ofEpochDay(minDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val lastSunday =
                LocalDate.ofEpochDay(maxDay).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val weekStarts = mutableListOf<LocalDate>()
            var cursor = firstMonday
            while (!cursor.isAfter(lastSunday)) {
                weekStarts += cursor
                cursor = cursor.plusWeeks(1)
            }
            weekStarts.map { start ->
                (0..6).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    date to (byDay[date.toEpochDay()]?.consumedMs ?: 0L)
                }
            }
        }
    if (weeks.isEmpty()) return

    val pageCount = weeks.size
    val lastPage = (pageCount - 1).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = lastPage) { pageCount }
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage(lastPage)
        }
    }
    val safePage = pagerState.currentPage.coerceIn(0, lastPage)
    val week = weeks[safePage]
    val rangeLabel =
        remember(week) {
            val start = week.first().first
            val end = week.last().first
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            "${start.format(fmt)} – ${end.format(fmt)}"
        }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(148.dp),
            ) { page ->
                val pageWeek = weeks[page]
                val pageMax = pageWeek.maxOf { it.second }.coerceAtLeast(1L)
                WeekdayBars(
                    week = pageWeek,
                    maxMs = pageMax,
                )
            }
            Text(
                text = stringResource(R.string.history_activity_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HistoryTimeOfDayGraph(
    insights: ListeningInsightSummary,
    modifier: Modifier = Modifier,
) {
    val buckets =
        listOf(
            ListeningTimeBucket.MORNING to insights.morningMs,
            ListeningTimeBucket.AFTERNOON to insights.afternoonMs,
            ListeningTimeBucket.EVENING to insights.eveningMs,
            ListeningTimeBucket.NIGHT to insights.nightMs,
        )
    val totalMs = buckets.sumOf { it.second }
    val maxMs = buckets.maxOf { it.second }.coerceAtLeast(1L)
    val peakLabel =
        when (insights.peakBucket) {
            ListeningTimeBucket.MORNING -> stringResource(R.string.history_bucket_morning)
            ListeningTimeBucket.AFTERNOON -> stringResource(R.string.history_bucket_afternoon)
            ListeningTimeBucket.EVENING -> stringResource(R.string.history_bucket_evening)
            ListeningTimeBucket.NIGHT -> stringResource(R.string.history_bucket_night)
            null -> null
        }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_tod_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (peakLabel != null && totalMs > 0) {
                    Text(
                        text = stringResource(R.string.history_tod_peak, peakLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (totalMs <= 0L) {
                Text(
                    text = stringResource(R.string.history_tod_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TimeOfDayBars(
                    buckets = buckets,
                    peakBucket = insights.peakBucket,
                    maxMs = maxMs,
                    modifier = Modifier.height(148.dp),
                )
            }
        }
    }
}

private data class TimeOfDayBarColors(
    val peak: Color,
    val bar: Color,
    val track: Color,
)

@Composable
private fun TimeOfDayBars(
    buckets: List<Pair<ListeningTimeBucket, Long>>,
    peakBucket: ListeningTimeBucket?,
    maxMs: Long,
    modifier: Modifier = Modifier,
) {
    val colors =
        TimeOfDayBarColors(
            peak = MaterialTheme.colorScheme.primary,
            bar = MaterialTheme.colorScheme.secondary,
            track = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { (bucket, ms) ->
            TimeOfDayBarColumn(
                bucket = bucket,
                ms = ms,
                maxMs = maxMs,
                peakBucket = peakBucket,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun timeBucketLabel(bucket: ListeningTimeBucket): String =
    when (bucket) {
        ListeningTimeBucket.MORNING -> stringResource(R.string.history_bucket_morning)
        ListeningTimeBucket.AFTERNOON -> stringResource(R.string.history_bucket_afternoon)
        ListeningTimeBucket.EVENING -> stringResource(R.string.history_bucket_evening)
        ListeningTimeBucket.NIGHT -> stringResource(R.string.history_bucket_night)
    }

@Composable
private fun TimeOfDayBarColumn(
    bucket: ListeningTimeBucket,
    ms: Long,
    maxMs: Long,
    peakBucket: ListeningTimeBucket?,
    colors: TimeOfDayBarColors,
    modifier: Modifier = Modifier,
) {
    val fraction = (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
    val fill = if (bucket == peakBucket) colors.peak else colors.bar
    val label = timeBucketLabel(bucket)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (ms > 0) formatDuration(ms) else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.track),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height((96.dp * max(fraction, if (ms > 0) 0.08f else 0f)))
                        .clip(RoundedCornerShape(12.dp))
                        .background(fill),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bucket == peakBucket) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WeekdayBars(
    week: List<Pair<LocalDate, Long>>,
    maxMs: Long,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { (date, ms) ->
            val fraction = (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (ms > 0) formatDuration(ms) else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height((96.dp * max(fraction, if (ms > 0) 0.08f else 0f)))
                                .clip(RoundedCornerShape(12.dp))
                                .background(barColor),
                    )
                }
                Text(
                    text =
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun HistoryDayChips(
    activeDays: Set<LocalDate>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit,
    onPickOlderDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = activeDays.sortedDescending().take(7)
    val chipFormatter = DateTimeFormatter.ofPattern("MMM d")

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(recent.size) { index ->
                    val day = recent[index]
                    FilterChip(
                        selected = selectedDate == day,
                        onClick = { onSelectDate(if (selectedDate == day) null else day) },
                        label = { Text(day.format(chipFormatter)) },
                    )
                }
            }
        }
        if (selectedDate != null) {
            IconButton(onClick = { onSelectDate(null) }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.history_clear_date_filter),
                )
            }
        }
        IconButton(onClick = onPickOlderDate) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = stringResource(R.string.history_pick_date),
            )
        }
    }
}
