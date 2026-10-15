package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto

internal enum class SuggestionsOrigin { Echo, Genres, Search, Library }

/**
 * Pure presentation helpers for the shared suggestions screen (genre / AI / search / OPML). The
 * screen never pre-selects suggestions: the listener adds what they want.
 */
internal object OnboardingSuggestionsPresentation {
    fun isLoading(uiState: OnboardingUiState): Boolean {
        val awaitingContent =
            uiState.isLoadingPodcasts || uiState.isAiLoading || uiState.isSynthesizing
        return awaitingContent &&
            uiState.aiCurriculumRows.isEmpty() &&
            uiState.genreChartsPodcasts.isEmpty()
    }

    fun isError(uiState: OnboardingUiState): Boolean = uiState.onboardingError != null &&
        uiState.aiCurriculumRows.isEmpty() &&
        uiState.genreChartsPodcasts.isEmpty()

    /**
     * Drops seed / already-picked show IDs from similar-shows curriculum rows so the
     * suggestions grid only offers new shows. Empty rows are removed.
     */
    fun filterCurriculumRowsExcluding(
        rows: List<OnboardingCurriculumRowDto>,
        excludeIds: Set<String>,
    ): List<OnboardingCurriculumRowDto> {
        if (excludeIds.isEmpty()) return rows.filter { it.podcasts.isNotEmpty() }
        return rows.mapNotNull { row ->
            val kept =
                row.podcasts.filter { podcast ->
                    podcast.id.toString() !in excludeIds
                }
            if (kept.isEmpty()) null else row.copy(podcasts = kept)
        }
    }

    /** Which flow reached the shared screen; each sets its own header and counting. */
    fun origin(uiState: OnboardingUiState): SuggestionsOrigin = when {
        uiState.reachedSuggestionsViaAiFlow -> SuggestionsOrigin.Echo
        uiState.reachedSuggestionsViaSearchFlow -> SuggestionsOrigin.Search
        uiState.reachedSuggestionsViaOpmlFlow -> SuggestionsOrigin.Library
        else -> SuggestionsOrigin.Genres
    }

    /** Every show the screen offers (lanes and charts). */
    fun recommendationIds(uiState: OnboardingUiState): Set<String> =
        (uiState.aiCurriculumRows.flatMap { it.podcasts }.map { it.id.toString() } + uiState.genreChartsPodcasts.map { it.id }).toSet()

    /**
     * How many shows the finish button adds. Search picks and imported libraries are subscribed
     * before suggestions load, so those flows count only the suggestions picked here; elsewhere
     * every selected show counts (Echo's taste-test and search picks included).
     */
    fun addCount(uiState: OnboardingUiState): Int = when (origin(uiState)) {
        SuggestionsOrigin.Search, SuggestionsOrigin.Library -> {
            val offered = recommendationIds(uiState)
            uiState.subscribedPodcastIds.count { it in offered }
        }
        else -> uiState.subscribedPodcastIds.size
    }

    /** The search picks or imported shows already in the library, for the summary at the top. */
    fun seedShows(uiState: OnboardingUiState): List<Podcast> {
        val offered = recommendationIds(uiState)
        return uiState.selectedPodcasts.values.filter { it.id !in offered }
    }

    /**
     * Lanes in order. In Echo's flow the shows picked before the build (a positive taste-test
     * answer, or a search mid-chat) lead in their own lane, selected, and leave the other lanes so
     * nothing shows twice. Nothing else starts selected.
     */
    fun lanes(uiState: OnboardingUiState, picksTitle: String): List<OnboardingSuggestionsLane> {
        val base = OnboardingSuggestionsLanes.deduplicateLanes(
            OnboardingSuggestionsLanes.build(uiState.aiCurriculumRows, uiState.genreChartsPodcasts, uiState.selectedGenres),
        )
        val picks = OnboardingSuggestionsLanes.deduplicatePodcasts(uiState.aiPicks)
        if (origin(uiState) != SuggestionsOrigin.Echo || picks.isEmpty()) return base
        val pickIds = picks.map { it.id }.toSet()
        val rest = base.map { lane -> lane.copy(podcasts = lane.podcasts.filter { it.id !in pickIds }) }.filter { it.isCharts || it.podcasts.isNotEmpty() }
        return listOf(OnboardingSuggestionsLane(PICKS_LANE_ID, picksTitle, purpose = "", isCharts = false, podcasts = picks)) + rest
    }

    const val PICKS_LANE_ID = "picks"

    /** Whether back from suggestions should wipe curriculum/charts (search / OPML paths). */
    fun shouldClearSuggestionPayloadOnBack(nextStep: OnboardingStep): Boolean = nextStep == OnboardingStep.SEARCH || nextStep == OnboardingStep.WELCOME

    /** Clears suggestion payloads / loading flags while preserving picks and search query. */
    fun withClearedSuggestionPayload(
        state: OnboardingUiState,
        nextStep: OnboardingStep,
    ): OnboardingUiState = state.copy(
        currentStep = nextStep,
        aiCurriculumRows = emptyList(),
        genreChartsPodcasts = emptyList(),
        isAiLoading = false,
        isSynthesizing = false,
        isLoadingPodcasts = false,
        aiLoadingStage = AiLoadingStage.IDLE,
        onboardingError = null,
        suggestionSeedCount = 0,
        reachedSuggestionsViaSearchFlow = false,
        reachedSuggestionsViaOpmlFlow = false,
        reachedSuggestionsViaAiFlow = false,
    )
}
