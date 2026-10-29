package cx.aswin.boxlore.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarShowsChoiceTest {
    private val search = OnboardingUiState(currentStep = OnboardingStep.AI_SUGGESTIONS, reachedSuggestionsViaSearchFlow = true, similarChoicePending = true)
    private val import = OnboardingUiState(currentStep = OnboardingStep.AI_SUGGESTIONS, reachedSuggestionsViaOpmlFlow = true, similarChoicePending = true)

    @Test
    fun `search and import ask first, import leading with Home`() {
        assertTrue(SimilarShowsChoice.showing(search))
        assertTrue(SimilarShowsChoice.showing(import))
        assertFalse(SimilarShowsChoice.homeIsPrimary(search))
        assertTrue(SimilarShowsChoice.homeIsPrimary(import))
        assertEquals("search", SimilarShowsChoice.path(search))
        assertEquals("import", SimilarShowsChoice.path(import))
    }

    @Test
    fun `Echo and genres never see the choice or the loading skip`() {
        val echo = OnboardingUiState(currentStep = OnboardingStep.AI_SUGGESTIONS, reachedSuggestionsViaAiFlow = true, similarChoicePending = true)
        assertFalse(SimilarShowsChoice.showing(echo))
        assertFalse(SimilarShowsChoice.canSkipLoading(echo.copy(similarChoicePending = false), loading = true))
        assertFalse(SimilarShowsChoice.canSkipLoading(OnboardingUiState(), loading = true))
    }

    @Test
    fun `the loading skip shows only while asked-for suggestions load`() {
        val loading = search.copy(similarChoicePending = false)
        assertTrue(SimilarShowsChoice.canSkipLoading(loading, loading = true))
        assertFalse(SimilarShowsChoice.canSkipLoading(loading, loading = false))
        assertFalse(SimilarShowsChoice.canSkipLoading(search, loading = true))
        assertFalse(SimilarShowsChoice.canSkipLoading(loading.copy(isCompleting = true), loading = true))
    }

    @Test
    fun `a late answer is dropped after leaving for Home, going back or before asking`() {
        val loading = search.copy(similarChoicePending = false)
        assertTrue(SimilarShowsChoice.acceptsResult(loading))
        assertFalse("left for Home", SimilarShowsChoice.acceptsResult(loading.copy(isCompleting = true)))
        assertFalse("went back to search", SimilarShowsChoice.acceptsResult(loading.copy(currentStep = OnboardingStep.SEARCH)))
        assertFalse("still choosing", SimilarShowsChoice.acceptsResult(search))
        assertFalse("finishing hides the choice", SimilarShowsChoice.showing(search.copy(isCompleting = true)))
    }
}
