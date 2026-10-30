package cx.aswin.boxlore.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.feature.history.insights.InsightsContent
import cx.aswin.boxlore.feature.history.insights.InsightsLogic
import cx.aswin.boxlore.feature.history.timeline.HistoryTimelineLogic
import cx.aswin.boxlore.feature.history.timeline.TimelineContent
import cx.aswin.boxlore.feature.history.timeline.TimelineEntry
import cx.aswin.boxlore.feature.history.timeline.TimelineFilter
import cx.aswin.boxlore.feature.history.timeline.TimelineGrouping
import cx.aswin.boxlore.feature.history.timeline.TimelineTabState
import cx.aswin.boxlore.feature.history.timeline.timelineEntries
import java.time.LocalDate
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
import kotlinx.coroutines.flow.flatMapLatest
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

    data class Ready(val content: InsightsContent, val period: ListeningPeriod, val today: LocalDate,) : InsightsUiState
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

    /** Each tab works only while it is open; the other keeps its last state until it is opened again. */
    private fun <T> whileOpen(open: HistoryTab, work: () -> Flow<T>): Flow<T> = tab.map { it == open }.distinctUntilChanged().flatMapLatest { active -> if (active) work() else emptyFlow() }

    val timelineState: StateFlow<TimelineUiState> =
        whileOpen(HistoryTab.TIMELINE) {
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
        whileOpen(HistoryTab.INSIGHTS) {
            combine(
                listeningHistoryPort.observeFullHistoryTimeline(),
                listeningHistoryPort.observeListeningByDay(),
                listeningHistoryPort.observePlayedShows(),
                period,
                today,
            ) { history, listening, played, chosen, day ->
                InsightsUiState.Ready(InsightsLogic.build(InsightsLogic.Input(history, listening, played, chosen, day, zoneId())), chosen, day) as InsightsUiState
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsUiState.Loading)

    init {
        viewModelScope.launch { listeningHistoryPort.maintainListeningAnalytics() }
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

    /** Called when the screen starts, so a screen left open past midnight reads the new day. */
    fun refreshToday() {
        today.value = now()
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

    fun onScreenResume() {
        refreshToday()
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
