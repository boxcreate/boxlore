package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.ranking.CandidateFeatureBuilder
import cx.aswin.boxlore.core.ranking.CandidateSignals
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingExposure
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.explore.logic.LoreHiddenShows
import cx.aswin.boxlore.feature.explore.logic.filterAndShuffleNewItems
import cx.aswin.boxlore.feature.explore.logic.weightedShuffle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LearnUiState {
    data object Loading : LearnUiState

    data class Success(
        val questionsStack: List<LearnCuriosityCard>,
        val isRefreshing: Boolean = false
    ) : LearnUiState

    data class CaughtUp(val isRefreshing: Boolean = false) : LearnUiState

    data class Error(val message: String) : LearnUiState
}

class LearnViewModel(
    private val podcastRepository: PodcastRepository,
    application: Application,
    private val rankingFeedback: RankingFeedbackRepository,
    private val historyStore: LearnCuriosityHistoryStore = LearnCuriosityHistoryStore(application),
    /** Every ID of the shows hidden everywhere; their cards leave the deck at once and never join it. */
    private val hiddenShowIds: Flow<Set<String>> = flowOf(emptySet()),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    private val artworkAccents = LoreArtworkAccentStore(viewModelScope) { sources ->
        loadLoreArtworkAccent(application, sources)
    }
    internal val artworkAccentColors = artworkAccents.colors

    // Telemetry tracking fields
    private var sessionStartTime = System.currentTimeMillis()
    private var hasTrackedExit = false
    private var cardsDismissedCount = 0
    private var cardsQueuedCount = 0
    private var playsCount = 0
    private var podcastsClickedCount = 0
    private var infosClickedCount = 0
    private val visibleSince = mutableMapOf<String, Long>()

    fun onScreenResume() {
        applyPendingRestores()
        artworkAccents.prefetch((_uiState.value as? LearnUiState.Success)?.questionsStack.orEmpty().take(4).map { it.artworkSources })
        if (hasTrackedExit) {
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
            cardsDismissedCount = 0
            cardsQueuedCount = 0
            playsCount = 0
            podcastsClickedCount = 0
            infosClickedCount = 0
        }
    }

    private fun applyPendingRestores() {
        val restored = historyStore.consumePendingRestores()
        if (restored.isEmpty()) return

        val restoredCards = restored.map { it.toLearnCuriosityCard() }
        _uiState.update { currentState ->
            when (currentState) {
                is LearnUiState.Success -> {
                    val restoredIds = restoredCards.map { it.episodeId }.toSet()
                    val merged = restoredCards + currentState.questionsStack.filterNot {
                        it.episodeId in restoredIds
                    }
                    currentState.copy(questionsStack = merged)
                }
                is LearnUiState.CaughtUp -> LearnUiState.Success(restoredCards)
                else -> currentState
            }
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnScreenSession(
            timeSpentSeconds = timeSpent,
            cardsDismissedCount = cardsDismissedCount,
            cardsQueuedCount = cardsQueuedCount,
            playsCount = playsCount,
            podcastsClickedCount = podcastsClickedCount,
            infosClickedCount = infosClickedCount
        )
    }

    fun trackCardVisible(card: LearnCuriosityCard) {
        if (visibleSince.putIfAbsent(card.episodeId, System.currentTimeMillis()) != null) return
        viewModelScope.launch {
            rankingFeedback.recordExposure(
                RankingExposure(
                    episodeId = card.episodeId,
                    podcastId = card.podcastId.orEmpty(),
                    objective = RankingObjective.DISCOVERY,
                    surface = RankingSurface.EXPLORE,
                    source = CandidateSource.CURATED_INTENT,
                    features = CandidateFeatureBuilder.build(
                        CandidateSignals(
                            isUnseenShow = true,
                            serverRelevance = card.curiosityScore / 10.0,
                            isUnplayed = true,
                        ),
                    ),
                    entryPoint = "lore",
                    online = true,
                ),
            )
        }
    }

    fun trackCardDismissed(card: LearnCuriosityCard) {
        cardsDismissedCount++
        val dwellMillis = System.currentTimeMillis() - (visibleSince.remove(card.episodeId) ?: return)
        if (dwellMillis < MEANINGFUL_LORE_DWELL_MILLIS) return
        recordLoreAction(card, RankingAction.DISMISS)
    }

    fun trackCardQueued(card: LearnCuriosityCard) {
        cardsQueuedCount++
        visibleSince.remove(card.episodeId)
        recordLoreAction(card, RankingAction.EXPLICIT_QUEUE)
    }

    fun trackPlayClicked(card: LearnCuriosityCard) {
        playsCount++
        recordLoreAction(card, RankingAction.OPEN_DETAILS)
    }

    fun trackPodcastClicked(card: LearnCuriosityCard) {
        podcastsClickedCount++
        recordLoreAction(card, RankingAction.OPEN_DETAILS)
    }

    fun trackInfoClicked(card: LearnCuriosityCard) {
        infosClickedCount++
        recordLoreAction(card, RankingAction.OPEN_DETAILS)
    }

    private fun recordLoreAction(
        card: LearnCuriosityCard,
        action: RankingAction,
    ) {
        viewModelScope.launch {
            rankingFeedback.recordAction(
                target = FeedbackTarget(
                    episodeId = card.episodeId,
                    podcastId = card.podcastId.orEmpty(),
                    source = CandidateSource.CURATED_INTENT,
                ),
                action = action,
            )
        }
    }

    private val paging = CuriosityDeckPaging()
    private var isLoadingMore = false
    private var fetchJob: Job? = null
    private val scanResume = CuriosityScanResume()

    @Volatile
    private var hiddenIds: Set<String> = emptySet()

    private fun List<LearnCuriosityCard>.withoutHiddenShows(): List<LearnCuriosityCard> = LoreHiddenShows.withoutHiddenShows(this, hiddenIds)

    init {
        viewModelScope.launch {
            hiddenShowIds.distinctUntilChanged().collect { ids ->
                hiddenIds = ids
                val state = _uiState.value as? LearnUiState.Success ?: return@collect
                when (val change = LoreHiddenShows.change(state.questionsStack, ids, paging.isEndOfContent)) {
                    LoreHiddenShows.Change.None -> Unit
                    LoreHiddenShows.Change.CaughtUp -> showCaughtUp()
                    is LoreHiddenShows.Change.Deck -> {
                        _uiState.value = state.copy(questionsStack = change.cards)
                        if (change.loadMore) fetchNextPage()
                    }
                }
            }
        }
        viewModelScope.launch {
            uiState.map { state ->
                (state as? LearnUiState.Success)?.questionsStack.orEmpty().take(4).map { it.artworkSources }
            }.distinctUntilChanged().collect(artworkAccents::prefetch)
        }
        loadData()
    }

    private fun getDismissedIds(): Set<String> = historyStore.getDismissedIds()

    /** Scans for the first deck with unseen cards, carrying on where an earlier check stopped ([CuriosityScanResume]). */
    private suspend fun loadFirstAvailableDeck(
        bypassCache: Boolean
    ): InitialCuriosityDeckResult {
        val dismissedIds = getDismissedIds()
        // Read the hidden shows first so the scan never waits on the collector to see them.
        val hidden = hiddenShowIds.first().also { hiddenIds = it }
        return findFirstUnseenCuriosityDeck(
            dismissedIds = dismissedIds,
            startPage = scanResume.startPage(dismissedIds),
            isHidden = { LoreHiddenShows.isHidden(it, hidden) },
            fetchPage = { page ->
                podcastRepository.getCuratedCuriosity(
                    page = page,
                    bypassCache = bypassCache
                )?.questionsStack?.map { it.toLearnCuriosityCard() }
            }
        ).also {
            scanResume.after(it, dismissedIds, freshPages = bypassCache)
            paging.onScan(it)
        }
    }

    /**
     * No unseen card found: caught up. After [InitialCuriosityDeckResult.Capped] the list goes on,
     * so paging stays open and the next check carries on further down.
     */
    private fun showCaughtUp() {
        _uiState.value = LearnUiState.CaughtUp()
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnCaughtUp(0)
    }

    private fun showDeck(result: InitialCuriosityDeckResult.Found) {
        val unhidden = result.unseenItems.withoutHiddenShows()
        if (unhidden.isEmpty()) return showCaughtUp()
        val shuffled = weightedShuffle(
            list = unhidden,
            curiosityScore = LearnCuriosityCard::curiosityScore,
        )
        _uiState.value = LearnUiState.Success(
            questionsStack = shuffled
        )
    }

    fun dismissCuriosity(card: LearnCuriosityCard, action: LearnHistoryAction) {
        val episodeId = card.episodeId
        historyStore.recordDismissal(card, action)

        val currentState = _uiState.value
        if (currentState is LearnUiState.Success) {
            val updatedStack = currentState.questionsStack.filterNot { it.episodeId == episodeId }
            if (updatedStack.isEmpty() && paging.isEndOfContent) {
                _uiState.value = LearnUiState.CaughtUp()
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnCaughtUp(0)
                return
            }

            _uiState.value = currentState.copy(questionsStack = updatedStack)
            if (updatedStack.size < 3) {
                fetchNextPage()
            }
        }
    }

    private suspend fun fetchPageAndFilter(
        page: Int,
        currentStack: List<LearnCuriosityCard>
    ): List<LearnCuriosityCard> {
        val res = podcastRepository.getCuratedCuriosity(page = page, bypassCache = false) ?: return emptyList()
        if (res.questionsStack.isEmpty()) {
            paging.onListEnded()
            return emptyList()
        }
        paging.onPageLoaded(page)
        return filterAndShuffleNewItems(
            rawItems = res.questionsStack.map { it.toLearnCuriosityCard() }.withoutHiddenShows(),
            currentStack = currentStack,
            dismissedIds = getDismissedIds(),
            episodeId = LearnCuriosityCard::episodeId,
            curiosityScore = LearnCuriosityCard::curiosityScore,
        )
    }

    private fun appendCuriositiesToSuccessState(newItems: List<LearnCuriosityCard>) {
        _uiState.update { state ->
            if (state is LearnUiState.Success) {
                state.copy(questionsStack = state.questionsStack + newItems)
            } else {
                state
            }
        }
    }

    private fun fetchNextPage() {
        if (isLoadingMore || paging.isEndOfContent || fetchJob?.isActive == true) return
        if (_uiState.value !is LearnUiState.Success) return

        isLoadingMore = true
        fetchJob = viewModelScope.launch {
            try {
                var pageToFetch = paging.currentPage + 1
                var accumulatedNew = emptyList<LearnCuriosityCard>()
                var pageAttempts = 0
                val maxAttempts = 5

                while (accumulatedNew.isEmpty() && !paging.isEndOfContent && pageAttempts < maxAttempts) {
                    pageAttempts++
                    val currentStack = (_uiState.value as? LearnUiState.Success)?.questionsStack ?: emptyList()
                    accumulatedNew = fetchPageAndFilter(pageToFetch, currentStack)
                    if (!paging.isEndOfContent) {
                        pageToFetch++
                    }
                }

                if (accumulatedNew.isNotEmpty()) {
                    appendCuriositiesToSuccessState(accumulatedNew)
                }
            } catch (e: Exception) {
                android.util.Log.e("LearnViewModel", "Failed to load page ${paging.currentPage + 1}", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun loadData() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = LearnUiState.Loading
            paging.reset()
            isLoadingMore = false
            try {
                when (val result = loadFirstAvailableDeck(bypassCache = false)) {
                    is InitialCuriosityDeckResult.Found -> showDeck(result)
                    is InitialCuriosityDeckResult.Exhausted,
                    is InitialCuriosityDeckResult.Capped -> showCaughtUp()
                    is InitialCuriosityDeckResult.Failed -> {
                        _uiState.value = LearnUiState.Error("Failed to load curiosity curation")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LearnUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun refresh() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val current = _uiState.value
            when (current) {
                is LearnUiState.Success -> _uiState.value = current.copy(isRefreshing = true)
                is LearnUiState.CaughtUp -> _uiState.value = current.copy(isRefreshing = true)
                else -> Unit
            }
            try {
                isLoadingMore = false
                // A failed check keeps what is shown and the paging behind it: only an answer moves [paging].
                when (val result = loadFirstAvailableDeck(bypassCache = true)) {
                    is InitialCuriosityDeckResult.Found -> showDeck(result)
                    is InitialCuriosityDeckResult.Exhausted,
                    is InitialCuriosityDeckResult.Capped -> showCaughtUp()
                    is InitialCuriosityDeckResult.Failed -> {
                        _uiState.value = when (current) {
                            is LearnUiState.Success -> current.copy(isRefreshing = false)
                            is LearnUiState.CaughtUp -> current.copy(isRefreshing = false)
                            else -> LearnUiState.Error("Failed to refresh curiosity curation")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = when (current) {
                    is LearnUiState.Success -> current.copy(isRefreshing = false)
                    is LearnUiState.CaughtUp -> current.copy(isRefreshing = false)
                    else -> LearnUiState.Error(e.message ?: "Unknown error occurred")
                }
            }
        }
    }

    companion object {
        private const val MEANINGFUL_LORE_DWELL_MILLIS = 3_000L
    }
}
