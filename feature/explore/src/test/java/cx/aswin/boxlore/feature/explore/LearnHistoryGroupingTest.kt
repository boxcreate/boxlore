package cx.aswin.boxlore.feature.explore

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LearnHistoryGroupingTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, atMs: Long) = LearnHistoryEntry(
        id, "Episode $id", "Curious world", null, null, "1", null, 600, null, "Question $id?", null, 8, LearnHistoryAction.DISMISS, atMs,
    )

    private fun periods(now: String, locale: Locale, vararg times: Long) =
        groupLearnHistory(times.mapIndexed { index, time -> entry("$index", time) }, at(now), zone, locale)

    @Test
    fun `midnight separates today from yesterday`() {
        val groups = periods("2026-11-18T00:05", Locale.GERMANY, at("2026-11-18T00:01"), at("2026-11-17T23:59"), at("2026-11-17T00:00"))
        assertEquals(listOf(LearnHistoryPeriod.Today, LearnHistoryPeriod.Yesterday), groups.map { it.period })
        assertEquals(listOf("1", "2"), groups[1].entries.map { it.episodeId })
    }

    @Test
    fun `the week starts on the language's first day`() {
        // Wednesday 18 November 2026: Monday the 16th is this week in Germany, Sunday the 15th only in the US.
        val sunday = at("2026-11-15T12:00")
        val monday = at("2026-11-16T12:00")
        val german = periods("2026-11-18T12:00", Locale.GERMANY, monday, sunday)
        assertEquals(LearnHistoryPeriod.ThisWeek, german[0].period)
        assertEquals(LearnHistoryPeriod.Month(2026, 11, sunday), german[1].period)
        val american = periods("2026-11-18T12:00", Locale.US, monday, sunday)
        assertEquals(listOf(LearnHistoryPeriod.ThisWeek), american.map { it.period })
        assertEquals(2, american[0].entries.size)
    }

    @Test
    fun `older cards group by month, newest month first, and undated ones go last`() {
        val october = at("2026-10-02T09:00")
        val august = at("2026-08-30T09:00")
        val groups = periods("2026-11-18T12:00", Locale.FRANCE, 0L, august, at("2026-11-18T08:00"), october)
        assertEquals(
            listOf(LearnHistoryPeriod.Today, LearnHistoryPeriod.Month(2026, 10, october), LearnHistoryPeriod.Month(2026, 8, august), LearnHistoryPeriod.Earlier),
            groups.map { it.period },
        )
    }
}
