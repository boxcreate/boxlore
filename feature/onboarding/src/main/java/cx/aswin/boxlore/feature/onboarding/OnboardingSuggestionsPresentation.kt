package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto

/**
 * Pure presentation helpers for the shared suggestions screen (genre / AI / search / OPML).
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

    /** Finish-bar CTA on the shared suggestions screen. */
    fun finishCtaLabel(
        uiState: OnboardingUiState,
        selectedCount: Int,
    ): String {
        val isSeedFlow =
            uiState.reachedSuggestionsViaSearchFlow || uiState.reachedSuggestionsViaOpmlFlow
        if (isSeedFlow) {
            val recommendedIds =
                uiState.aiCurriculumRows
                    .flatMap { it.podcasts }
                    .map { it.id.toString() }
                    .toSet()
            val selectedRecommendationsCount =
                uiState.subscribedPodcastIds.count { it in recommendedIds }
            return if (selectedRecommendationsCount > 0) {
                "Add $selectedRecommendationsCount & start"
            } else {
                "Start without adding"
            }
        }
        return if (selectedCount > 0) {
            "Subscribe & start · $selectedCount"
        } else {
            "Start without subscribing"
        }
    }

    data class LoadingCopy(
        val title: String,
        val subtitle: String,
    )

    fun loadingCopy(uiState: OnboardingUiState): LoadingCopy = when {
        uiState.reachedSuggestionsViaOpmlFlow ->
            LoadingCopy(
                title = "Import complete",
                subtitle = "Finding shows inspired by your library…",
            )
        uiState.reachedSuggestionsViaSearchFlow ->
            LoadingCopy(
                title =
                if (uiState.suggestionSeedCount > 0) {
                    "Subscribed to ${uiState.suggestionSeedCount} " +
                        if (uiState.suggestionSeedCount == 1) "show" else "shows"
                } else {
                    "Shows subscribed"
                },
                subtitle = "Looking for more you might like…",
            )
        else ->
            LoadingCopy(
                title = "Designing your feed",
                subtitle = "Matching shows to your taste…",
            )
    }

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
