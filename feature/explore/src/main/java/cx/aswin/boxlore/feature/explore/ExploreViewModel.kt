package cx.aswin.boxlore.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.ExploreDefaultTab
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SearchResult
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.PodcastRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.getHistoryForRecommendations
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.explore.logic.ExploreBrowseLogic
import cx.aswin.boxlore.feature.explore.logic.ExploreSharedRecommendationsLogic

enum class SearchTab { SHOWS, EPISODES }

sealed interface ExploreUiState {
    data object Loading : ExploreUiState
    data class Success(
        val trending: List<Podcast> = emptyList(),
        val searchResults: List<Podcast> = emptyList(),
        val recommendations: List<Episode> = emptyList(),
        val subscribedIds: Set<String> = emptySet(), // For badging
        val currentCategory: String = "All",
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val isLoading: Boolean = false, // For showing skeleton in grid area only
        val currentVibe: String? = null,
        val suggestedVibes: List<Pair<String, String>> = emptyList(),
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val selectedTab: Int = 1, // 0 for Trending, 1 for For You (default)
        val isRecommendationsLoading: Boolean = false,
        val isRecommendationsFallback: Boolean = true,
        val searchTab: SearchTab = SearchTab.SHOWS,
        val semanticSearchResults: List<Episode> = emptyList(),
        /** Podcast-vector hits from the same semantic request (additive `feeds`). */
        val semanticPodcastResults: List<Podcast> = emptyList(),
        val isSemanticLoading: Boolean = false,
        val hasPerformedSemanticSearch: Boolean = false,
        /** Hybrid `/search` hits not already in Meili typeahead. */
        val alsoFoundResults: List<Podcast> = emptyList(),
    ) : ExploreUiState
    data class Error(val message: String) : ExploreUiState
}

private data class ExplorePrimarySlice(
    val subscribedIds: Set<String>,
    val category: String,
    val trending: List<Podcast>,
    val searchResults: List<Podcast>,
    val query: String,
)

private data class ExploreLoadingSlice(
    val isLoading: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
)

private data class ExploreRecsSlice(
    val currentVibe: String?,
    val suggestedVibes: List<Pair<String, String>>,
    val selectedTab: Int,
    val recommendations: List<Episode>,
    val isRecommendationsLoading: Boolean,
    val isRecommendationsFallback: Boolean,
)

private data class ExploreSearchSlice(
    val searchTab: SearchTab,
    val semanticSearchResults: List<Episode>,
    val semanticPodcastResults: List<Podcast>,
    val isSemanticLoading: Boolean,
    val hasPerformedSemanticSearch: Boolean,
    val alsoFoundResults: List<Podcast>,
)

class ExploreViewModel(
    application: android.app.Application,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    private val playbackRepository: PlaybackRepository,
    private val adaptiveScorer: AdaptiveCandidateScorer,
    initialCategory: String? = null,
    initialTab: String? = null,
) : androidx.lifecycle.AndroidViewModel(application) {

    private val initialSelectedTab =
        ExploreDefaultTab.resolveIndex(
            navTab = initialTab,
            hasCategory = initialCategory != null,
            preferred = userPrefs.cachedExploreDefaultTab,
        )

    private val _uiState = MutableStateFlow<ExploreUiState>(
        ExploreUiState.Success(
            trending = emptyList(),
            searchResults = emptyList(),
            recommendations = emptyList(),
            subscribedIds = emptySet(),
            currentCategory = initialCategory ?: "All",
            searchQuery = "",
            isSearching = false,
            isLoading = true,
            currentVibe = null,
            suggestedVibes = emptyList(),
            isLoadingMore = false,
            selectedTab = initialSelectedTab,
            isRecommendationsLoading = false,
            isRecommendationsFallback = true,
            searchTab = SearchTab.SHOWS,
            semanticSearchResults = emptyList(),
            semanticPodcastResults = emptyList(),
            isSemanticLoading = false,
            hasPerformedSemanticSearch = false,
            alsoFoundResults = emptyList(),
        )
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()
    val playerState = playbackRepository.playerState

    // Internal state to combine
    private val _searchQuery = MutableStateFlow("")
    private val _currentCategory = MutableStateFlow(initialCategory ?: "All") // Use it here
    private val _trendingPodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Podcast>>(emptyList())
    private val _selectedTab = MutableStateFlow(initialSelectedTab)
    private val _recommendations = MutableStateFlow<List<Episode>>(emptyList())
    private val _isRecommendationsLoading = MutableStateFlow(false)
    private val _isRecommendationsFallback = MutableStateFlow(true)
    private val _searchTab = MutableStateFlow(SearchTab.SHOWS)
    private val _semanticSearchResults = MutableStateFlow<List<Episode>>(emptyList())
    private val _semanticPodcastResults = MutableStateFlow<List<Podcast>>(emptyList())
    private val _isSemanticLoading = MutableStateFlow(false)
    private val _hasPerformedSemanticSearch = MutableStateFlow(false)
    private val _alsoFoundResults = MutableStateFlow<List<Podcast>>(emptyList())

    // Seen/cached podcasts for eager zero-latency substring client-side matching
    private val _seenPodcasts = java.util.concurrent.ConcurrentHashMap<String, Podcast>()
    private val _localSubstringResults = MutableStateFlow<List<Podcast>>(emptyList())
    
    // Combine local substring matches and Meili/catalog remote results (also-found stays separate)
    private val _combinedSearchResults = combine(_localSubstringResults, _searchResults) { local, remote ->
        val seenIds = mutableSetOf<String>()
        val combined = mutableListOf<Podcast>()
        
        remote.forEach {
            if (seenIds.add(it.id)) {
                combined.add(it)
            }
        }
        local.forEach {
            if (seenIds.add(it.id)) {
                combined.add(it)
            }
        }
        combined
    }

    private val _isLoading = MutableStateFlow(true) // Explicit loading state
    
    // Vibe Prompt State
    private val _currentVibe = MutableStateFlow<String?>(null)
    private val _suggestedVibes = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    
    // Search Job to cancel previous searches
    private var searchJob: Job? = null

    // Pagination State
    private var currentOffset = 0
    private val PAGE_SIZE = 20
    private val searchTieWindow = 4
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMorePages = MutableStateFlow(true)

    // Telemetry State
    private var sessionStartTime = System.currentTimeMillis()
    private var categoriesClickedCount = 0
    private var vibesClickedCount = 0
    private var searchesPerformedCount = 0
    private var podcastsClickedCount = 0
    private var maxScrollDepth = 0
    private var hasTrackedExit = false

    // Active charts region (shown as a chip on the Trending header)
    private val _activeRegionCode = MutableStateFlow("us")
    val activeRegionCode: StateFlow<String> = _activeRegionCode.asStateFlow()


    init {
        // Preload subscriptions into _seenPodcasts cache to enable instant zero-latency filtering
        viewModelScope.launch {
            subscriptionRepository.subscribedPodcasts.collect { subscribed ->
                subscribed.forEach { podcast ->
                    _seenPodcasts[podcast.id] = podcast
                }
            }
        }

        // Observe Subscriptions for Badging
        viewModelScope.launch {
            val primaryState = combine(
                combine(
                    subscriptionRepository.subscribedPodcastIds,
                    _currentCategory,
                    _trendingPodcasts,
                    _combinedSearchResults,
                    _searchQuery,
                ) { subIds, category, trending, searchRes, query ->
                    ExplorePrimarySlice(subIds, category, trending, searchRes, query)
                },
                combine(_isLoading, _isLoadingMore, _hasMorePages) { loading, loadingMore, hasMore ->
                    ExploreLoadingSlice(loading, loadingMore, hasMore)
                },
            ) { primary, loading -> primary to loading }

            val secondaryState = combine(
                combine(
                    _currentVibe,
                    _suggestedVibes,
                    _selectedTab,
                    _recommendations,
                    _isRecommendationsLoading,
                ) { vibe, vibes, selectedTab, recommendations, isRecommendationsLoading ->
                    ExploreRecsSlice(
                        vibe,
                        vibes,
                        selectedTab,
                        recommendations,
                        isRecommendationsLoading,
                        isRecommendationsFallback = true,
                    )
                }.combine(_isRecommendationsFallback) { slice, isFallback ->
                    slice.copy(isRecommendationsFallback = isFallback)
                },
                combine(
                    combine(
                        _searchTab,
                        _semanticSearchResults,
                        _semanticPodcastResults,
                    ) { searchTab, semanticSearchResults, semanticPodcastResults ->
                        Triple(searchTab, semanticSearchResults, semanticPodcastResults)
                    },
                    combine(
                        _isSemanticLoading,
                        _hasPerformedSemanticSearch,
                        _alsoFoundResults,
                    ) { isSemanticLoading, hasPerformedSemanticSearch, alsoFound ->
                        Triple(isSemanticLoading, hasPerformedSemanticSearch, alsoFound)
                    },
                ) { semantic, meta ->
                    ExploreSearchSlice(
                        searchTab = semantic.first,
                        semanticSearchResults = semantic.second,
                        semanticPodcastResults = semantic.third,
                        isSemanticLoading = meta.first,
                        hasPerformedSemanticSearch = meta.second,
                        alsoFoundResults = meta.third,
                    )
                },
            ) { recs, search -> recs to search }

            primaryState.combine(secondaryState) { (primary, loading), (recs, search) ->
                val isSearching = primary.query.isNotEmpty() || recs.currentVibe != null
                ExploreUiState.Success(
                    trending = primary.trending,
                    searchResults = primary.searchResults,
                    recommendations = recs.recommendations,
                    subscribedIds = primary.subscribedIds,
                    currentCategory = primary.category,
                    searchQuery = primary.query,
                    isSearching = isSearching,
                    isLoading = loading.isLoading,
                    currentVibe = recs.currentVibe,
                    suggestedVibes = recs.suggestedVibes,
                    isLoadingMore = loading.isLoadingMore,
                    hasMore = loading.hasMore,
                    selectedTab = recs.selectedTab,
                    isRecommendationsLoading = recs.isRecommendationsLoading,
                    isRecommendationsFallback = recs.isRecommendationsFallback,
                    searchTab = search.searchTab,
                    semanticSearchResults = search.semanticSearchResults,
                    semanticPodcastResults = search.semanticPodcastResults,
                    isSemanticLoading = search.isSemanticLoading,
                    hasPerformedSemanticSearch = search.hasPerformedSemanticSearch,
                    alsoFoundResults = search.alsoFoundResults,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Search Debounce Implementation
        startSearchObserver()
        
        // Initial Load
        loadAllVibes()
        
        viewModelScope.launch {
            combine(_currentCategory, userPrefs.regionStream) { category, region ->
                category to region
            }.collectLatest { (category, region) ->
                loadTrending(category, region)
            }
        }

        // Hydrate For You from the shared Home/Explore BoxcastPrefs cache (same payload Home writes).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prefs = BoxcastPrefs(getApplication())
                val cached = ExploreSharedRecommendationsLogic.decodeCachedRecommendationsJson(
                    prefs.getCachedRecommendationsJson(),
                )
                if (cached.isNotEmpty()) {
                    _recommendations.value = cached
                }
                _isRecommendationsFallback.value = prefs.isRecommendationsFallback()
            } catch (e: Exception) {
                android.util.Log.e("ExploreViewModel", "Failed to load shared recommendations cache", e)
            }
        }

        viewModelScope.launch {
            combine(
                userPrefs.regionStream,
                subscriptionRepository.subscribedPodcastIds
            ) { _, _ -> }.collect {
                fetchPersonalizedRecommendations()
            }
        }
        
        // Keep active region label in sync for the charts header chip.
        viewModelScope.launch {
            userPrefs.regionStream.collect { region ->
                _activeRegionCode.value = region
            }
        }
    }

    private fun getSortedVibesForTimeOfDay(): List<Pair<String, String>> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return ExploreBrowseLogic.vibesForHour(hour)
    }

    private fun loadAllVibes() {
        _suggestedVibes.value = getSortedVibesForTimeOfDay()
    }

    @OptIn(FlowPreview::class)
    private fun startSearchObserver() {
        // Show typeahead: short debounce (Meili is cheap)
        _searchQuery
            .debounce(300L)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isNotBlank() && _searchTab.value == SearchTab.SHOWS) {
                    performSearch(query)
                }
            }
            .launchIn(viewModelScope)

        // Episode semantic: long debounce before CF Workers AI embed — avoid partial queries
        _searchQuery
            .debounce(1000L)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isNotBlank() && _searchTab.value == SearchTab.EPISODES) {
                    performSemanticSearch(query)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchTriggered(query: String) {
        if (query.isNotBlank()) {
            if (_searchTab.value == SearchTab.EPISODES) {
                performSemanticSearch(query)
            } else {
                performSearch(query)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        if (query.isNotEmpty() && _currentVibe.value != null) {
            _currentVibe.value = null // Stop showing vibe results if they start typing
            _searchResults.value = emptyList()
        }
        _searchQuery.value = query

        // Eager, zero-latency local filtering as the user types (0ms delay)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _localSubstringResults.value = emptyList()
            _semanticSearchResults.value = emptyList()
            _semanticPodcastResults.value = emptyList()
            _alsoFoundResults.value = emptyList()
            _hasPerformedSemanticSearch.value = false
            _isSemanticLoading.value = false
        } else {
            _localSubstringResults.value =
                ExploreBrowseLogic.filterPodcastsBySubstring(trimmed, _seenPodcasts.values)

            // Concept tab: show loader immediately while we wait out the high embed debounce
            if (_searchTab.value == SearchTab.EPISODES) {
                _isSemanticLoading.value = true
                _hasPerformedSemanticSearch.value = false
            }
        }
    }

    fun onCategorySelected(category: String) {
        if (_currentCategory.value == category) return
        categoriesClickedCount++
        _currentCategory.value = category
        clearVibe()
        // Clear Search when switching category to browse
        _searchQuery.value = "" 
        _localSubstringResults.value = emptyList()
        _trendingPodcasts.value = emptyList() // Clear to force Skeleton
    }
    
    fun onVibeSelected(vibeId: String, vibeName: String) {
        vibesClickedCount++
        _searchQuery.value = ""
        _localSubstringResults.value = emptyList()
        _currentVibe.value = vibeName
        _isLoading.value = true
        _searchResults.value = emptyList()
        _alsoFoundResults.value = emptyList()

        searchJob?.cancel()
        
        var myJob: Job? = null
        myJob = viewModelScope.launch {
            try {
                // Uses same curated endpoint as HomeScreen!
                val region = userPrefs.regionStream.first()
                val languages = userPrefs.contentLanguagesStream.first()
                val results = podcastRepository.getCuratedPodcasts(
                    vibeId = vibeId,
                    country = region,
                    languages = languages,
                )
                if (searchJob == myJob) {
                    _searchResults.value = results
                    results.forEach { _seenPodcasts[it.id] = it }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (searchJob == myJob) {
                    _searchResults.value = emptyList()
                }
            } finally {
                if (searchJob == myJob) {
                    _isLoading.value = false
                }
            }
        }
        searchJob = myJob
    }
    
    fun clearVibe() {
        _currentVibe.value = null
        if (_searchQuery.value.isEmpty()) {
            _searchResults.value = emptyList()
            _alsoFoundResults.value = emptyList()
            _localSubstringResults.value = emptyList()
        }
    }

    private suspend fun loadTrending(category: String, region: String) {
        currentOffset = 0
        _hasMorePages.value = true
        _isLoading.value = true
        try {
            // Map "All" to null for API, and lowercase others for consistency
            val apiCategory = if (category == "All") null else category.lowercase()
            
            // This hits the Turso DB (via Proxy)
            val podcasts = podcastRepository.getTrendingPodcasts(
                country = region, 
                limit = PAGE_SIZE,
                category = apiCategory,
                offset = 0
            )
            _trendingPodcasts.value = podcasts
            podcasts.forEach { _seenPodcasts[it.id] = it }
            _hasMorePages.value = podcasts.size >= PAGE_SIZE
            currentOffset = podcasts.size
        } catch (e: Exception) {
            // Handle error
            _trendingPodcasts.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMoreTrending() {
        if (_isLoadingMore.value || !_hasMorePages.value || _searchQuery.value.isNotEmpty() || _currentVibe.value != null) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val apiCategory = if (_currentCategory.value == "All") null else _currentCategory.value.lowercase()
                val region = userPrefs.regionStream.first()
                val morePodcasts = podcastRepository.getTrendingPodcasts(
                    country = region,
                    limit = PAGE_SIZE,
                    category = apiCategory,
                    offset = currentOffset
                )
                if (morePodcasts.size < PAGE_SIZE) {
                    _hasMorePages.value = false
                }
                currentOffset += morePodcasts.size
                val previous = _trendingPodcasts.value
                val merged = ExploreBrowseLogic.mergeUniqueById(previous, morePodcasts) { it.id }
                val newPodcasts = merged.drop(previous.size)
                _trendingPodcasts.value = merged
                newPodcasts.forEach { _seenPodcasts[it.id] = it }
            } catch (e: Exception) {
                android.util.Log.e("ExploreViewModel", "Load more error", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun performSearch(query: String) {
        if (_currentVibe.value != null) return // Safety check

        searchesPerformedCount++
        searchJob?.cancel()

        if (_searchTab.value == SearchTab.EPISODES) {
            performSemanticSearch(query)
        } else {
            _semanticSearchResults.value = emptyList()
        }
        
        var myJob: Job? = null
        myJob = viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = emptyList()
            _alsoFoundResults.value = emptyList()
            try {
                val grouped = podcastRepository.searchPodcastsGrouped(query)
                if (searchJob == myJob) {
                    val history = playbackRepository.getAllHistory().first()
                    val rankedCatalog = rankPodcastsOrOriginal(grouped.catalog, history)
                    val rankedAlso = rankPodcastsOrOriginal(grouped.alsoFound, history)
                    _searchResults.value = rankedCatalog
                    _alsoFoundResults.value = rankedAlso
                    grouped.all.forEach { _seenPodcasts[it.id] = it }
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreSearchPerformed(
                        query,
                        rankedCatalog.size + rankedAlso.size,
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                if (searchJob == myJob) {
                    _searchResults.value = emptyList()
                    _alsoFoundResults.value = emptyList()
                }
            } finally {
                if (searchJob == myJob) {
                    _isLoading.value = false
                }
            }
        }
        searchJob = myJob
    }

    private var semanticSearchJob: Job? = null

    private fun performSemanticSearch(query: String) {
        semanticSearchJob?.cancel()
        var myJob: Job? = null
        myJob = viewModelScope.launch {
            _isSemanticLoading.value = true
            _semanticSearchResults.value = emptyList()
            _semanticPodcastResults.value = emptyList()
            _hasPerformedSemanticSearch.value = false
            try {
                val region = userPrefs.regionStream.first()
                val grouped = podcastRepository.searchSemanticGrouped(query, region)
                if (semanticSearchJob == myJob) {
                    val history = playbackRepository.getAllHistory().first()
                    val rankedEps = rankEpisodesOrOriginal(grouped.episodes, history)
                    val rankedPods = rankPodcastsOrOriginal(grouped.podcasts, history)
                    _semanticSearchResults.value = rankedEps
                    _semanticPodcastResults.value = rankedPods
                    rankedPods.forEach { _seenPodcasts[it.id] = it }
                    _hasPerformedSemanticSearch.value = true
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreSearchPerformed(
                        query,
                        rankedEps.size + rankedPods.size,
                        searchMode = "concept_semantic",
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                if (semanticSearchJob == myJob) {
                    _semanticSearchResults.value = emptyList()
                    _semanticPodcastResults.value = emptyList()
                    _hasPerformedSemanticSearch.value = true
                }
            } finally {
                if (semanticSearchJob == myJob) {
                    _isSemanticLoading.value = false
                }
            }
        }
        semanticSearchJob = myJob
    }

    private suspend fun rankPodcastsOrOriginal(
        results: List<Podcast>,
        history: List<cx.aswin.boxlore.core.database.ListeningHistoryEntity>? = null,
    ): List<Podcast> {
        return try {
            rankPodcastSearchTies(results, history)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            results
        }
    }

    private suspend fun rankEpisodesOrOriginal(
        results: List<Episode>,
        history: List<cx.aswin.boxlore.core.database.ListeningHistoryEntity>? = null,
    ): List<Episode> {
        return try {
            rankEpisodeSearchTies(results, history)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            results
        }
    }

    private suspend fun rankPodcastSearchTies(
        results: List<Podcast>,
        historyOverride: List<cx.aswin.boxlore.core.database.ListeningHistoryEntity>? = null,
    ): List<Podcast> {
        val history = historyOverride ?: playbackRepository.getAllHistory().first()
        return results.chunked(searchTieWindow).flatMap { window ->
            adaptiveScorer.rankPodcasts(
                inputs = window.map { podcast ->
                    PodcastRankingInput(
                        podcast = podcast,
                        priorScore = 1.0,
                        source = CandidateSource.SERVER_RECOMMENDATION,
                        isNovel = podcast.subscribedAt <= 0L,
                    )
                },
                history = history,
                objective = RankingObjective.DISCOVERY,
                surface = RankingSurface.EXPLORE,
            )
        }
    }

    private suspend fun rankEpisodeSearchTies(
        results: List<Episode>,
        historyOverride: List<cx.aswin.boxlore.core.database.ListeningHistoryEntity>? = null,
    ): List<Episode> {
        val history = historyOverride ?: playbackRepository.getAllHistory().first()
        return results.chunked(searchTieWindow).flatMap { window ->
            val scores = adaptiveScorer.scoreEpisodes(
                inputs = window.map { episode ->
                    EpisodeRankingInput(
                        episode = episode,
                        podcast = episode.toSearchPodcast(),
                        priorScore = 1.0,
                        source = CandidateSource.SERVER_RECOMMENDATION,
                        isNovel = true,
                    )
                },
                history = history,
                objective = RankingObjective.DISCOVERY,
                surface = RankingSurface.EXPLORE,
            )
            window.sortedByDescending { scores[it.id] ?: 0.0 }
        }
    }

    private fun Episode.toSearchPodcast(): Podcast =
        ExploreBrowseLogic.episodeToSearchPodcast(this)

    fun setSearchTab(tab: SearchTab) {
        if (_searchTab.value == tab) return
        _searchTab.value = tab
        val query = _searchQuery.value.trim()
        when {
            tab == SearchTab.EPISODES && query.isNotEmpty() -> {
                if (_semanticSearchResults.value.isEmpty() && _semanticPodcastResults.value.isEmpty()) {
                    performSemanticSearch(query)
                } else {
                    // Already have results for this query — don't leave the loader stuck on.
                    _isSemanticLoading.value = false
                }
            }
            tab == SearchTab.SHOWS && query.isNotEmpty() -> performSearch(query)
        }
    }

    fun trackPodcastClicked(index: Int) {
        podcastsClickedCount++
        if (index > maxScrollDepth) {
            maxScrollDepth = index
        }
    }

    fun onScreenResume() {
        if (hasTrackedExit) {
            // User came back from background or backstack. Restart the session timer.
            // Note: We don't reset the click counts, so it truly acts as one contiguous session.
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackExploreScreenSession(
            timeSpentSeconds = timeSpent,
            categoriesClickedCount = categoriesClickedCount,
            vibesClickedCount = vibesClickedCount,
            searchesPerformedCount = searchesPerformedCount,
            podcastsClickedCount = podcastsClickedCount,
            maxScrollDepth = maxScrollDepth,
            finalCategoryState = _currentCategory.value,
            finalVibeState = _currentVibe.value,
            finalSearchQuery = _searchQuery.value.takeIf { it.isNotBlank() }
        )
    }

    fun onTabSelected(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun fetchPersonalizedRecommendations() {
        viewModelScope.launch {
            _isRecommendationsLoading.value = true
            val prefs = BoxcastPrefs(getApplication())
            try {
                // Prefer shared cache if we already have rows (Home often populated it).
                if (_recommendations.value.isEmpty()) {
                    val cached = ExploreSharedRecommendationsLogic.decodeCachedRecommendationsJson(
                        prefs.getCachedRecommendationsJson(),
                    )
                    if (cached.isNotEmpty()) {
                        _recommendations.value = cached
                        _isRecommendationsFallback.value = prefs.isRecommendationsFallback()
                    }
                }

                val interests = prefs.getUserGenres().toList()
                val history = playbackRepository.getHistoryForRecommendations(15)

                val subscribedIds = subscriptionRepository.subscribedPodcastIds.first().toList()
                val subscribedGenres = subscriptionRepository.subscribedPodcasts.first()
                    .mapNotNull { it.genre }
                    .distinct()

                val region = userPrefs.regionStream.first()
                val languages = userPrefs.contentLanguagesStream.first()

                android.util.Log.d(
                    "ExploreViewModel",
                    "Refreshing shared bootstrap recommendations; history=${history.size}, interests=$interests, region=$region, langs=$languages, subscribed=${subscribedIds.size}",
                )
                // Same catalog path as Home For You — includes popular-in-region when taste seeds are empty.
                val bootstrapData = podcastRepository.getHomeBootstrapData(
                    country = region,
                    vibeIds = emptyList(),
                    history = history,
                    interests = interests,
                    subscribedPodcastIds = subscribedIds,
                    subscribedGenres = subscribedGenres,
                    languages = languages,
                )
                val distinctRecs = ExploreSharedRecommendationsLogic.distinctRecommendations(
                    bootstrapData.recommendations,
                )
                android.util.Log.d(
                    "ExploreViewModel",
                    "Shared recommendations size=${distinctRecs.size}, fallback=${bootstrapData.isRecommendationsFallback}",
                )
                _recommendations.value = distinctRecs
                _isRecommendationsFallback.value = bootstrapData.isRecommendationsFallback
                try {
                    prefs.saveRecommendationsCache(
                        ExploreSharedRecommendationsLogic.encodeRecommendationsJson(distinctRecs),
                        bootstrapData.isRecommendationsFallback,
                    )
                } catch (ce: Exception) {
                    android.util.Log.e("ExploreViewModel", "Failed to write shared recommendations cache", ce)
                }
            } catch (e: Exception) {
                android.util.Log.e("ExploreViewModel", "Failed to fetch shared recommendations", e)
            } finally {
                _isRecommendationsLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        trackScreenExit()
    }
}
