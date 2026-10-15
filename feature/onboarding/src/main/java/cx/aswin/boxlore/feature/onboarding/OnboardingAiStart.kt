package cx.aswin.boxlore.feature.onboarding

/** Pure state steps for starting Echo and for leaving or discarding built suggestions. */
internal object OnboardingAiStart {
    /** A restart clears the conversation but keeps the listener's stored region. */
    fun freshState(previous: OnboardingUiState): OnboardingUiState =
        OnboardingUiState(
            currentStep = OnboardingStep.AI_ONBOARDING,
            currentRegion = previous.currentRegion,
            initialRegion = previous.initialRegion,
        )

    /** Building opens suggestions at once; they show their own loading state until rows arrive. */
    fun buildingState(state: OnboardingUiState): OnboardingUiState =
        withoutResults(state).copy(
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            reachedSuggestionsViaAiFlow = true,
            reachedSuggestionsViaSearchFlow = false,
            reachedSuggestionsViaOpmlFlow = false,
        )

    /**
     * Drops built rows and the picks inside them, so a rebuild never subscribes shows the listener
     * can no longer see. Search side-trip picks outside the rows stay selected.
     */
    fun withoutResults(state: OnboardingUiState): OnboardingUiState {
        val rowIds = state.aiCurriculumRows.flatMap { it.podcasts }.map { it.id.toString() }.toSet()
        if (rowIds.isEmpty() && state.aiCurriculumRows.isEmpty()) return state
        return state.copy(
            aiCurriculumRows = emptyList(),
            subscribedPodcastIds = state.subscribedPodcastIds - rowIds,
            selectedPodcasts = state.selectedPodcasts - rowIds,
        )
    }
}
