package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.history.HistoryScreen
import cx.aswin.boxlore.feature.history.HistoryTab
import cx.aswin.boxlore.feature.history.HistoryViewModel
import cx.aswin.boxlore.feature.history.InsightsUiState
import cx.aswin.boxlore.feature.history.TimelineUiState
import cx.aswin.boxlore.feature.history.story.StoryCard
import cx.aswin.boxlore.feature.history.story.StoryLogic
import cx.aswin.boxlore.feature.history.story.StoryPlayer
import cx.aswin.boxlore.feature.history.timeline.JumpToDayContent
import cx.aswin.boxlore.feature.history.timeline.JumpToDayHandle
import cx.aswin.boxlore.feature.history.timeline.JumpToDayState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Listening history captures for side-by-side review with the design boards (run with
 * `-PtabletCaptures=after`): the Timeline by day and by show, Jump to a day, the empty screen,
 * Insights, and each card of October's story, at the boards' 400dp phone width and at tablet sizes.
 * The data is the boards' own (Oct 30, 2026), with the three months before for the stories.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryTabletAuditCaptureTest(private val view: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        assumeTrue(System.getProperty("boxlore.tabletCaptures") == "after")
        val port = BoardHistory(empty = view == "empty", timedDays = if (view == "insights-early") 2 else Int.MAX_VALUE)
        val vm = HistoryViewModel(port, zoneId = { ZoneId.systemDefault() }, now = { TODAY })
        fixture.prepare()
        if (view.startsWith("story-")) {
            captureStory(port, StoryCard.valueOf(view.removePrefix("story-").uppercase()))
            return
        }
        if (view == "jump") {
            // The sheet has its own window, which offline captures don't draw: render its content on the sheet's surface.
            fixture.setContent(foldSafeViewport = true) {
                BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                JumpToDayHandle()
                                JumpToDayContent(JumpToDayState(BOARD_CALENDAR, BOARD_CALENDAR.keys, LocalDate.of(2026, 3, 9), TODAY), {}, {})
                            }
                        }
                    }
                }
            }
            fixture.capture("history-$view")
            return
        }
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) { HistoryScreen(vm, {}, {}, {}) }
        }
        vm.selectTab(if (view.startsWith("insights")) HistoryTab.INSIGHTS else HistoryTab.TIMELINE)
        // The tabs work off the main thread: let the paused clock run until the open one is ready.
        repeat(50) {
            if ((if (view.startsWith("insights")) vm.insightsState.value is InsightsUiState.Ready else vm.timelineState.value is TimelineUiState.Ready)) return@repeat
            fixture.compose.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
            fixture.compose.waitForIdle()
        }
        val ready = if (view.startsWith("insights")) vm.insightsState.value is InsightsUiState.Ready else vm.timelineState.value is TimelineUiState.Ready
        check(ready) { "The $view tab was still loading: a capture of it would show the loader" }
        if (view == "shows") {
            fixture.compose.mainClock.advanceTimeBy(1_000)
            fixture.compose.onNodeWithText("Shows").performClick()
        }
        fixture.capture("history-$view")
    }

    /** October's story as it shows on Nov 2, at [card], once the card's entrance has settled (Echo at rest). */
    private fun captureStory(port: BoardHistory, card: StoryCard) {
        val story = requireNotNull(port.story(YearMonth.of(2026, 10), LocalDate.of(2026, 11, 2)))
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) { StoryPlayer(story, card, {}, {}, {}) }
        }
        repeat(40) {
            fixture.compose.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
        }
        fixture.compose.waitForIdle()
        fixture.capture("history-story-${card.name.lowercase()}")
    }

    companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 30)

        private val STORY_CARDS = StoryCard.entries.map { "story-" + it.name.lowercase() }

        /** Board A2's October: listening minutes per day. */
        val BOARD_CALENDAR: Map<LocalDate, Long> =
            mapOf(3 to 35, 6 to 20, 8 to 44, 11 to 30, 14 to 50, 15 to 25, 16 to 40, 17 to 40, 19 to 30, 20 to 45, 21 to 45, 22 to 35, 23 to 25, 24 to 60, 25 to 50, 26 to 40, 27 to 55, 28 to 168, 30 to 23)
                .entries.associate { (day, minutes) -> LocalDate.of(2026, 10, day) to minutes * 60_000L }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> = listOf("days", "shows", "jump", "empty", "insights", "insights-early").flatMap { view ->
            listOf(400 to (if (view == "insights") 3_600 else 1_640), 400 to 890, 1280 to 800, 840 to 1280).flatMap { (width, height) ->
                listOf(false, true).map { dark -> arrayOf<Any>(view, PhoneGoldenConfiguration(width, height, dark)) }
            }
        } + STORY_CARDS.flatMap { view ->
            // The story is its own full-screen frame: the boards' size, a tablet, and a wide fold-out.
            listOf(400 to 890, 1280 to 800, 840 to 1280).map { (width, height) -> arrayOf<Any>(view, PhoneGoldenConfiguration(width, height, true)) }
        } + listOf(arrayOf<Any>("story-type", PhoneGoldenConfiguration(400, 890, false)))
    }
}

/** The boards' history: Oct 26 to Oct 30, 2026, with their shows, times and progress. */
internal class BoardHistory(empty: Boolean, timedDays: Int = Int.MAX_VALUE) : ListeningHistoryPort {
    private val history =
        if (empty) {
            emptyList()
        } else {
            listOf(
                row("ripper", "Was Jack the Ripper a Freemason?", "Conspiracy Theories", "ct", 30, 20, 12, progress = 24, duration = 53),
                row("briefing_us_2026-10-28", "Cyclone Evacuations And Airport IT Failures", "The boxlore brief", "briefing_us", 28, 22, 52, progress = 2, duration = 8),
                row("courthouse", "The Courthouse - Revisited", "Darknet Diaries", "dd", 28, 22, 15, progress = 6, duration = 58),
                row("ricky", "Ricky", "Darknet Diaries", "dd", 28, 21, 20, progress = 16, duration = 57),
                row("diginotar", "DigiNotar, You are the Weakest Link, Good Bye!", "Darknet Diaries", "dd", 28, 20, 5, completed = true, liked = true),
                row("ai", "Inside the early days of the AI revolution", "The Vergecast", "vc", 27, 21, 10, completed = true),
                row("sign", "The Sign of Four: Part One", "Sherlock Holmes Short Stories", "sh", 27, 7, 30, progress = 18, duration = 30),
                row("molasses", "The Great Molasses Flood", "Stuff You Missed in History Class", "hc", 26, 18, 45, completed = true),
            )
        }

    /** September (12 h 15 min, 2 h 05 min less than October), August (mostly history) and July (mostly science). */
    private val earlierMonths: List<ListeningDayEpisode> =
        (1..29 step 2).flatMap { day -> SHOW_SPLIT.map { (podcastId, share) -> boardDay(LocalDate.of(2026, 9, day), podcastId, (49 * 60_000L * share).toLong()) } } +
            (3..30 step 3).map { day -> boardDay(LocalDate.of(2026, 8, day), "hc", 62 * 60_000L) } +
            (5..25 step 4).map { day -> boardDay(LocalDate.of(2026, 7, day), "sci", 70 * 60_000L) }

    /** Board A2's calendar, each day split like A4: Darknet Diaries first, then the other shows; evenings lead. */
    private val listening =
        if (empty) {
            emptyList()
        } else {
            HistoryTabletAuditCaptureTest.BOARD_CALENDAR.entries.sortedByDescending { it.key }.take(timedDays).flatMap { (date, ms) ->
                SHOW_SPLIT.map { (podcastId, share) -> boardDay(date, podcastId, (ms * share).toLong()) }
            } + if (timedDays == Int.MAX_VALUE) earlierMonths else emptyList()
        }
    private val played =
        listOf(
            ListeningPlayedShow("dd", "Darknet Diaries", PhoneGoldenRule.ARTWORK_URL, "True Crime", 0L, 0L),
            ListeningPlayedShow("ct", "Conspiracy Theories", PhoneGoldenRule.ARTWORK_URL, "True Crime", 0L, 0L),
            ListeningPlayedShow("briefing_us", "The boxlore brief", PhoneGoldenRule.ARTWORK_URL, "News", 0L, 0L),
            ListeningPlayedShow("ks", "Killer Stories", PhoneGoldenRule.ARTWORK_URL, "True Crime", 0L, 0L),
            ListeningPlayedShow("hc", "Stuff You Missed in History Class", PhoneGoldenRule.ARTWORK_URL, "History", 0L, 0L),
            ListeningPlayedShow("vc", "The Vergecast", PhoneGoldenRule.ARTWORK_URL, "Technology", 0L, 0L),
            ListeningPlayedShow("sci", "Science Vs", PhoneGoldenRule.ARTWORK_URL, "Science", 0L, 0L),
        )

    /** A month's story from this history, as of [today]. */
    fun story(month: YearMonth, today: LocalDate) = StoryLogic.build(month, history, listening, played, today, ZoneId.systemDefault())

    override fun observeHistoryTimeline(): Flow<List<ListeningHistoryItem>> = flowOf(history)

    override fun observeFullHistoryTimeline(): Flow<List<ListeningHistoryItem>> = flowOf(history)

    override fun observeListeningByDay(): Flow<List<ListeningDayEpisode>> = flowOf(listening)

    override fun observePlayedShows(): Flow<List<ListeningPlayedShow>> = flowOf(played)

    override fun observeInsights(period: ListeningPeriod): Flow<ListeningInsightSummary> = flowOf(
        ListeningInsightSummary(
            period = period,
            trackingSinceEpochMs = null,
            totalConsumedMs = 0L,
            previousPeriodConsumedMs = 0L,
            completedCount = 3,
            inProgressCount = 5,
            likedCount = 1,
            sessionCount = 0,
            averageSessionMs = 0L,
            longestSessionMs = 0L,
            streakDays = 0,
            activeDaysInPeriod = 0,
            peakBucket = null,
            morningMs = 0L,
            afternoonMs = 0L,
            eveningMs = 0L,
            nightMs = 0L,
            topShow = null,
            hasEnoughData = false,
        ),
    )

    override suspend fun removeHistoryItem(episodeId: String): ListeningHistoryRemoval? = null

    override suspend fun restoreHistoryRemoval(removal: ListeningHistoryRemoval) = Unit

    override suspend fun clearHistory() = Unit

    override suspend fun maintainListeningAnalytics() = Unit

    @Suppress("LongParameterList")
    private fun row(
        id: String,
        title: String,
        show: String,
        podcastId: String,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
        progress: Long = 0,
        duration: Long = 60,
        completed: Boolean = false,
        liked: Boolean = false,
    ) = ListeningHistoryItem(
        episodeId = id,
        podcastId = podcastId,
        episodeTitle = title,
        episodeImageUrl = PhoneGoldenRule.ARTWORK_URL,
        podcastImageUrl = PhoneGoldenRule.ARTWORK_URL,
        episodeAudioUrl = "https://audio.example.invalid/$id.mp3",
        podcastName = show,
        progressMs = progress * 60_000L,
        durationMs = duration * 60_000L,
        isCompleted = completed,
        isLiked = liked,
        lastPlayedAt = LocalDateTime.of(2026, 10, dayOfMonth, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    private fun boardDay(date: LocalDate, podcastId: String, ms: Long) = ListeningDayEpisode(
        localDay = date.toEpochDay(),
        episodeId = "$podcastId-$date",
        podcastId = podcastId,
        consumedMs = ms,
        sessionCount = 1,
        lastListenedAt = 0L,
        morningMs = (ms * 0.14).toLong(),
        afternoonMs = (ms * 0.18).toLong(),
        eveningMs = (ms * 0.46).toLong(),
        nightMs = (ms * 0.22).toLong(),
        longestSessionMs = ms,
        // Where and what (Room v40 details): mostly Bluetooth, a good part new (every release date known), some from Lore and Smart queue.
        bluetoothMs = (ms * 0.52).toLong(),
        carMs = (ms * 0.30).toLong(),
        speakerMs = (ms * 0.18).toLong(),
        newReleaseMs = (ms * 0.64).toLong(),
        releaseKnownMs = ms,
        loreMs = (ms * 0.12).toLong(),
        smartQueueMs = (ms * 0.21).toLong(),
    )

    private companion object {
        val SHOW_SPLIT = listOf("dd" to 0.29, "ct" to 0.22, "briefing_us" to 0.16, "ks" to 0.13, "hc" to 0.08, "vc" to 0.12)
    }
}
