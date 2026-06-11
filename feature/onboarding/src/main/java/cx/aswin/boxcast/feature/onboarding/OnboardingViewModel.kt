package cx.aswin.boxcast.feature.onboarding

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cx.aswin.boxcast.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxcast.core.network.model.OnboardingPart
import cx.aswin.boxcast.core.network.model.OnboardingNextTurnRequest
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumRequest
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumEpisodeDto
import cx.aswin.boxcast.core.network.model.OnboardingQuery
import cx.aswin.boxcast.core.network.model.toPodcast
import cx.aswin.boxcast.core.model.Episode

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

enum class AiLoadingStage {
    IDLE,
    GENERATING_RESPONSE,
    SYNTHESIZING_PREFERENCES,
    FETCHING_CATALOGS,
    ASSEMBLING_FEED
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val selectedGenres: Set<String> = emptySet(),
    val recommendedPodcasts: List<Podcast> = emptyList(),
    val subscribedPodcastIds: Set<String> = emptySet(),
    val isLoadingPodcasts: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Podcast> = emptyList(),
    val isSearching: Boolean = false,
    val isCompleting: Boolean = false,
    val currentRegion: String = "us",
    val initialRegion: String = "us",
    val selectedPodcasts: Map<String, Podcast> = emptyMap(),
    // AI Onboarding fields
    val aiHistory: List<OnboardingHistoryEntry> = emptyList(),
    val aiAssistantMessage: String = "Hi! I'm boxcast. To start, what kind of a listener are you?",
    val aiOptions: List<String> = listOf(
        "Storyseeker | Serialized narratives, investigative series, and immersive audio dramas",
        "Deep Diver | Detailed analysis, long-form research, and intellectual essays",
        "Conversationalist | Candid guest interviews, unscripted discussions, and casual banter",
        "Chill Listener | Soothing voices, mindfulness sessions, and relaxing background talk"
    ),
    val aiCurrentTurn: Int = 1,
    val aiCustomInputText: String = "",
    val aiSelectedOptions: Set<String> = emptySet(),
    val isAiLoading: Boolean = false,
    val isSynthesizing: Boolean = false,
    val aiCurriculumRows: List<OnboardingCurriculumRowDto> = emptyList(),
    val aiLoadingStage: AiLoadingStage = AiLoadingStage.IDLE,
    val onboardingError: String? = null
)

enum class OnboardingStep {
    WELCOME, GENRES, PODCASTS, SEARCH, AI_ONBOARDING, AI_SUGGESTIONS
}

class OnboardingViewModel(
    application: Application,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null
    private var recommendationJob: Job? = null

    private val turnHistoryState = mutableListOf<TurnState>()
    private var lastFailedAction: (() -> Unit)? = null
    
    data class TurnState(
        val assistantMessage: String,
        val options: List<String>,
        val history: List<OnboardingHistoryEntry>
    )

    init {
        // Retrieve and initialize region once on startup
        viewModelScope.launch {
            userPrefs.regionStream.take(1).collect { region ->
                _uiState.update { 
                    it.copy(
                         currentRegion = region,
                         initialRegion = region
                    ) 
                }
                if (_uiState.value.currentStep == OnboardingStep.PODCASTS) {
                    loadRecommendationsForRegion(region)
                }
            }
        }
    }

    // ── Analytics Timing & Counters ────────────────────────────────
    private var onboardingStartMs: Long = 0L
    private var welcomeScreenStartMs: Long = 0L
    private var genreScreenStartMs: Long = 0L
    private var podcastScreenStartMs: Long = 0L
    private var searchScreenStartMs: Long = 0L
    private var searchesPerformedCount: Int = 0
    private var podcastsSubscribedInSearchCount: Int = 0
    private var searchEntryPoint: String = "genre_screen"
    private var onboardingStartedFired: Boolean = false
    private var didScrollSuggestions: Boolean = false
    
    /** Total time since the onboarding flow began */
    fun getTotalOnboardingTime(): Float {
        if (onboardingStartMs == 0L) return 0f
        return (System.currentTimeMillis() - onboardingStartMs) / 1000f
    }
    
    /** Time spent on the welcome screen in seconds */
    fun getWelcomeScreenTimeSpent(): Float {
        if (welcomeScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - welcomeScreenStartMs) / 1000f
    }
    
    /** Time spent on the genre screen in seconds */
    fun getGenreScreenTimeSpent(): Float {
        if (genreScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - genreScreenStartMs) / 1000f
    }
    
    /** Time spent on the podcast screen in seconds */
    private fun getPodcastScreenTimeSpent(): Float {
        if (podcastScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - podcastScreenStartMs) / 1000f
    }

    /** Time spent on the search screen in seconds */
    private fun getSearchScreenTimeSpent(): Float {
        if (searchScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - searchScreenStartMs) / 1000f
    }

    /** Called when the welcome screen composable first loads */
    fun onWelcomeScreenViewed() {
        if (!onboardingStartedFired) {
            onboardingStartMs = System.currentTimeMillis()
            welcomeScreenStartMs = System.currentTimeMillis()
            AnalyticsHelper.trackOnboardingStarted()
            onboardingStartedFired = true
        }
    }

    /** Called when the genre screen composable first loads */
    fun onGenreScreenViewed() {
        if (genreScreenStartMs == 0L) {
            genreScreenStartMs = System.currentTimeMillis()
        }
    }

    /** Called when the podcast screen composable first loads */
    fun onPodcastScreenViewed() {
        if (podcastScreenStartMs == 0L) {
            podcastScreenStartMs = System.currentTimeMillis()
        }
    }

    fun onPodcastScreenScrolled() {
        didScrollSuggestions = true
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }
    
    fun startOnboarding() {
        _uiState.update { it.copy(currentStep = OnboardingStep.AI_ONBOARDING) }
    }
    
    fun toggleGenre(genre: String) {
        _uiState.update { state ->
            val newGenres = if (genre in state.selectedGenres) {
                state.selectedGenres - genre
            } else {
                state.selectedGenres + genre
            }
            state.copy(selectedGenres = newGenres)
        }
    }
    
    private fun loadRecommendationsForRegion(region: String) {
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPodcasts = true) }
            val genresList = _uiState.value.selectedGenres.toList()
            val allPodcasts = mutableListOf<Podcast>()
            
            // Fetch trending podcasts for each selected genre
            val perGenreLimit = when {
                genresList.size <= 2 -> 5
                genresList.size <= 4 -> 3
                else -> 2
            }
            
            for (genre in genresList) {
                val trending = podcastRepository.getTrendingPodcasts(
                    country = region,
                    category = genre,
                    limit = perGenreLimit
                )
                allPodcasts.addAll(trending)
            }
            
            // Deduplicate and limit to 10
            val uniquePodcasts = allPodcasts
                .distinctBy { it.id }
                .shuffled()
                .take(10)
            
            _uiState.update {
                it.copy(
                    recommendedPodcasts = uniquePodcasts,
                    isLoadingPodcasts = false
                )
            }
        }
    }

    fun continueToRecommendations() {
        // Analytics: Track genres submitted with time spent
        val selectedGenres = _uiState.value.selectedGenres
        AnalyticsHelper.trackGenresSubmitted(selectedGenres, getGenreScreenTimeSpent())

        _uiState.update { it.copy(currentStep = OnboardingStep.PODCASTS) }
        loadRecommendationsForRegion(_uiState.value.currentRegion)
    }

    fun setRegion(region: String) {
        _uiState.update { it.copy(currentRegion = region) }
        if (_uiState.value.currentStep == OnboardingStep.PODCASTS) {
            loadRecommendationsForRegion(region)
        }
    }
    
    fun togglePodcastSubscription(podcastId: String) {
        _uiState.update { state ->
            val newSelected = if (podcastId in state.subscribedPodcastIds) {
                state.selectedPodcasts - podcastId
            } else {
                val podcast = state.recommendedPodcasts.find { it.id == podcastId }
                    ?: state.searchResults.find { it.id == podcastId }
                    ?: state.aiCurriculumRows.flatMap { it.podcasts }.map { it.toPodcast() }.find { it.id == podcastId }
                if (podcast != null) {
                    state.selectedPodcasts + (podcastId to podcast)
                } else {
                    state.selectedPodcasts
                }
            }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys
            )
        }
    }

    fun toggleAllCurriculumPodcasts() {
        _uiState.update { state ->
            val allPodcasts = state.aiCurriculumRows.flatMap { it.podcasts }.map { it.toPodcast() }
            val allIds = allPodcasts.map { it.id }.toSet()
            val allSelected = allIds.isNotEmpty() && allIds.all { it in state.subscribedPodcastIds }
            
            val newSelected = if (allSelected) {
                state.selectedPodcasts.filterKeys { it !in allIds }
            } else {
                state.selectedPodcasts + allPodcasts.associateBy { it.id }
            }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys
            )
        }
    }

    fun toggleAllPodcastsInRow(rowTitle: String) {
        _uiState.update { state ->
            val row = state.aiCurriculumRows.find { it.rowTitle == rowTitle } ?: return@update state
            val rowPodcasts = row.podcasts.map { it.toPodcast() }
            val rowIds = rowPodcasts.map { it.id }.toSet()
            val allSelected = rowIds.isNotEmpty() && rowIds.all { it in state.subscribedPodcastIds }
            
            val newSelected = if (allSelected) {
                state.selectedPodcasts.filterKeys { it !in rowIds }
            } else {
                state.selectedPodcasts + rowPodcasts.associateBy { it.id }
            }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys
            )
        }
    }
    
    fun navigateToSearch() {
        // Determine entry point based on current step
        searchEntryPoint = when (_uiState.value.currentStep) {
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.PODCASTS -> "podcast_screen"
            else -> "genre_screen"
        }

        // Analytics: Track search opened
        val genreTime = if (searchEntryPoint == "genre_screen") getGenreScreenTimeSpent() else null
        AnalyticsHelper.trackSearchOpened(searchEntryPoint, genreTime)

        // Reset search counters for this session
        searchesPerformedCount = 0
        podcastsSubscribedInSearchCount = 0
        searchScreenStartMs = System.currentTimeMillis()

        _uiState.update { it.copy(currentStep = OnboardingStep.SEARCH) }
    }
    
    fun navigateBackFromPodcasts() {
        _uiState.update { it.copy(currentStep = OnboardingStep.GENRES) }
    }
    
    fun navigateBackToWelcome() {
        _uiState.update { it.copy(currentStep = OnboardingStep.WELCOME) }
    }
    
    fun navigateBackFromSearch() {
        val state = _uiState.value
        val exitDestination = if (state.selectedGenres.isNotEmpty()) "podcast_screen" else "genre_screen"

        // Analytics: Track search exit
        AnalyticsHelper.trackSearchExited(
            exitDestination = exitDestination,
            searchesPerformed = searchesPerformedCount,
            podcastsSubscribedInSearch = podcastsSubscribedInSearchCount,
            timeSpentOnSearchSeconds = getSearchScreenTimeSpent()
        )

        val backStep = if (state.selectedGenres.isNotEmpty()) OnboardingStep.PODCASTS else OnboardingStep.GENRES
        _uiState.update { it.copy(currentStep = backStep, searchQuery = "", searchResults = emptyList()) }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        // Debounce search
        searchJob?.cancel()
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(400) // Debounce
            
            val results = podcastRepository.searchPodcasts(cleaned)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }

            // Analytics: Track search performed
            searchesPerformedCount++
            AnalyticsHelper.trackSearchPerformed(cleaned, results.size)
        }
    }
    
    fun subscribeFromSearch(podcast: Podcast) {
        _uiState.update { state ->
            val newSelected = state.selectedPodcasts + (podcast.id to podcast)
            
            // Allow this podcast to appear in the main recommendation list too
            val newRecommendations = if (state.recommendedPodcasts.any { it.id == podcast.id }) {
                state.recommendedPodcasts
            } else {
                state.recommendedPodcasts + podcast
            }
            
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys,
                recommendedPodcasts = newRecommendations
            )
        }

        // Analytics: Track podcast subscribed in search
        podcastsSubscribedInSearchCount++
        AnalyticsHelper.trackSearchPodcastSubscribed(
            podcastName = podcast.title,
            podcastId = podcast.id,
            totalSubscribedCount = _uiState.value.subscribedPodcastIds.size
        )
    }
    
    fun completeOnboarding(onDone: () -> Unit) {
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            val state = _uiState.value
            // Subscribe to all selected podcasts accumulated across regions and search
            val podcastsToSubscribe = state.selectedPodcasts.values
            for (podcast in podcastsToSubscribe) {
                subscriptionRepository.subscribe(podcast)
            }
            
            // Persist the home region preference selection upon completion
            userPrefs.setRegion(state.currentRegion)
            
            // Mark onboarding as completed
            prefs.edit().putBoolean("onboarding_completed", true).apply()

            // Analytics: If completing from search screen, fire search_done variant
            if (state.currentStep == OnboardingStep.SEARCH) {
                AnalyticsHelper.trackOnboardingSearchDone(
                    entryPoint = searchEntryPoint,
                    totalSubscribedCount = state.subscribedPodcastIds.size,
                    searchesPerformed = searchesPerformedCount,
                    timeSpentOnSearchSeconds = getSearchScreenTimeSpent(),
                    timeSpentOnGenreScreenSeconds = getGenreScreenTimeSpent(),
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                    selectedGenres = state.selectedGenres
                )
            } else if (state.currentStep == OnboardingStep.PODCASTS) {
                val removedCount = state.recommendedPodcasts.count { it.id !in state.subscribedPodcastIds }
                AnalyticsHelper.trackOnboardingSuggestionsDone(
                    totalSubscribedCount = state.subscribedPodcastIds.size,
                    removedSuggestionsCount = removedCount,
                    addedFromSearchCount = podcastsSubscribedInSearchCount,
                    didScrollSuggestions = didScrollSuggestions,
                    timeSpentOnPodcastScreenSeconds = getPodcastScreenTimeSpent(),
                    timeSpentOnGenreScreenSeconds = getGenreScreenTimeSpent(),
                    totalOnboardingTimeSeconds = getTotalOnboardingTime()
                )
            }

            // Save selected genres for future personalization
            prefs.edit().putStringSet("user_genres", state.selectedGenres).apply()

            onDone()
        }
    }
    
    fun skipOnboarding(onDone: () -> Unit) {
        // Analytics: Track skip
        val currentScreen = when (_uiState.value.currentStep) {
            OnboardingStep.WELCOME -> "welcome_screen"
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.PODCASTS -> "podcast_screen"
            OnboardingStep.SEARCH -> "search_screen"
            OnboardingStep.AI_ONBOARDING -> "ai_onboarding_screen"
            OnboardingStep.AI_SUGGESTIONS -> "ai_suggestions_screen"
        }
        val podcastTime = if (_uiState.value.currentStep == OnboardingStep.PODCASTS) getPodcastScreenTimeSpent() else null
        AnalyticsHelper.trackOnboardingSkipped(currentScreen, getGenreScreenTimeSpent(), getTotalOnboardingTime(), podcastTime)

        prefs.edit().putBoolean("onboarding_completed", true).apply()

        onDone()
    }

    fun updateAiCustomInput(text: String) {
        _uiState.update { state ->
            state.copy(
                aiCustomInputText = text,
                aiSelectedOptions = if (text.isNotEmpty()) emptySet() else state.aiSelectedOptions
            )
        }
    }

    fun toggleAiOption(option: String) {
        _uiState.update { state ->
            if (state.aiCustomInputText.isNotEmpty()) {
                return@update state
            }
            val currentSelected = state.aiSelectedOptions
            val newSelected = if (option in currentSelected) {
                currentSelected - option
            } else {
                currentSelected + option
            }
            state.copy(
                aiSelectedOptions = newSelected
            )
        }
    }

    fun switchToLegacyOnboarding() {
        _uiState.update { it.copy(currentStep = OnboardingStep.GENRES, onboardingError = null) }
    }

    fun retryLastAction() {
        val action = lastFailedAction
        if (action != null) {
            _uiState.update { it.copy(onboardingError = null) }
            action()
        }
    }

    fun sendAiTurnInput() {
        val currentState = _uiState.value
        if (currentState.isAiLoading) return
        val turnInput = (currentState.aiSelectedOptions.toList() + 
            if (currentState.aiCustomInputText.isNotBlank()) listOf(currentState.aiCustomInputText) else emptyList()
        ).joinToString(". ")

        if (turnInput.isBlank()) return

        // Push current turn state to back stack before updating
        turnHistoryState.add(
            TurnState(
                assistantMessage = currentState.aiAssistantMessage,
                options = currentState.aiOptions,
                history = currentState.aiHistory
            )
        )

        val userEntry = OnboardingHistoryEntry(
            role = "user",
            parts = listOf(OnboardingPart(text = turnInput))
        )
        val newHistory = currentState.aiHistory + userEntry

        _uiState.update {
            it.copy(
                aiHistory = newHistory,
                aiSelectedOptions = emptySet(),
                aiCustomInputText = "",
                isAiLoading = true,
                aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
                onboardingError = null
            )
        }

        var action: (() -> Unit)? = null
        action = {
            _uiState.update {
                it.copy(
                    isAiLoading = true,
                    aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
                    onboardingError = null
                )
            }
            viewModelScope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val testInput = turnInput.trim().lowercase()
                    if (testInput == "test_fail" || testInput == "test_error") {
                        throw java.io.IOException("Simulated network failure")
                    } else if (testInput == "test_timeout") {
                        delay(50000)
                    }

                    val response = kotlinx.coroutines.withTimeout(45000) {
                        withContext(Dispatchers.IO) {
                            podcastRepository.api.getOnboardingNextTurn(
                                publicKey = podcastRepository.publicKey,
                                request = OnboardingNextTurnRequest(history = newHistory)
                            ).execute()
                        }
                    }

                    if (testInput == "test_delay" || testInput == "test_slow") {
                        val elapsed = System.currentTimeMillis() - startTime
                        val remainingDelay = 20000 - elapsed
                        if (remainingDelay > 0) {
                            delay(remainingDelay)
                        }
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val assistantEntry = OnboardingHistoryEntry(
                            role = "model",
                            parts = listOf(OnboardingPart(text = body.assistantMessage))
                        )
                        if (body.options.isEmpty()) {
                            _uiState.update { state ->
                                state.copy(
                                    aiHistory = state.aiHistory + assistantEntry,
                                    aiAssistantMessage = body.assistantMessage,
                                    aiOptions = emptyList(),
                                    aiCurrentTurn = state.aiCurrentTurn + 1,
                                    isAiLoading = false,
                                    aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                                    onboardingError = null
                                )
                            }
                            this@OnboardingViewModel.synthesizeAndBuildCurriculum(force = true)
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    aiHistory = state.aiHistory + assistantEntry,
                                    aiAssistantMessage = body.assistantMessage,
                                    aiOptions = body.options,
                                    aiCurrentTurn = state.aiCurrentTurn + 1,
                                    isAiLoading = false,
                                    aiLoadingStage = AiLoadingStage.IDLE,
                                    onboardingError = null
                                )
                            }
                        }
                    } else {
                        throw Exception("Server returned error ${response.code()}")
                    }
                } catch (e: Exception) {
                    val userFriendlyMsg = when (e) {
                        is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to catch its breath. Let's try that again."
                        else -> "We encountered a temporary hiccup. Let's try that again."
                    }
                    lastFailedAction = action
                    _uiState.update { state ->
                        state.copy(
                            isAiLoading = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            onboardingError = userFriendlyMsg
                        )
                    }
                }
            }
        }
        action()
    }

    fun synthesizeAndBuildCurriculum(force: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isAiLoading && !force) return
        val turnInput = (currentState.aiSelectedOptions.toList() + 
            if (currentState.aiCustomInputText.isNotBlank()) listOf(currentState.aiCustomInputText) else emptyList()
        ).joinToString(". ")

        val newHistoryList = currentState.aiHistory.toMutableList()
        if (turnInput.isNotBlank()) {
            newHistoryList.add(
                OnboardingHistoryEntry(
                    role = "user",
                    parts = listOf(OnboardingPart(text = turnInput))
                )
            )
        }
        if (newHistoryList.isEmpty()) {
            newHistoryList.add(
                OnboardingHistoryEntry(
                    role = "user",
                    parts = listOf(OnboardingPart(text = "Recommend some top topics like True Crime or Tech"))
                )
            )
        }

        _uiState.update {
            it.copy(
                aiHistory = newHistoryList,
                aiSelectedOptions = emptySet(),
                aiCustomInputText = "",
                isAiLoading = true,
                isSynthesizing = true,
                aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                onboardingError = null
            )
        }

        var action: (() -> Unit)? = null
        action = {
            _uiState.update {
                it.copy(
                    isAiLoading = true,
                    isSynthesizing = true,
                    aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                    onboardingError = null
                )
            }
            viewModelScope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val testInput = turnInput.trim().lowercase()
                    if (testInput == "test_fail" || testInput == "test_error") {
                        throw java.io.IOException("Simulated network failure")
                    } else if (testInput == "test_timeout") {
                        delay(50000)
                    }

                    // Call Synthesize to get queries
                    val queries = kotlinx.coroutines.withTimeout(45000) {
                        val synthResponse = withContext(Dispatchers.IO) {
                            podcastRepository.api.onboardingSynthesize(
                                publicKey = podcastRepository.publicKey,
                                request = OnboardingNextTurnRequest(history = newHistoryList)
                            ).execute()
                        }
                        if (synthResponse.isSuccessful && synthResponse.body() != null) {
                            synthResponse.body()!!
                        } else {
                            throw Exception("Failed to synthesize preferences")
                        }
                    }

                    _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS) }

                    // Call Curriculum with synthesized queries
                    val rows = kotlinx.coroutines.withTimeout(45000) {
                        val curriculumResponse = withContext(Dispatchers.IO) {
                            podcastRepository.api.getOnboardingCurriculum(
                                publicKey = podcastRepository.publicKey,
                                request = OnboardingCurriculumRequest(
                                    queries = queries,
                                    country = currentState.currentRegion
                                )
                            ).execute()
                        }
                        if (curriculumResponse.isSuccessful && curriculumResponse.body() != null) {
                            curriculumResponse.body()!!.map {
                                it.copy(episodes = emptyList())
                            }
                        } else {
                            throw Exception("Failed to load curriculum from synthesis")
                        }
                    }

                    _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.ASSEMBLING_FEED) }

                    if (testInput == "test_delay" || testInput == "test_slow") {
                        val elapsed = System.currentTimeMillis() - startTime
                        val remainingDelay = 20000 - elapsed
                        if (remainingDelay > 0) {
                            delay(remainingDelay)
                        }
                    }

                    val newPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }
                    val defaultSelectedIds = buildSet {
                        if (rows.size == 1) {
                            rows.firstOrNull()?.podcasts?.take(2)?.forEach { add(it.id.toString()) }
                        } else {
                            rows.forEach { row ->
                                row.podcasts.firstOrNull()?.let { add(it.id.toString()) }
                            }
                        }
                    }
                    val defaultSelectedPodcasts = newPodcasts.filter { it.id.toString() in defaultSelectedIds }.associateBy { it.id }
                    
                    _uiState.update { state ->
                        val newSelected = state.selectedPodcasts + defaultSelectedPodcasts
                        state.copy(
                            aiHistory = newHistoryList,
                            aiCurriculumRows = rows,
                            aiCurrentTurn = 4,
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            selectedPodcasts = newSelected,
                            subscribedPodcastIds = defaultSelectedIds,
                            onboardingError = null
                        )
                    }
                } catch (e: Exception) {
                    try {
                        _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS) }
                        val trending = withContext(Dispatchers.IO) {
                            podcastRepository.getTrendingPodcasts(
                                country = currentState.currentRegion,
                                limit = 10
                            )
                        }
                        val dummyPodcastDtos = trending.map { pod ->
                            OnboardingCurriculumPodcastDto(
                                id = pod.id.toLongOrNull() ?: 0L,
                                title = pod.title,
                                author = pod.artist,
                                image = pod.imageUrl,
                                artwork = pod.imageUrl,
                                categories = mapOf("1" to pod.genre)
                            )
                        }

                        val fallbackRow = OnboardingCurriculumRowDto(
                            rowTitle = "Trending Hits",
                            podcasts = dummyPodcastDtos,
                            episodes = emptyList()
                        )
                        val newPodcasts = listOf(fallbackRow).flatMap { it.podcasts }.map { it.toPodcast() }
                        val defaultSelectedIds = buildSet {
                            fallbackRow.podcasts.take(2).forEach { add(it.id.toString()) }
                        }
                        val defaultSelectedPodcasts = newPodcasts.filter { it.id.toString() in defaultSelectedIds }.associateBy { it.id }
                        _uiState.update { state ->
                            val newSelected = state.selectedPodcasts + defaultSelectedPodcasts
                            state.copy(
                                aiHistory = newHistoryList,
                                aiCurriculumRows = listOf(fallbackRow),
                                aiCurrentTurn = 4,
                                isAiLoading = false,
                                isSynthesizing = false,
                                aiLoadingStage = AiLoadingStage.IDLE,
                                selectedPodcasts = newSelected,
                                subscribedPodcastIds = defaultSelectedIds,
                                onboardingError = null
                            )
                        }
                    } catch (ex: Exception) {
                        val userFriendlyMsg = when (e) {
                            is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to build your feed. Let's try that again."
                            else -> "We encountered a temporary hiccup. Let's try that again."
                        }
                        lastFailedAction = action
                        _uiState.update { state ->
                            state.copy(
                                isAiLoading = false,
                                isSynthesizing = false,
                                aiLoadingStage = AiLoadingStage.IDLE,
                                onboardingError = userFriendlyMsg
                            )
                        }
                    }
                }
            }
        }
        action()
    }

    fun navigateToSuggestions() {
        _uiState.update { it.copy(currentStep = OnboardingStep.AI_SUGGESTIONS) }
    }

    fun navigateBackFromSuggestions() {
        _uiState.update { it.copy(currentStep = OnboardingStep.AI_ONBOARDING) }
    }

    fun navigateBackInAiOnboarding() {
        val currentState = _uiState.value
        if (currentState.aiCurrentTurn <= 1) {
            _uiState.update { it.copy(currentStep = OnboardingStep.WELCOME) }
        } else {
            if (turnHistoryState.isNotEmpty()) {
                val previousState = turnHistoryState.removeAt(turnHistoryState.size - 1)
                _uiState.update {
                    it.copy(
                        aiAssistantMessage = previousState.assistantMessage,
                        aiOptions = previousState.options,
                        aiHistory = previousState.history,
                        aiCurrentTurn = currentState.aiCurrentTurn - 1,
                        aiSelectedOptions = emptySet(),
                        aiCustomInputText = "",
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiCurriculumRows = emptyList()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        aiCurrentTurn = 1,
                        aiHistory = emptyList(),
                        aiSelectedOptions = emptySet(),
                        aiCustomInputText = "",
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiCurriculumRows = emptyList()
                    )
                }
            }
        }
    }

    fun finishAiOnboarding(onDone: () -> Unit) {
        if (_uiState.value.isCompleting) return
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val selectedIds = state.subscribedPodcastIds
                val rows = state.aiCurriculumRows
                val allCurriculumPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }.distinctBy { it.id }
                val podcastsToSubscribe = allCurriculumPodcasts.filter { it.id in selectedIds }
                for (podcast in podcastsToSubscribe) {
                    subscriptionRepository.subscribe(podcast)
                }

                userPrefs.setRegion(state.currentRegion)
                prefs.edit().putBoolean("onboarding_completed", true).apply()

                val userGenres = _uiState.value.aiHistory
                    .filter { it.role == "user" }
                    .flatMap { it.parts }
                    .map { it.text }
                    .toSet()
                prefs.edit().putStringSet("user_genres", userGenres).apply()

                onDone()
            } catch (e: Exception) {
                prefs.edit().putBoolean("onboarding_completed", true).apply()
                onDone()
            }
        }
    }

    fun markOnboardingCompletedSilent(onDone: () -> Unit) {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        onDone()
    }
}
