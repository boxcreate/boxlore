package cx.aswin.boxlore.feature.history

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.timeline.TimelineFilter
import cx.aswin.boxlore.feature.history.timeline.TimelineGrouping
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Listening history's selections live in the ViewModel, so the tab, filter, grouping and search
 * survive rotation, resizing and folding (the screen is recreated; the ViewModel is not), and
 * Jump to a day resets them before asking the Timeline to scroll.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val history = MutableStateFlow(listOf(item("a", completed = false), item("b", completed = true)))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    private val created = mutableListOf<HistoryViewModel>()

    @AfterEach
    fun tearDown() {
        // Stop each ViewModel's work while the test Main dispatcher is still set.
        runBlocking { created.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test
    fun selectionsAreKeptForTheNextScreen() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectTab(HistoryTab.INSIGHTS)
        vm.setFilter(TimelineFilter.FINISHED)
        vm.setGrouping(TimelineGrouping.SHOWS)
        vm.setQuery("b")

        // A recreated screen reads the same ViewModel; the Timeline works again once it is open.
        assertEquals(HistoryTab.INSIGHTS, vm.selectedTab.value)
        vm.selectTab(HistoryTab.TIMELINE)
        val timeline = vm.timelineState.first { it is TimelineUiState.Ready && it.query == "b" } as TimelineUiState.Ready
        assertEquals(TimelineFilter.FINISHED, timeline.filter)
        assertEquals(TimelineGrouping.SHOWS, timeline.grouping)
        assertEquals("b", timeline.query)
        assertEquals(listOf("Show b"), timeline.content.months.single().shows.map { it.podcastName })
    }

    @Test
    fun allInProgressOpensTheTimelineFilteredByDay() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setQuery("no such episode")
        vm.selectTab(HistoryTab.INSIGHTS)
        vm.setGrouping(TimelineGrouping.SHOWS)

        vm.showAllInProgress()

        val timeline = vm.timelineState.first { it is TimelineUiState.Ready && it.filter == TimelineFilter.IN_PROGRESS } as TimelineUiState.Ready
        assertEquals(HistoryTab.TIMELINE, vm.selectedTab.value)
        assertEquals(TimelineGrouping.DAYS, timeline.grouping)
        // An earlier search doesn't hide the in-progress episodes.
        assertEquals("", timeline.query)
        assertEquals(listOf("a"), timeline.content.months.flatMap { month -> month.days.flatMap { day -> day.episodes.map { it.item.episodeId } } })
        // The chip alone is easy to miss: the screen is asked to scroll to the filters.
        assertEquals(TimelineScrollTarget.InProgress, vm.scrollRequests.first())
    }

    @Test
    fun aJumpResetsTheTimelineAndAsksForTheDay() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectTab(HistoryTab.TIMELINE)
        vm.setFilter(TimelineFilter.LIKED)
        vm.setGrouping(TimelineGrouping.SHOWS)
        vm.setQuery("nothing")

        vm.jumpTo(LocalDate.of(2026, 10, 3))

        assertEquals(TimelineScrollTarget.Day(LocalDate.of(2026, 10, 3)), vm.scrollRequests.first())
        val timeline = vm.timelineState.first { it is TimelineUiState.Ready } as TimelineUiState.Ready
        assertEquals(TimelineFilter.ALL, timeline.filter)
        assertEquals(TimelineGrouping.DAYS, timeline.grouping)
        assertTrue(timeline.query.isEmpty())
    }

    @Test
    fun theTimelineFollowsTheHistory() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectTab(HistoryTab.TIMELINE)
        val before = vm.timelineState.first { it is TimelineUiState.Ready } as TimelineUiState.Ready
        assertEquals(1, before.content.inProgressCount)

        history.value = history.value + item("c", completed = false)

        val after = vm.timelineState.first { it is TimelineUiState.Ready && it.content.inProgressCount == 2 } as TimelineUiState.Ready
        assertEquals(2, after.content.jumpBackIn.size)
    }

    @Test
    fun insightsOpenFirstAndWorkOnlyWhileTheirTabIsOpen() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(HistoryTab.INSIGHTS, vm.selectedTab.value)

        val insights = vm.insightsState.first { it is InsightsUiState.Ready } as InsightsUiState.Ready

        assertEquals(1, insights.content.inProgress.count)
        assertEquals(LocalDate.of(2026, 10, 30), insights.today)
    }

    @Test
    fun lastMonthsStoryIsNewUntilItIsOpened() = runTest(dispatcher) {
        val seen = MutableStateFlow<String?>(null)
        val listening = listOf(day(2026, 9, 12, "show", 30), day(2026, 8, 3, "show", 20))
        val vm = HistoryViewModel(FakeHistory(history, listening), zoneId = { ZoneOffset.UTC }, now = { LocalDate.of(2026, 10, 30) }, storySeen = seen, markStorySeen = { seen.value = it }).also { created += it }

        val shelf = (vm.insightsState.first { it is InsightsUiState.Ready } as InsightsUiState.Ready).stories
        assertEquals(YearMonth.of(2026, 9), shelf.latest!!.month)
        assertTrue(shelf.latestIsNew)
        assertEquals(listOf(YearMonth.of(2026, 8)), shelf.earlier.map { it.month })

        vm.openStory(YearMonth.of(2026, 9))

        val open = vm.storyState.first { it is StoryUiState.Open } as StoryUiState.Open
        assertEquals(YearMonth.of(2026, 9), open.story.month)
        assertEquals(30 * 60_000L, open.story.totalMs)
        assertEquals("2026-09", seen.value)
        val after = vm.insightsState.first { it is InsightsUiState.Ready && !it.stories.latestIsNew } as InsightsUiState.Ready
        assertEquals(YearMonth.of(2026, 9), after.stories.latest!!.month)

        vm.closeStory()
        assertEquals(StoryUiState.Closed, vm.storyState.first { it == StoryUiState.Closed })
    }

    @Test
    fun aFailedSeenWriteLeavesTheStoryOpen() = runTest(dispatcher) {
        val listening = listOf(day(2026, 9, 12, "show", 30))
        val vm = HistoryViewModel(FakeHistory(history, listening), zoneId = { ZoneOffset.UTC }, now = { LocalDate.of(2026, 10, 30) }, markStorySeen = { throw IOException("disk full") }).also { created += it }

        vm.openStory(YearMonth.of(2026, 9))

        val open = vm.storyState.first { it is StoryUiState.Open } as StoryUiState.Open
        assertEquals(YearMonth.of(2026, 9), open.story.month)
    }

    @Test
    fun shareYourListeningOpensTheStoryAtItsShareCard() = runTest(dispatcher) {
        val vm = HistoryViewModel(FakeHistory(history, listOf(day(2026, 8, 3, "show", 20))), zoneId = { ZoneOffset.UTC }, now = { LocalDate.of(2026, 10, 30) }).also { created += it }

        vm.openStory(YearMonth.of(2026, 8), atShare = true)

        val open = vm.storyState.first { it is StoryUiState.Open } as StoryUiState.Open
        assertTrue(open.atShare)
    }

    private fun viewModel() = HistoryViewModel(FakeHistory(history), zoneId = { ZoneOffset.UTC }, now = { LocalDate.of(2026, 10, 30) }).also { created += it }

    private fun day(year: Int, month: Int, dayOfMonth: Int, podcastId: String, minutes: Long) = ListeningDayEpisode(
        localDay = LocalDate.of(year, month, dayOfMonth).toEpochDay(),
        episodeId = "$podcastId-$month-$dayOfMonth",
        podcastId = podcastId,
        consumedMs = minutes * 60_000L,
        sessionCount = 1,
        lastListenedAt = 0L,
    )

    private fun item(id: String, completed: Boolean) = ListeningHistoryItem(
        episodeId = id,
        podcastId = "show",
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show $id",
        progressMs = 60_000L,
        durationMs = 600_000L,
        isCompleted = completed,
        isLiked = false,
        lastPlayedAt = 1_793_000_000_000L,
    )

    private class FakeHistory(private val history: Flow<List<ListeningHistoryItem>>, private val listening: List<ListeningDayEpisode> = emptyList()) : ListeningHistoryPort {
        override fun observeHistoryTimeline(): Flow<List<ListeningHistoryItem>> = history

        override fun observeFullHistoryTimeline(): Flow<List<ListeningHistoryItem>> = history

        override fun observeListeningByDay(): Flow<List<ListeningDayEpisode>> = flowOf(listening)

        override fun observePlayedShows(): Flow<List<ListeningPlayedShow>> = flowOf(emptyList())

        override fun observeInsights(period: ListeningPeriod): Flow<ListeningInsightSummary> = flowOf()

        override suspend fun removeHistoryItem(episodeId: String): ListeningHistoryRemoval? = null

        override suspend fun restoreHistoryRemoval(removal: ListeningHistoryRemoval) = Unit

        override suspend fun clearHistory() = Unit

        override suspend fun maintainListeningAnalytics() = Unit
    }
}
