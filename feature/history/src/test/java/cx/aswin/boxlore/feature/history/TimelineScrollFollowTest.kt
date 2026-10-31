package cx.aswin.boxlore.feature.history

import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.history.timeline.HistoryTimelineLogic
import cx.aswin.boxlore.feature.history.timeline.TimelineContent
import cx.aswin.boxlore.feature.history.timeline.TimelineDay
import cx.aswin.boxlore.feature.history.timeline.TimelineEntry
import cx.aswin.boxlore.feature.history.timeline.TimelineFilter
import cx.aswin.boxlore.feature.history.timeline.TimelineGrouping
import cx.aswin.boxlore.feature.history.timeline.TimelineMonth
import cx.aswin.boxlore.feature.history.timeline.TimelineTabState
import cx.aswin.boxlore.feature.history.timeline.timelineEntries
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Jump to a day and All in progress scroll once their reset shows; a newer request replaces a waiting one. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineScrollFollowTest {
    private val day = LocalDate.of(2026, 10, 20)
    private val requests = MutableSharedFlow<TimelineScrollTarget>(extraBufferCapacity = 1)
    private val timeline = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    private val scrolls = mutableListOf<Int>()

    @Test
    fun allInProgressScrollsToTheFiltersOnceTheyShowInProgress() = runTest {
        val follower = launch { followTimelineScrolls(requests, timeline) { scrolls += it } }
        runCurrent()

        requests.emit(TimelineScrollTarget.InProgress)
        timeline.value = ready(TimelineFilter.ALL, TimelineGrouping.DAYS)
        runCurrent()
        assertEquals(emptyList<Int>(), scrolls)

        val inProgress = ready(TimelineFilter.IN_PROGRESS, TimelineGrouping.DAYS)
        timeline.value = inProgress
        runCurrent()
        assertEquals(listOf(inProgress.entries.indexOf(TimelineEntry.Chips)), scrolls)
        follower.cancel()
    }

    @Test
    fun aJumpAfterASkippedInProgressResetStillScrolls() = runTest {
        val follower = launch { followTimelineScrolls(requests, timeline) { scrolls += it } }
        runCurrent()

        // All in progress, then Shows before its by-day state arrived: that state never shows.
        requests.emit(TimelineScrollTarget.InProgress)
        timeline.value = ready(TimelineFilter.IN_PROGRESS, TimelineGrouping.SHOWS)
        runCurrent()

        requests.emit(TimelineScrollTarget.Day(day))
        val all = ready(TimelineFilter.ALL, TimelineGrouping.DAYS)
        timeline.value = all
        runCurrent()
        assertEquals(listOf(all.entries.indexOfFirst { it is TimelineEntry.Day && it.date == day }), scrolls)

        // The replaced request no longer scrolls when its state turns up later.
        timeline.value = ready(TimelineFilter.IN_PROGRESS, TimelineGrouping.DAYS)
        runCurrent()
        assertEquals(1, scrolls.size)
        follower.cancel()
    }

    private fun ready(filter: TimelineFilter, grouping: TimelineGrouping): TimelineUiState.Ready {
        val episode = HistoryTimelineLogic.episodeOf(ListeningHistoryItem("e1", "show", "Episode", null, null, null, "Show", 0L, 0L, false, false, 0L))
        val month = TimelineMonth(YearMonth.of(2026, 10), 1, 0L, listOf(TimelineDay(day, 0L, listOf(episode))), emptyList())
        val content = TimelineContent(listOf(episode), 1, listOf(month), emptyMap(), emptySet(), day, false)
        val today = LocalDate.of(2026, 10, 30)
        return TimelineUiState.Ready(content, filter, grouping, "", today, timelineEntries(TimelineTabState(content, filter, grouping, "", today)))
    }
}
