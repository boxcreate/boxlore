package cx.aswin.boxlore.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.feature.history.insights.InsightsContent
import cx.aswin.boxlore.feature.history.insights.InsightsLogic
import cx.aswin.boxlore.feature.history.story.MonthStory
import cx.aswin.boxlore.feature.history.story.StoryLogic
import cx.aswin.boxlore.feature.history.story.StoryMonth
import cx.aswin.boxlore.feature.history.timeline.HistoryTimelineLogic
import cx.aswin.boxlore.feature.history.timeline.TimelineContent
import cx.aswin.boxlore.feature.history.timeline.TimelineEntry
import cx.aswin.boxlore.feature.history.timeline.TimelineFilter
import cx.aswin.boxlore.feature.history.timeline.TimelineGrouping
import cx.aswin.boxlore.feature.history.timeline.TimelineTabState
import cx.aswin.boxlore.feature.history.timeline.timelineEntries
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Listening history's two tabs, in their order: Insights first (it opens on it), then the Timeline. */
enum class HistoryTab {
    INSIGHTS,
    TIMELINE,
}

sealed interface HistoryUiEvent {
    data class ShowUndoDelete(val removal: ListeningHistoryRemoval) : HistoryUiEvent

    data object HistoryCleared : HistoryUiEvent
}

/** The Timeline tab: loading, or its content with the chosen filter, grouping and search text. */
sealed interface TimelineUiState {
    data object Loading : TimelineUiState

    data class Ready(
        val content: TimelineContent,
        val filter: TimelineFilter,
        val grouping: TimelineGrouping,
        val query: String,
        val today: LocalDate,
        /** The list's rows, built off the main thread. */
        val entries: List<TimelineEntry> = emptyList(),
    ) : TimelineUiState {
        val tabState: TimelineTabState get() = TimelineTabState(content, filter, grouping, query, today)
    }
}

/** The Insights tab: loading, or the summary for the chosen period. */
sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    data class Ready(val content: InsightsContent, val period: ListeningPeriod, val today: LocalDate, val stories: StoryShelf = StoryShelf(),) : InsightsUiState
}

/**
 * The monthly stories: last month's ([latest], on the Insights tab's card, New until it is opened)
 * and every month before it with timed listening ([earlier]).
 */
data class StoryShelf(val latest: StoryMonth? = null, val latestIsNew: Boolean = false, val earlier: List<StoryMonth> = emptyList())

/** A month's story on show, opened at its first card or, from Share your listening, at its share card. */
sealed interface StoryUiState {
    data object Closed : StoryUiState

    data class Open(val story: MonthStory, val atShare: Boolean) : StoryUiState
}

/**
 * Listening history (Timeline and Insights tabs). The Timeline reads the whole history and every
 * phone's timed listening ([ListeningHistoryPort.observeFullHistoryTimeline],
 * [ListeningHistoryPort.observeListeningByDay]); selections live here so they survive rotation,
 * resizing and folding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val listeningHistoryPort: ListeningHistoryPort,
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> LocalDate = { LocalDate.now(zoneId()) },
    /** The last story month opened ("2026-10"), so its card stops saying New. */
    private val storySeen: Flow<String?> = flowOf(null),
    private val markStorySeen: suspend (String) -> Unit = {},
) : ViewModel() {
    private val tab = MutableStateFlow(HistoryTab.INSIGHTS)
    private val filter = MutableStateFlow(TimelineFilter.ALL)
    private val grouping = MutableStateFlow(TimelineGrouping.DAYS)
    private val query = MutableStateFlow("")
    private val period = MutableStateFlow(ListeningPeriod.DAYS_30)
    private val today = MutableStateFlow(now())

    private val eventsChannel = Channel<HistoryUiEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    /** Days the Timeline should scroll to (Jump to a day), once each filter and grouping reset has applied. */
    private val scrollChannel = Channel<LocalDate>(Channel.CONFLATED)
    val scrollRequests = scrollChannel.receiveAsFlow()

    val selectedTab: StateFlow<HistoryTab> = tab.asStateFlow()

    private data class Selection(val filter: TimelineFilter, val grouping: TimelineGrouping, val query: String, val today: LocalDate,)

    private val selection = combine(filter, grouping, query, today) { f, g, q, t -> Selection(f, g, q, t) }

    val timelineState: StateFlow<TimelineUiState> =
        tab.whileOpen(HistoryTab.TIMELINE) {
            combine(listeningHistoryPort.observeFullHistoryTimeline(), listeningHistoryPort.observeListeningByDay(), selection) { history, listening, chosen ->
                val ready =
                    TimelineUiState.Ready(
                        content = HistoryTimelineLogic.build(HistoryTimelineLogic.Input(history, listening, chosen.filter, chosen.query, zoneId())),
                        filter = chosen.filter,
                        grouping = chosen.grouping,
                        query = chosen.query,
                        today = chosen.today,
                    )
                ready.copy(entries = timelineEntries(ready.tabState)) as TimelineUiState
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimelineUiState.Loading)

    val insightsState: StateFlow<InsightsUiState> =
        tab.whileOpen(HistoryTab.INSIGHTS) {
            combine(
                listeningHistoryPort.observeFullHistoryTimeline(),
                listeningHistoryPort.observeListeningByDay(),
                listeningHistoryPort.observePlayedShows(),
                period,
                today,
            ) { history, listening, played, chosen, day ->
                val content = InsightsLogic.build(InsightsLogic.Input(history, listening, played, chosen, day, zoneId()))
                InsightsUiState.Ready(content, chosen, day) to StoryLogic.months(listening, played, day)
            }.combine(storySeen) { (ready, months), seen ->
                val lastMonth = YearMonth.from(ready.today).minusMonths(1)
                val latest = months.firstOrNull { it.month == lastMonth }
                ready.copy(stories = StoryShelf(latest, latest != null && latest.month.toString() != seen, months - setOfNotNull(latest))) as InsightsUiState
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsUiState.Loading)

    private data class StoryRequest(val month: YearMonth, val atShare: Boolean)

    private val storyRequest = MutableStateFlow<StoryRequest?>(null)

    /** The story on show, built once from the whole history when it opens. */
    val storyState: StateFlow<StoryUiState> =
        storyRequest.flatMapLatest { request ->
            if (request == null) {
                flowOf<StoryUiState>(StoryUiState.Closed)
            } else {
                flow {
                    val story = StoryLogic.build(
                        request.month,
                        listeningHistoryPort.observeFullHistoryTimeline().first(),
                        listeningHistoryPort.observeListeningByDay().first(),
                        listeningHistoryPort.observePlayedShows().first(),
                        today.value,
                        zoneId(),
                    )
                    emit(if (story == null) StoryUiState.Closed else StoryUiState.Open(story, request.atShare))
                }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoryUiState.Closed)

    init {
        viewModelScope.launch { listeningHistoryPort.maintainListeningAnalytics() }
    }

    /** Opens [month]'s story, at its share card when [atShare]; opening last month's story clears its New. */
    fun openStory(month: YearMonth, atShare: Boolean = false) {
        storyRequest.value = StoryRequest(month, atShare)
        if (month == YearMonth.from(today.value).minusMonths(1)) viewModelScope.launch { markStorySeen(month.toString()) }
    }

    fun closeStory() {
        storyRequest.value = null
    }

    fun selectTab(selected: HistoryTab) {
        tab.value = selected
    }

    fun setFilter(selected: TimelineFilter) {
        filter.value = selected
    }

    fun setGrouping(selected: TimelineGrouping) {
        grouping.value = selected
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setPeriod(selected: ListeningPeriod) {
        period.value = selected
    }

    /** "All {n}" of Jump back in: every in-progress episode. */
    fun showAllInProgress() {
        tab.value = HistoryTab.TIMELINE
        grouping.value = TimelineGrouping.DAYS
        filter.value = TimelineFilter.IN_PROGRESS
    }

    /** Jump to a day: back to every episode by day, then the screen scrolls to [date]. */
    fun jumpTo(date: LocalDate) {
        filter.value = TimelineFilter.ALL
        grouping.value = TimelineGrouping.DAYS
        query.value = ""
        scrollChannel.trySend(date)
    }

    fun removeHistoryItem(episodeId: String) {
        viewModelScope.launch {
            val removal = listeningHistoryPort.removeHistoryItem(episodeId) ?: return@launch
            itemsDeletedCount++
            eventsChannel.send(HistoryUiEvent.ShowUndoDelete(removal))
        }
    }

    fun undoRemoval(removal: ListeningHistoryRemoval) {
        viewModelScope.launch { listeningHistoryPort.restoreHistoryRemoval(removal) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            listeningHistoryPort.clearHistory()
            itemsDeletedCount++
            eventsChannel.send(HistoryUiEvent.HistoryCleared)
        }
    }

    var sessionStartTime: Long = 0L
    private var hasTrackedExit = false

    var episodesClickedCount = 0
    var itemsDeletedCount = 0

    /** Called when the screen starts: a screen left open past midnight reads the new day. */
    fun onScreenResume() {
        today.value = now()
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHistorySession(
            timeSpentSeconds = timeSpent,
            episodesClickedCount = episodesClickedCount,
            itemsDeletedCount = itemsDeletedCount,
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Each tab works only while it is open; the other keeps its last state until it is opened again. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<HistoryTab>.whileOpen(open: HistoryTab, work: () -> Flow<T>): Flow<T> =
    map { it == open }.distinctUntilChanged().flatMapLatest { active -> if (active) work() else emptyFlow() }
