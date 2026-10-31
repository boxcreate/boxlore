package cx.aswin.boxlore.feature.history.timeline

import cx.aswin.boxlore.core.model.ListeningHistoryItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Jump to a day's calendar (A2), the fast scroller, the Timeline's words for time and its list entries. */
class TimelinePiecesTest {
    @Test
    fun calendarShadesFollowTheBoardsDays() {
        assertEquals(0, HistoryCalendarLogic.level(0L, played = false))
        assertEquals(1, HistoryCalendarLogic.level(0L, played = true))
        assertEquals(1, HistoryCalendarLogic.level(30 * MINUTE, played = true))
        assertEquals(2, HistoryCalendarLogic.level(35 * MINUTE, played = true))
        assertEquals(2, HistoryCalendarLogic.level(55 * MINUTE, played = true))
        assertEquals(3, HistoryCalendarLogic.level(60 * MINUTE, played = true))
        assertEquals(3, HistoryCalendarLogic.level(168 * MINUTE, played = true))
    }

    @Test
    fun october2026StartsOnThursdayInAMondayFirstWeek() {
        val cells = HistoryCalendarLogic.cells(YearMonth.of(2026, 10))

        assertEquals(3, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 10, 1), cells[3])
        assertEquals(34, cells.size)
    }

    @Test
    fun theCalendarGoesBackToTheFirstListen() {
        val current = YearMonth.of(2026, 10)

        assertEquals(YearMonth.of(2026, 3), HistoryCalendarLogic.earliestMonth(LocalDate.of(2026, 3, 9), current))
        assertEquals(current, HistoryCalendarLogic.earliestMonth(null, current))
    }

    @Test
    fun theThumbFollowsTheListAndADragReachesEveryItem() {
        assertEquals(0f, TimelineScrollerLogic.fraction(0, 100, 10))
        assertEquals(0.5f, TimelineScrollerLogic.fraction(45, 100, 10))
        assertEquals(1f, TimelineScrollerLogic.fraction(95, 100, 10))
        assertEquals(0, TimelineScrollerLogic.indexAt(0f, 100, 10))
        assertEquals(90, TimelineScrollerLogic.indexAt(1f, 100, 10))
        assertFalse(TimelineScrollerLogic.isLongEnough(20, 10))
        assertTrue(TimelineScrollerLogic.isLongEnough(30, 10))
    }

    @Test
    fun lengthsReadInWholeMinutesAndDaysByName() {
        assertNull(TimelineTime.hoursMinutes(59_000L))
        assertEquals(0L to 23L, TimelineTime.hoursMinutes(23 * MINUTE + 59_000L))
        assertEquals(2L to 48L, TimelineTime.hoursMinutes(168 * MINUTE))
        val today = LocalDate.of(2026, 10, 30)
        assertEquals(TimelineTime.DayTitle.Today, TimelineTime.dayTitleParts(today, today))
        assertEquals(TimelineTime.DayTitle.Yesterday, TimelineTime.dayTitleParts(today.minusDays(1), today))
        assertEquals("Wednesday, Oct 28", TimelineTime.dayTitleText(LocalDate.of(2026, 10, 28), false, Locale.US))
        assertEquals("October", TimelineTime.monthTitle(YearMonth.of(2026, 10), today, Locale.US))
        assertEquals("December 2025", TimelineTime.monthTitle(YearMonth.of(2025, 12), today, Locale.US))
        val india = ZoneOffset.ofHoursMinutes(5, 30)
        val evening = LocalDateTime.of(2026, 10, 30, 20, 12).toInstant(india).toEpochMilli()
        assertEquals("8:12 pm", TimelineTime.clockTime(evening, india, false, Locale.US))
        assertEquals("20:12", TimelineTime.clockTime(evening, india, true, Locale.US))
    }

    @Test
    fun aJumpLandsOnItsDayElseTheNearestLaterDayWhereItsEpisodesWereLastPlayed() {
        val day = { date: LocalDate -> TimelineDay(date, 0L, listOf(HistoryTimelineLogic.episodeOf(item("e$date")))) }
        val month =
            TimelineMonth(YearMonth.of(2026, 10), 3, 0L, listOf(day(LocalDate.of(2026, 10, 28)), day(LocalDate.of(2026, 10, 20)), day(LocalDate.of(2026, 10, 3))), emptyList())
        val content = TimelineContent(emptyList(), 0, listOf(month), emptyMap(), emptySet(), LocalDate.of(2026, 10, 3), false)
        val entries = timelineEntries(TimelineTabState(content, TimelineFilter.ALL, TimelineGrouping.DAYS, "", LocalDate.of(2026, 10, 30)))

        val landed = { target: LocalDate -> (entries[timelineIndexFor(entries, target)!!] as? TimelineEntry.Day)?.day?.date }
        assertEquals(LocalDate.of(2026, 10, 20), landed(LocalDate.of(2026, 10, 20)))
        // Listened on the 23rd but last played on the 28th: that's where the episode is.
        assertEquals(LocalDate.of(2026, 10, 28), landed(LocalDate.of(2026, 10, 23)))
        assertEquals(LocalDate.of(2026, 10, 3), landed(LocalDate.of(2026, 10, 1)))
        // After the newest day: the newest day.
        assertEquals(LocalDate.of(2026, 10, 28), landed(LocalDate.of(2026, 10, 30)))
        assertEquals(entries.map { it.key }.distinct(), entries.map { it.key })
    }

    private fun item(id: String) = ListeningHistoryItem(id, "show", "Episode", null, null, null, "Show", 0L, 0L, false, false, 0L)

    private companion object {
        const val MINUTE = 60_000L
    }
}
