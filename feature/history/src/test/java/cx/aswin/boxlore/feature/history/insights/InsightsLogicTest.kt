package cx.aswin.boxlore.feature.history.insights

import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Insights stories for a period (boards A4–A6). */
class InsightsLogicTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun timeListenedComparesWithThePeriodBeforeAndFindsTheBestRun() {
        val listening = (19..28).map { day(it, "dd", minutes = 30) } + day(2, "dd", minutes = 600, month = 9) + day(30, "ct", minutes = 23)
        val content = build(listening)

        assertEquals((10 * 30 + 23) * MINUTE, content.time.totalMs)
        assertEquals((10 * 30 + 23) * MINUTE / 30, content.time.perDayMs)
        assertEquals(600 * MINUTE, content.time.previousMs)
        assertEquals(10, content.time.bestRunDays)
        assertTrue(content.hasEnoughTiming)
    }

    @Test
    fun allTimeHasNoPeriodBefore() {
        val content = build((1..6).map { day(it, "dd", 10) }, period = ListeningPeriod.ALL)

        assertNull(content.time.previousMs)
        assertEquals(60 * MINUTE / 30, content.time.perDayMs)
    }

    @Test
    fun theListeningTypeComesFromTheBiggestPartOfTheDayAndTheFinishRate() {
        val listening = (20..25).map { day(it, "dd", 60, evening = 46, night = 22, afternoon = 18, morning = 14) }
        val history = (1..4).map { item("e$it", "dd", firstPlayed = 20, finished = it <= 3) }

        val type = build(listening, history).type!!

        assertEquals(TypeTime.EVENING, type.time)
        assertEquals(TypeHabit.FINISHER, type.habit)
        assertEquals(DayPart.EVENING, type.topPart)
        assertEquals(0.75f, type.finishRate!!, 0.001f)
        assertEquals(3 to 4, InsightsWording.nearestFraction(0.74f))
    }

    @Test
    fun anEvenDayIsAllDayAndRareFinishesSample() {
        val listening = (20..25).map { day(it, "dd", 60, evening = 25, night = 25, afternoon = 25, morning = 25) }
        val history = (1..4).map { item("e$it", "dd", firstPlayed = 20, finished = it == 1) }

        val type = build(listening, history).type!!

        assertEquals(TypeTime.ALL_DAY, type.time)
        assertEquals(TypeHabit.SAMPLER, type.habit)
    }

    @Test
    fun topShowsAreByTimeWithTheirShareAndFinishes() {
        val listening = (20..25).map { day(it, "dd", 40) } + (20..22).map { day(it, "ct", 20) }
        val history = listOf(item("a", "dd", firstPlayed = 21, finished = true), item("b", "ct", firstPlayed = 21, finished = false))

        val top = build(listening, history).topShows

        assertEquals(listOf("dd", "ct"), top.map { it.podcastId })
        assertEquals(0.8f, top.first().share, 0.001f)
        assertEquals(1, top.first().finishedCount)
    }

    @Test
    fun theBigDayIsTheWeekdayWithTheMostListening() {
        // Oct 28, 2026 is a Wednesday.
        val content = build((19..27).map { day(it, "dd", 10) } + day(28, "dd", 168))

        assertEquals(DayOfWeek.WEDNESDAY, content.weekdays!!.biggest)
    }

    @Test
    fun theMosaicShowsEachDaysMostPlayedShow() {
        val listening = listOf(day(28, "dd", 100), day(28, "ct", 30), day(3, "ct", 35))
        val played = listOf(show("dd", "https://img/dd.jpg", "True Crime"), show("ct", "https://img/ct.jpg", "History"))

        val mosaic = build(listening, played = played).mosaic

        val cells = mosaic.cells.filterNotNull().associateBy { it.date.dayOfMonth }
        assertEquals("https://img/dd.jpg", cells.getValue(28).imageUrl)
        assertEquals("https://img/ct.jpg", cells.getValue(3).imageUrl)
        assertFalse(cells.getValue(4).listened)
        assertEquals(2, mosaic.listenedDays)
        assertEquals(30, mosaic.daysSoFar)
        assertEquals(3, mosaic.cells.takeWhile { it == null }.size)
    }

    @Test
    fun recordsFindTheBiggestDayTheLongestRunTheLongestSittingAndMostInADay() {
        val listening =
            (19..28).map { day(it, "dd", 30) } + day(28, "dd", 138, episode = "ricky", longest = 64) + day(28, "dd", 10, episode = "courthouse")
        val history = listOf(item("ricky", "dd", title = "Ricky"))

        val records = build(listening, history, played = listOf(show("dd", null, "True Crime"))).records

        assertEquals(LocalDate.of(2026, 10, 28) to 178 * MINUTE, records.biggestDay)
        assertEquals(10, records.longestRun!!.first)
        assertEquals(64 * MINUTE to "Ricky", records.longestSitting)
        assertEquals(3, records.mostInOneDay!!.episodes)
    }

    @Test
    fun genresFoldBeyondFiveIntoOther() {
        val genres = listOf("True Crime", "Tech", "History", "Fiction", "Science", "News")
        val listening = genres.mapIndexed { index, _ -> day(20, "s$index", 60L - index * 5L) }
        val played = genres.mapIndexed { index, genre -> show("s$index", null, genre) }

        val shares = InsightsParts.genres(listening, played)

        assertEquals(listOf("True Crime", "Tech", "History", "Fiction", null), shares.map { it.genre })
        assertEquals(1f, shares.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun allStartsTodayWhenTheFirstTimedDayIsLaterThanToday() {
        // A clock change, or a phone in a timezone ahead: listening dated tomorrow.
        val content = build(listOf(day(31, "a", 40), day(30, "a", 20)), period = ListeningPeriod.ALL)

        assertEquals(20 * MINUTE, content.time.totalMs)
        assertEquals(20 * MINUTE, content.time.perDayMs)
    }

    @Test
    fun earlyPlayedDaysCountEveryTimedDayOfAnEpisodeHeardOverSeveral() {
        // One episode heard on the 22nd, 23rd and 24th, last played on the 24th.
        val content = build(listOf(day(22, "a", 5, episode = "e"), day(23, "a", 5, episode = "e"), day(24, "a", 5, episode = "e")), listOf(item("e", "a", playedDay = 24)))

        assertEquals(3, content.early.playedDaysThisMonth)
    }

    @Test
    fun milestonesStepUp() {
        assertEquals(200, InsightsParts.milestone(178).next)
        assertEquals(10, InsightsParts.milestone(0).next)
        assertEquals(1_500, InsightsParts.milestone(1_000).next)
    }

    @Test
    fun withFewTimedDaysHistoryTellsTheStory() {
        val history = (1..4).map { item("e$it", "brief", finished = it <= 3, playedDay = 20 + it) } + item("x", "dd", playedDay = 28)
        val content = build(listOf(day(22, "brief", 5), day(23, "brief", 5)), history)

        assertFalse(content.hasEnoughTiming)
        assertEquals(3, content.early.finished)
        assertEquals(LocalDate.of(2026, 10, 22), content.early.timingSince)
        assertEquals(3, content.early.timedDaysNeeded)
        // Episodes from Oct 21 but listening time only from Oct 22: Insights are limited for now.
        assertTrue(content.early.historyBeforeTiming)
        assertEquals("brief", content.early.showsByEpisodes.first().podcastId)
        assertEquals(5, content.early.playedDaysThisMonth)
    }

    @Test
    fun insightsAreLimitedOnlyWhenTheHistoryGoesBackBeforeTheListeningTime() {
        val since22 = listOf(day(22, "dd", 5))

        assertFalse(build(since22, listOf(item("a", "dd", playedDay = 22), item("b", "dd", playedDay = 25))).early.historyBeforeTiming)
        assertTrue(build(emptyList(), listOf(item("a", "dd", playedDay = 25))).early.historyBeforeTiming)
        assertFalse(build(emptyList()).early.historyBeforeTiming)
        // A first play recorded before timing began counts, even when the last play came after.
        assertTrue(build(since22, listOf(item("a", "dd", firstPlayed = 20, playedDay = 25))).early.historyBeforeTiming)
    }

    @Test
    fun staleInProgressEpisodesAreCounted() {
        val history = listOf(item("old", "dd", playedDay = 1, month = 9), item("new", "dd", playedDay = 29))

        val inProgress = build(emptyList(), history).inProgress

        assertEquals(2, inProgress.count)
        assertEquals(1, inProgress.stale)
    }

    private fun build(
        listening: List<ListeningDayEpisode>,
        history: List<ListeningHistoryItem> = emptyList(),
        played: List<ListeningPlayedShow> = emptyList(),
        period: ListeningPeriod = ListeningPeriod.DAYS_30,
    ) = InsightsLogic.build(InsightsLogic.Input(history, listening, played, period, today, zone))

    @Suppress("LongParameterList")
    private fun day(
        dayOfMonth: Int,
        podcastId: String,
        minutes: Long,
        month: Int = 10,
        episode: String = "$podcastId-$dayOfMonth",
        evening: Long = 0,
        night: Long = 0,
        afternoon: Long = 0,
        morning: Long = 0,
        longest: Long = 0,
    ) = ListeningDayEpisode(
        localDay = LocalDate.of(2026, month, dayOfMonth).toEpochDay(),
        episodeId = episode,
        podcastId = podcastId,
        consumedMs = minutes * MINUTE,
        sessionCount = 1,
        lastListenedAt = 0L,
        morningMs = morning * MINUTE,
        afternoonMs = afternoon * MINUTE,
        eveningMs = evening * MINUTE,
        nightMs = night * MINUTE,
        longestSessionMs = longest * MINUTE,
    )

    @Suppress("LongParameterList")
    private fun item(
        id: String,
        podcastId: String,
        firstPlayed: Int? = null,
        finished: Boolean = false,
        title: String = "Episode $id",
        playedDay: Int = 25,
        month: Int = 10,
    ): ListeningHistoryItem {
        val playedAt = LocalDateTime.of(2026, month, playedDay, 20, 0).toInstant(zone).toEpochMilli()
        val first = firstPlayed?.let { LocalDateTime.of(2026, 10, it, 20, 0).toInstant(zone).toEpochMilli() }
        return ListeningHistoryItem(
            episodeId = id,
            podcastId = podcastId,
            episodeTitle = title,
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = null,
            podcastName = "Show $podcastId",
            progressMs = if (finished) 0L else 60_000L,
            durationMs = 600_000L,
            isCompleted = finished,
            isLiked = false,
            lastPlayedAt = playedAt,
            firstPlayedAt = first,
            finishedAt = if (finished) first?.plus(3_600_000L) ?: playedAt else null,
        )
    }

    private fun show(podcastId: String, image: String?, genre: String?) = ListeningPlayedShow(podcastId, "Show $podcastId", image, genre, 0L, 0L)

    private companion object {
        const val MINUTE = 60_000L
    }
}
