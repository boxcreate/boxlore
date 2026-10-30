package cx.aswin.boxlore.feature.onboarding

/**
 * Search and import stop on a choice before any AI request: see similar shows, or go to Home with
 * the picked or imported shows followed. Importers already have a library, so Home leads there;
 * searchers picked a few shows and are still exploring, so similar shows leads.
 */
internal object SimilarShowsChoice {
    private fun OnboardingUiState.searchOrImport() = reachedSuggestionsViaSearchFlow || reachedSuggestionsViaOpmlFlow

    /** The choice is on screen. */
    fun showing(state: OnboardingUiState): Boolean = state.similarChoicePending && state.searchOrImport() && !state.isCompleting

    /** Import leads with Go to Home; search with See similar shows. */
    fun homeIsPrimary(state: OnboardingUiState): Boolean = state.reachedSuggestionsViaOpmlFlow

    /** Analytics path name. */
    fun path(state: OnboardingUiState): String = if (state.reachedSuggestionsViaOpmlFlow) "import" else "search"

    /** Suggestions the listener asked for are loading, and they can still leave for Home. */
    fun canSkipLoading(state: OnboardingUiState, loading: Boolean): Boolean = loading && state.searchOrImport() && !state.similarChoicePending && !state.isCompleting

    /** A similar-shows answer still belongs on screen: not after leaving for Home or going back. */
    fun acceptsResult(state: OnboardingUiState): Boolean = state.currentStep == OnboardingStep.AI_SUGGESTIONS && !state.isCompleting && !state.similarChoicePending
}
