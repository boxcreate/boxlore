package cx.aswin.boxlore.feature.history.story

import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.R
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Echo's lines: which situation each card is in, which line it says, and the line arrays themselves. */
class StoryEchoLinesTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)
    private val october = YearMonth.of(2026, 10)

    @Test
    fun aSpecialSituationSpeaksOverTheRest() {
        // No listening timed before October: the first month on record, whatever the seed.
        val first = story(octoberMinutes = 120)
        (0..40).forEach { seed -> assertEquals(R.array.history_echo_hello_first_quiet, StoryEchoLines.lines(StoryCard.HELLO, first, seed)) }
        assertEquals(R.array.history_echo_time_first, StoryEchoLines.lines(StoryCard.TIME, first))
        assertEquals(R.array.history_echo_wrap_first, StoryEchoLines.lines(StoryCard.SHARE, first))
        // Listening before, but none in September: a gap.
        val gap = story(octoberMinutes = 600, augustMinutes = 60)
        assertEquals(R.array.history_echo_hello_gap, StoryEchoLines.lines(StoryCard.HELLO, gap))
        assertEquals(R.array.history_echo_time_gap, StoryEchoLines.lines(StoryCard.TIME, gap))
    }

    @Test
    fun otherwiseTheSeedPicksAmongTheSituationsThatFit() {
        // 10 h in October against 5 h on one September day: clearly more, and on more days.
        val story = story(octoberMinutes = 600, septemberMinutes = 300)
        val picked = (0 until 120).map { StoryEchoLines.lines(StoryCard.TIME, story, it) }.toSet()
        assertEquals(setOf(R.array.history_echo_time_up_large, R.array.history_echo_time_days_more), picked)
    }

    @Test
    fun theTimeCardTellsALargeChangeFromASmallOneAndSameFromEither() {
        fun picks(octoberMinutes: Long, septemberMinutes: Long) = (0 until 120).map { StoryEchoLines.lines(StoryCard.TIME, story(octoberMinutes, septemberMinutes, septemberDays = 10), it) }.toSet()
        assertEquals(setOf(R.array.history_echo_time_up_small), picks(620, 600))
        assertEquals(setOf(R.array.history_echo_time_down_large), picks(300, 600))
        assertEquals(setOf(R.array.history_echo_time_down_small), picks(580, 600))
        assertEquals(setOf(R.array.history_echo_time_same), picks(600, 600))
    }

    @Test
    fun theTopShowCardFindsItsGenreAndTies() {
        val crime = (0 until 120).map { StoryEchoLines.lines(StoryCard.TOP_SHOW, story(octoberMinutes = 600, otherShowMinutes = 500), it) }.toSet()
        assertTrue(R.array.history_echo_top_genre_true_crime in crime)
        assertFalse(R.array.history_echo_top_genre_history in crime)
        assertEquals(R.array.history_echo_top_tie, StoryEchoLines.lines(StoryCard.TOP_SHOW, story(octoberMinutes = 600, otherShowMinutes = 600)))
        assertEquals(R.array.history_echo_top_one_show, StoryEchoLines.lines(StoryCard.TOP_SHOW, story(octoberMinutes = 600)))
    }

    @Test
    fun recordsTellANewBestFromATie() {
        // Ten days in a row in October, after five in a row in August.
        assertEquals(R.array.history_echo_records_new_record, StoryEchoLines.lines(StoryCard.RECORDS, story(octoberMinutes = 600, augustMinutes = 50, augustDays = 5)))
        // Ten in a row in August too: a tie, not a new record.
        assertEquals(R.array.history_echo_records_tied_record, StoryEchoLines.lines(StoryCard.RECORDS, story(octoberMinutes = 600, augustMinutes = 100, augustDays = 10)))
    }

    @Test
    fun aLineWhoseFactIsMissingOrThatRunsLongGivesWay() {
        val lines = listOf("{days} days.", "A {show} line.", "Plain line.")
        val usual = listOf("Usual line.")
        // No days: the next line that fills.
        assertEquals("A Short line.", StoryEchoLines.choose(lines, usual, start = 0, mapOf("show" to "Short")))
        // A show name long enough to overflow the bubble: on to the next.
        assertEquals("Plain line.", StoryEchoLines.choose(lines, usual, start = 1, mapOf("show" to "x".repeat(80))))
        // Nothing in the situation fills: the card's usual lines.
        assertEquals("Usual line.", StoryEchoLines.choose(listOf("{days} days."), usual, start = 0, emptyMap()))
    }

    @Test
    fun placeholdersFillAndASeedLandsEachCardOnItsOwnLine() {
        assertEquals("10 days of listening. I took notes.", StoryEchoLines.fill("{days} days of listening. I took notes.", mapOf("days" to "10")))
        assertEquals("{unknown} stays", StoryEchoLines.fill("{unknown} stays", emptyMap()))
        val picked = StoryCard.entries.map { StoryEchoLines.index(12, seed = 5, card = it) }
        assertEquals(picked.size, picked.distinct().size)
        assertTrue(picked.all { it in 0 until 12 })
        assertEquals(0, StoryEchoLines.index(1, seed = -3, card = StoryCard.SHARE))
    }

    @Test
    fun everyLineArrayIsThereTwelveLongShortCalmAndUsesKnownPlaceholders() {
        val xml = File("src/main/res/values/story_echo_lines.xml").readText()
        val arrays = Regex("""<string-array name="(\w+)">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { match ->
            match.groupValues[1] to Regex("""<item>(.*?)</item>""").findAll(match.groupValues[2]).map { it.groupValues[1].replace("\\'", "'") }.toList()
        }
        // Every array the picker names exists, and every array is named (StoryEchoLines.kt names them all).
        val named = Regex("""R\.array\.(\w+)""").findAll(File("src/main/java/cx/aswin/boxlore/feature/history/story/StoryEchoLines.kt").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(named, arrays.keys)
        val known = setOf(
            "month", "previous", "days", "hours", "time", "diff", "show", "share", "finished", "episodes", "shows", "genre", "previousGenre",
            "output", "newShare", "lore", "smartQueue", "run", "best", "sitting", "count", "bigDay", "bigDayTime", "weekday",
        )
        // The usual lines are the last resort, so they only name facts every month the card shows has.
        val always = setOf("month", "previous", "time", "show", "share", "bigDay", "bigDayTime")
        val lines = arrays.values.flatten()
        assertEquals(lines.size, lines.distinct().size, "a line repeats")
        arrays.forEach { (name, items) ->
            assertEquals(12, items.size, name)
            items.forEach { line -> checkLine(name, line, if (name.endsWith("_usual")) always else known) }
        }
    }

    /** A line is short, calm, and names only [allowed] facts. */
    private fun checkLine(name: String, line: String, allowed: Set<String>) {
        assertTrue(line.length <= 60, "$name: $line")
        assertFalse('!' in line, "$name: $line")
        Regex("""\{(\w+)}""").findAll(line).forEach { assertTrue(it.groupValues[1] in allowed, "$name: ${it.value} isn't allowed") }
    }

    private fun story(
        octoberMinutes: Long,
        septemberMinutes: Long? = null,
        otherShowMinutes: Long = 0,
        septemberDays: Int = 1,
        augustMinutes: Long? = null,
        augustDays: Int = 1,
    ): MonthStory {
        val days = (1..10).map { day(october.atDay(it), "dd", octoberMinutes / 10) } +
            (if (otherShowMinutes > 0) listOf(day(october.atDay(12), "hc", otherShowMinutes)) else emptyList()) +
            (septemberMinutes?.let { minutes -> (1..septemberDays).map { day(october.minusMonths(1).atDay(it), "dd", minutes / septemberDays) } } ?: emptyList()) +
            (augustMinutes?.let { minutes -> (1..augustDays).map { day(october.minusMonths(2).atDay(it), "dd", minutes / augustDays) } } ?: emptyList())
        return StoryLogic.build(october, emptyList(), days, played, today, zone)!!
    }

    private val played = listOf(
        ListeningPlayedShow("dd", "Darknet Diaries", null, "True Crime", 0L, 0L),
        ListeningPlayedShow("hc", "History Class", null, "History", 0L, 0L),
    )

    private fun day(date: LocalDate, podcastId: String, minutes: Long) = ListeningDayEpisode(
        localDay = date.toEpochDay(),
        episodeId = "$podcastId-$date",
        podcastId = podcastId,
        consumedMs = minutes * 60_000L,
        sessionCount = 1,
        lastListenedAt = 0L,
        eveningMs = minutes * 60_000L,
    )
}
