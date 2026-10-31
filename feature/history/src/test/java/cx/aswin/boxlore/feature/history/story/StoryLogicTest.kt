package cx.aswin.boxlore.feature.history.story

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import cx.aswin.boxlore.core.designsystem.echo.EchoAccents
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacterSpec
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoMotionClip
import cx.aswin.boxlore.core.designsystem.echo.EchoReach
import cx.aswin.boxlore.core.designsystem.echo.EchoScreenTrack
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.insights.TypeHabit
import cx.aswin.boxlore.feature.history.insights.TypeTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The monthly story (boards A7–A12): which months have one, what each card says, its colours and where Echo stands. */
class StoryLogicTest {
    private val zone = ZoneOffset.UTC
    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun everyEarlierMonthWithTimedListeningHasAStoryNewestFirst() {
        val listening = listOf(day(10, 3, "dd", 30), day(9, 12, "hc", 20), day(11, 1, "dd", 15), day(7, 4, "hc", 0))
        val months = StoryLogic.months(listening, played, today)

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), months.map { it.month })
        assertEquals(EchoOutfit.TRUE_CRIME, months.first().outfit)
        assertEquals(EchoOutfit.HISTORY, months.last().outfit)
    }

    @Test
    fun theStoryTellsTheMonthsTimeDaysShowsAndBiggestDay() {
        // Oct 28, 2026 is a Wednesday.
        val listening = (19..27).map { day(10, it, "dd", 30) } + day(10, 28, "dd", 120) + day(10, 28, "ct", 48) + day(9, 5, "dd", 600)
        val story = build(listening)

        assertEquals((9 * 30 + 168) * MINUTE, story.totalMs)
        assertEquals(10, story.listenedDays)
        assertEquals(2, story.shows)
        assertEquals(DayOfWeek.WEDNESDAY, story.busiestWeekday)
        assertEquals(LocalDate.of(2026, 10, 28) to 168 * MINUTE, story.biggestDay)
        assertEquals(31, story.perDay.size)
        assertEquals(0L, story.perDay[0])
        assertEquals(story.totalMs / 31, story.perDayAverageMs)
        assertEquals(600 * MINUTE, story.previousMonthMs)
        assertEquals(10, story.bestRun!!.first)
        assertTrue(story.bestRunIsRecord)
    }

    @Test
    fun withoutTimedListeningBeforeTheMonthThereIsNoComparison() {
        assertNull(build(listOf(day(10, 3, "dd", 30))).previousMonthMs)
    }

    @Test
    fun aRunShorterThanAnEarlierOneIsNotARecord() {
        val listening = (1..12).map { day(9, it, "dd", 10) } + (19..21).map { day(10, it, "dd", 10) }
        val story = build(listening)

        assertEquals(3, story.bestRun!!.first)
        assertFalse(story.bestRunIsRecord)
    }

    @Test
    fun aRunThatOnlyTiesAnEarlierBestIsNotARecord() {
        // Three days in September, three in October: the same length, so October doesn't claim a new best.
        val listening = (1..3).map { day(9, it, "dd", 10) } + (19..21).map { day(10, it, "dd", 10) }
        val story = build(listening)

        assertEquals(3, story.bestRun!!.first)
        assertEquals(3, story.bestRunEver)
        assertFalse(story.bestRunIsRecord)
    }

    @Test
    fun aRunCrossingIntoTheMonthCountsAgainstTheMonthsOwnRun() {
        // Sep 25 to Oct 5 is 11 days; October's own longest run, Oct 20 to 27, is 8: not a record.
        val listening = (25..30).map { day(9, it, "dd", 10) } + (1..5).map { day(10, it, "dd", 10) } + (20..27).map { day(10, it, "dd", 10) }
        val story = build(listening)

        assertEquals(8, story.bestRun!!.first)
        assertFalse(story.bestRunIsRecord)
    }

    @Test
    fun theThisMonthsDaysStillToComeAreLeftEmpty() {
        val story = StoryLogic.build(october, emptyList(), listOf(day(10, 3, "dd", 30)), played, LocalDate.of(2026, 10, 30), zone)!!

        assertNull(story.perDay[30])
        assertEquals(0L, story.perDay[29])
    }

    @Test
    fun theTopShowCarriesItsShareFinishesAndTheEpisodeToResume() {
        val listening = (20..23).map { day(10, it, "dd", 30) } + day(10, 24, "hc", 40)
        val history = listOf(item("a", "dd", finished = true, day = 21), item("b", "dd", finished = false, day = 23), item("c", "hc", finished = false, day = 24))
        val top = StoryLogic.build(october, history, listening, played, today, zone)!!.topShow!!

        assertEquals("dd", top.podcastId)
        // The name the history shows for it.
        assertEquals("Show dd", top.name)
        assertEquals(0.75f, top.share, 0.001f)
        assertEquals(1, top.finished)
        assertEquals("b", top.resume!!.episodeId)
        assertEquals(540_000L, top.resumeRemainingMs)
    }

    @Test
    fun echoDressesForTheTopShowsGenreAndTheTimeOfDay() {
        val story = build((20..23).map { day(10, it, "hc", 30) } + day(10, 24, "dd", 20))

        assertEquals(EchoOutfit.HISTORY, story.showOutfit)
        assertEquals(EchoOutfit.TRUE_CRIME, StoryLogic.genreOutfit("True Crime"))
        assertEquals(EchoOutfit.TECH, StoryLogic.genreOutfit("Technology"))
        assertEquals(EchoOutfit.HEALTH, StoryLogic.genreOutfit("Health & Fitness"))
        assertEquals(EchoOutfit.HELLO, StoryLogic.genreOutfit(null))
        assertEquals(EchoOutfit.MORNING, StoryLogic.timeOutfit(TypeTime.MORNING))
        assertEquals(EchoOutfit.DAYTIME, StoryLogic.timeOutfit(TypeTime.DAYTIME))
        assertEquals(EchoOutfit.NIGHT, StoryLogic.timeOutfit(TypeTime.ALL_DAY))
        assertEquals("story-history-still", StoryLogic.tileSequence(EchoOutfit.HISTORY))
    }

    @Test
    fun cardsWithoutTheirDataAreLeftOut() {
        // How you listened needs the listening's details (where it was heard), recorded since Room v40.
        val detailed = build((20..23).map { day(10, it, "dd", 30, evening = 30).copy(bluetoothMs = 30 * MINUTE) })
        assertEquals(StoryCard.entries, StoryLogic.cards(detailed))

        val timed = build((20..23).map { day(10, it, "dd", 30, evening = 30) })
        assertEquals(StoryCard.entries - StoryCard.HOW, StoryLogic.cards(timed))

        val untimed = build((20..23).map { day(10, it, "dd", 30) })
        assertEquals(StoryCard.entries - StoryCard.HOW - StoryCard.TYPE, StoryLogic.cards(untimed))
        assertEquals(StoryCard.entries - StoryCard.HOW - StoryCard.TYPE - StoryCard.TOP_SHOW, StoryLogic.cards(untimed.copy(topShow = null)))
    }

    @Test
    fun onBoxloresOwnColoursTheStoryIsExactlyTheBoards() {
        val dark = StoryPalette.forTheme(0xFF9C8CFF.toInt(), 0xFF89C6B5.toInt(), dark = true)
        assertEquals(Color(0xFF3F347B), dark.container)
        assertEquals(Color(0xFF5145D8), dark.vivid)
        assertEquals(Color(0xFF274C41), dark.green)
        assertTrue(dark.ownHue)
        assertEquals(EchoAccents.Original, dark.echoOwn)

        val light = StoryPalette.forTheme(0xFF5145D8.toInt(), 0xFF397B6D.toInt(), dark = false)
        assertEquals(dark, light)
    }

    @Test
    fun onAnotherThemeTheStoryTakesItsHueAndKeepsEachTone() {
        val board = StoryPalette.forTheme(0xFF9C8CFF.toInt(), 0xFF89C6B5.toInt(), dark = true)
        val orange = StoryPalette.forTheme(0xFFFFB68C.toInt(), 0xFFC9C67F.toInt(), dark = true)

        assertNotEquals(board.container, orange.container)
        assertFalse(orange.ownHue)
        assertTrue(orange.echoOwn is EchoAccents.Custom)
        // Each colour keeps its tone: the dark container stays dark, its text light.
        assertEquals(Hct.fromInt(0xFF3F347B.toInt()).tone, Hct.fromInt(orange.container.toArgb()).tone, 1.0)
        assertEquals(Hct.fromInt(0xFFECE7FF.toInt()).tone, Hct.fromInt(orange.onContainer.toArgb()).tone, 1.0)
        assertEquals(orange.vivid.toArgb(), (orange.echoOwn as EchoAccents.Custom).parts)
    }

    @Test
    fun echoFillsTheBoardsBoxWithItsOutlineAndMargin() {
        val spec = EchoCharacterSpec(
            screens = emptyMap(),
            sequences = emptyMap(),
            clips = mapOf("wave" to clip("wave", EchoReach(0.3f, 0.3f, 0.7f, 0.9f))),
            reaches = mapOf("story-trophy-rest" to EchoReach(0.2f, 0.1f, 0.8f, 0.9f)),
        )
        // A rest pose: its 0.8-tall outline plus 3% of it above and below fills the 230dp box.
        val trophy = StoryEchoStage.square(spec, "story-trophy", StoryEchoBox(224f, 562f, 230f))!!
        val side = 230f / (0.8f * 1.06f)
        assertEquals(side, trophy.side, 0.01f)
        assertEquals(562f - (0.1f - 0.024f) * side, trophy.top, 0.01f)
        assertEquals(224f - (0.2f - 0.024f) * side, trophy.left, 0.01f)

        // The hello wave fills its box with all the wave clip reaches.
        val hello = StoryEchoStage.square(spec, "story-hello", StoryEchoBox(6f, 560f, 300f))!!
        assertEquals(500f, hello.side, 0.01f)
        assertEquals(560f - 150f, hello.top, 0.01f)

        // Centred on the biggest day's bar.
        val centred = StoryEchoStage.square(spec, "story-trophy", StoryEchoBox(0f, 394f, 210f, centerX = 300f))!!
        assertEquals(300f, centred.left + 0.5f * centred.side, 0.01f)
    }

    @Test
    fun echoStandsOnTheMiddleOfTheBiggestDaysBar() {
        // 31 days: 31 bars share 368dp less 30 gaps.
        val unit = (368f - 30 * 4f) / 31f
        assertEquals(16f + unit / 2f, StoryTimeChart.biggestCenter(31, 0), 0.01f)
        assertEquals(16f + 27 * (unit + 4f) + unit / 2f, StoryTimeChart.biggestCenter(31, 27), 0.01f)
    }

    @Test
    fun theStoryCarriesItsCountsRunnersUpDetailsAndRecords() {
        val listening = (1..10).map { day(10, it, "dd", 60, evening = 60).copy(longestSessionMs = 40 * MINUTE, carMs = 60 * MINUTE, newReleaseMs = 20 * MINUTE, loreMs = 15 * MINUTE) } +
            listOf(day(10, 12, "ct", 30, evening = 30).copy(longestSessionMs = 90 * MINUTE, headphonesMs = 30 * MINUTE), day(10, 13, "hc", 20, evening = 20).copy(speakerMs = 20 * MINUTE)) +
            (20..23).map { day(9, it, "dd", 30, evening = 30) }
        val story = build(listening)

        assertEquals(12, story.counts.episodes)
        assertEquals(listOf("ct", "hc"), story.runnersUp.map { it.podcastId })
        assertEquals(30 * MINUTE, story.runnersUp.first().listenedMs)
        assertEquals(90 * MINUTE to "Conspiracy Theories", story.longestSitting)
        val how = story.how!!
        assertEquals(cx.aswin.boxlore.feature.history.insights.ListeningOutput.CAR, how.outputs.first().output)
        assertEquals(15f / 65f, how.sources.loreShare, 0.001f)
        // Oct 1 to 10 is the month's run and the longest so far (September's was four days).
        assertEquals(10, story.bestRunEver)
        assertEquals(java.time.DayOfWeek.THURSDAY, story.bigWeekday!!.first)
    }

    @Test
    fun howYouListenedLeavesNewOrCatchingUpOutUntilEnoughOfTheMonthHasAReleaseDate() {
        val undated = (1..10).map { day(10, it, "dd", 60, evening = 60).copy(carMs = 60 * MINUTE, newReleaseMs = 20 * MINUTE, loreMs = 15 * MINUTE) }

        // Where and what started it still show; new or older doesn't, as none of the month's release dates are known.
        val how = build(undated).how!!
        assertEquals(cx.aswin.boxlore.feature.history.insights.ListeningOutput.CAR, how.outputs.first().output)
        assertEquals(0.25f, how.sources.loreShare, 0.001f)
        assertNull(how.sources.newShare)

        // With 40 of each hour dated, new is a share of those 40 minutes.
        assertEquals(0.5f, build(undated.map { it.copy(releaseKnownMs = 40 * MINUTE) }).how!!.sources.newShare!!, 0.001f)
    }

    @Test
    fun theEntryCardCountsHowYouListenedOnlyWhenThatCardIsMade() {
        // Every minute has a known release date but no output: details, yet nothing to say where it was heard.
        val listening = (20..23).map { day(10, it, "dd", 60, evening = 60).copy(newReleaseMs = 30 * MINUTE, releaseKnownMs = 60 * MINUTE) }
        val month = StoryLogic.months(listening, played, today).single()

        assertEquals(StoryCard.entries.size - 1, month.cards)
        assertEquals(StoryLogic.cards(build(listening)).size, month.cards)
    }

    @Test
    fun theEntryCardsTeaserHasTheTopShowItsCoversAndHowManyCards() {
        val listening = (20..23).map { day(10, it, "dd", 60, evening = 60).copy(bluetoothMs = 60 * MINUTE) } + day(10, 24, "ct", 20, evening = 20).copy(bluetoothMs = 20 * MINUTE)
        val month = StoryLogic.months(listening, played, today).single()

        assertEquals("Darknet Diaries", month.topShowName)
        assertEquals(2, month.covers.size)
        assertEquals(StoryCard.entries.size, month.cards)
    }

    private fun build(listening: List<ListeningDayEpisode>) = StoryLogic.build(october, emptyList(), listening, played, today, zone)!!

    private val played = listOf(
        ListeningPlayedShow("dd", "Darknet Diaries", null, "True Crime", 0L, 0L),
        ListeningPlayedShow("ct", "Conspiracy Theories", null, "True Crime", 0L, 0L),
        ListeningPlayedShow("hc", "History Class", null, "History", 0L, 0L),
    )

    private fun day(month: Int, dayOfMonth: Int, podcastId: String, minutes: Long, evening: Long = 0) = ListeningDayEpisode(
        localDay = LocalDate.of(2026, month, dayOfMonth).toEpochDay(),
        episodeId = "$podcastId-$month-$dayOfMonth",
        podcastId = podcastId,
        consumedMs = minutes * MINUTE,
        sessionCount = 1,
        lastListenedAt = 0L,
        eveningMs = evening * MINUTE,
    )

    private fun item(id: String, podcastId: String, finished: Boolean, day: Int) = ListeningHistoryItem(
        episodeId = id,
        podcastId = podcastId,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show $podcastId",
        progressMs = if (finished) 0L else 60_000L,
        durationMs = 600_000L,
        isCompleted = finished,
        isLiked = false,
        lastPlayedAt = LocalDateTime.of(2026, 10, day, 20, 0).toInstant(zone).toEpochMilli(),
    )

    private fun clip(id: String, reach: EchoReach) = EchoMotionClip(id, "clips/$id.mp4", 24, 1, 4, EchoFace.Idle, "rest", listOf(EchoScreenTrack(0.5f, 0.4f, 0.2f, 0.2f, 0f)), reach = reach)

    @Test
    fun theTypeCardNeedsHalfAnHourTimedAndAHabitNeedsThreeEpisodes() {
        // 20 minutes timed by part of the day: too little to call a listening type.
        assertFalse(StoryCard.TYPE in StoryLogic.cards(build(listOf(day(10, 3, "dd", 20, evening = 20)))))
        // An hour of evenings over one unfinished episode: an evening listener, not yet a sampler.
        val story = StoryLogic.build(october, listOf(item("dd-10-3", "dd", finished = false, day = 3)), listOf(day(10, 3, "dd", 60, evening = 60)), played, today, zone)!!
        assertTrue(StoryCard.TYPE in StoryLogic.cards(story))
        assertEquals(TypeHabit.LISTENER, story.type!!.habit)
    }

    @Test
    fun earlierStoriesFoldToTwoRowsUntilOpened() {
        val months = (1..8).map { StoryMonth(YearMonth.of(2026, 1).plusMonths(it.toLong()), it * MINUTE, EchoOutfit.HELLO) }
        assertTrue(EarlierStories.folds(months))
        assertEquals(months.take(6), EarlierStories.shown(months, expanded = false))
        assertEquals(months, EarlierStories.shown(months, expanded = true))
        // Two rows or fewer: everything shows and there's no button.
        assertFalse(EarlierStories.folds(months.take(6)))
        assertEquals(months.take(6), EarlierStories.shown(months.take(6), expanded = false))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
