package cx.aswin.boxlore.feature.history.timeline

import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Timeline's content (boards A1 and A3): months, days, shows, filters, search and Jump back in. */
class HistoryTimelineLogicTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun everyDayIsListedNewestFirstWithItsTimeListened() {
        val content =
            build(
                listOf(row("a", day = 28, hour = 22), row("b", day = 28, hour = 20), row("c", day = 27, hour = 21)),
                listOf(listening("a", 28, minutes = 100), listening("b", 28, minutes = 68), listening("c", 27, minutes = 55)),
            )

        val october = content.months.single()
        assertEquals(YearMonth.of(2026, 10), october.month)
        assertEquals(3, october.episodeCount)
        assertEquals((100L + 68 + 55) * 60_000L, october.listenedMs)
        assertEquals(listOf(date(28), date(27)), october.days.map { it.date })
        assertEquals(listOf("a", "b"), october.days.first().episodes.map { it.item.episodeId })
        assertEquals(168L * 60_000L, october.days.first().listenedMs)
    }

    @Test
    fun aDayFromBeforeTimingBeganHasNoTime() {
        val content = build(listOf(row("old", day = 2, hour = 9)), emptyList())

        assertEquals(0L, content.months.single().days.single().listenedMs)
        assertTrue(date(2) in content.playedDays)
        assertEquals(date(2), content.firstListenDate)
    }

    @Test
    fun monthsAreNewestFirst() {
        val content = build(listOf(row("oct", day = 3, hour = 9), row("sep", month = 9, day = 30, hour = 9)), emptyList())

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), content.months.map { it.month })
    }

    @Test
    fun filtersKeepTheirEpisodesAndJumpBackInListsEveryUnfinishedOne() {
        val history =
            listOf(
                row("playing", day = 28, hour = 22, progress = 20, duration = 60),
                row("done", day = 28, hour = 21, completed = true, liked = true),
                row("unstarted", day = 27, hour = 9, progress = 0, duration = 60),
            )

        assertEquals(listOf("playing"), ids(build(history, emptyList(), TimelineFilter.IN_PROGRESS)))
        assertEquals(listOf("done"), ids(build(history, emptyList(), TimelineFilter.FINISHED)))
        assertEquals(listOf("done"), ids(build(history, emptyList(), TimelineFilter.LIKED)))
        val all = build(history, emptyList())
        assertEquals(listOf("playing"), all.jumpBackIn.map { it.item.episodeId })
        assertEquals(1, all.inProgressCount)
        assertEquals(40L * 60_000L, all.jumpBackIn.single().remainingMs)
    }

    @Test
    fun searchMatchesEveryWordInTheEpisodeOrShowTitle() {
        val history = listOf(row("a", day = 28, hour = 9, title = "Was Jack the Ripper a Freemason?", show = "Conspiracy Theories"), row("b", day = 28, hour = 8, title = "Ricky", show = "Darknet Diaries"))

        assertEquals(listOf("a"), ids(build(history, emptyList(), query = "ripper conspiracy")))
        assertEquals(listOf("b"), ids(build(history, emptyList(), query = "DARKNET")))
        assertTrue(build(history, emptyList(), query = "nothing like this").months.isEmpty())
    }

    @Test
    fun aShowGathersItsMonthWithFinishedFirstAndItsLatestUnfinishedToResume() {
        val history =
            listOf(
                row("ricky", day = 28, hour = 21, progress = 12, duration = 53, show = "Darknet Diaries", podcast = "dd"),
                row("diginotar", day = 28, hour = 20, completed = true, show = "Darknet Diaries", podcast = "dd"),
                row("courthouse", day = 28, hour = 19, progress = 5, duration = 57, show = "Darknet Diaries", podcast = "dd"),
                row("molasses", day = 26, hour = 18, completed = true, show = "History Class", podcast = "hc"),
            )

        val shows = build(history, listOf(listening("ricky", 28, 30, "dd"), listening("molasses", 26, 40, "hc"))).months.single().shows

        assertEquals(listOf("dd", "hc"), shows.map { it.podcastId })
        val darknet = shows.first()
        assertEquals(3, darknet.episodeCount)
        assertEquals(1, darknet.finishedCount)
        assertEquals(listOf(true, false, false), darknet.segments)
        assertEquals("ricky", darknet.resume?.item?.episodeId)
        assertEquals(30L * 60_000L, darknet.listenedMs)
        assertFalse(darknet.allFinished)
        assertTrue(shows.last().allFinished)
        assertNull(shows.last().resume)
    }

    @Test
    fun briefsAreCountedAsBriefs() {
        val shows = build(listOf(row("briefing_us_2026-10-28", day = 28, hour = 7, podcast = "briefing_us")), emptyList()).months.single().shows

        assertTrue(shows.single().isBrief)
    }

    @Test
    fun anEmptyHistoryIsEmpty() {
        val content = build(emptyList(), emptyList())

        assertTrue(content.isHistoryEmpty)
        assertNull(content.firstListenDate)
    }

    private fun ids(content: TimelineContent) = content.months.flatMap { month -> month.days.flatMap { day -> day.episodes.map { it.item.episodeId } } }

    private fun build(
        history: List<ListeningHistoryItem>,
        listening: List<ListeningDayEpisode>,
        filter: TimelineFilter = TimelineFilter.ALL,
        query: String = "",
    ) = HistoryTimelineLogic.build(HistoryTimelineLogic.Input(history, listening, filter, query, zone))

    private fun date(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    @Suppress("LongParameterList")
    private fun row(
        id: String,
        month: Int = 10,
        day: Int,
        hour: Int,
        progress: Long = 0,
        duration: Long = 60,
        completed: Boolean = false,
        liked: Boolean = false,
        title: String = "Episode $id",
        show: String = "Show",
        podcast: String = "show",
    ) = ListeningHistoryItem(
        episodeId = id,
        podcastId = podcast,
        episodeTitle = title,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example/$id.mp3",
        podcastName = show,
        progressMs = progress * 60_000L,
        durationMs = duration * 60_000L,
        isCompleted = completed,
        isLiked = liked,
        lastPlayedAt = LocalDateTime.of(2026, month, day, hour, 0).toInstant(zone).toEpochMilli(),
    )

    private fun listening(id: String, day: Int, minutes: Long, podcast: String = "show") =
        ListeningDayEpisode(date(day).toEpochDay(), id, podcast, minutes * 60_000L, 1, 0L)
}
