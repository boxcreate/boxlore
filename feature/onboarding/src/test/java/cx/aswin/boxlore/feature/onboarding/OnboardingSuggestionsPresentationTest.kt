package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingSuggestionsPresentationTest {
    @Test
    fun `isLoading is true for search and OPML ai flags when content empty`() {
        assertTrue(
            OnboardingSuggestionsPresentation.isLoading(
                OnboardingUiState(isAiLoading = true, isSynthesizing = true),
            ),
        )
        assertTrue(
            OnboardingSuggestionsPresentation.isLoading(
                OnboardingUiState(isLoadingPodcasts = true),
            ),
        )
    }

    @Test
    fun `isLoading is false when idle empty or when content already present`() {
        assertFalse(OnboardingSuggestionsPresentation.isLoading(OnboardingUiState()))
        assertFalse(
            OnboardingSuggestionsPresentation.isLoading(
                OnboardingUiState(
                    isAiLoading = true,
                    aiCurriculumRows =
                        listOf(
                            OnboardingCurriculumRowDto(
                                rowTitle = "Lane",
                                podcasts = listOf(OnboardingCurriculumPodcastDto(id = 1, title = "A")),
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun `filterCurriculumRowsExcluding drops seed ids and empty rows`() {
        val rows =
            listOf(
                OnboardingCurriculumRowDto(
                    rowTitle = "Seeds only",
                    podcasts =
                        listOf(
                            OnboardingCurriculumPodcastDto(id = 1, title = "Seed"),
                        ),
                ),
                OnboardingCurriculumRowDto(
                    rowTitle = "Mixed",
                    podcasts =
                        listOf(
                            OnboardingCurriculumPodcastDto(id = 1, title = "Seed"),
                            OnboardingCurriculumPodcastDto(id = 2, title = "New"),
                        ),
                ),
            )
        val filtered =
            OnboardingSuggestionsPresentation.filterCurriculumRowsExcluding(
                rows = rows,
                excludeIds = setOf("1"),
            )
        assertEquals(1, filtered.size)
        assertEquals("Mixed", filtered.single().rowTitle)
        assertEquals(listOf(2L), filtered.single().podcasts.map { it.id })
    }

    @Test
    fun `loadingCopy is structured for search OPML and default`() {
        assertEquals(
            OnboardingSuggestionsPresentation.LoadingCopy(
                title = "Subscribed to 3 shows",
                subtitle = "Looking for more you might like…",
            ),
            OnboardingSuggestionsPresentation.loadingCopy(
                OnboardingUiState(reachedSuggestionsViaSearchFlow = true, suggestionSeedCount = 3),
            ),
        )
        assertEquals(
            OnboardingSuggestionsPresentation.LoadingCopy(
                title = "Subscribed to 1 show",
                subtitle = "Looking for more you might like…",
            ),
            OnboardingSuggestionsPresentation.loadingCopy(
                OnboardingUiState(reachedSuggestionsViaSearchFlow = true, suggestionSeedCount = 1),
            ),
        )
        assertEquals(
            OnboardingSuggestionsPresentation.LoadingCopy(
                title = "Import complete",
                subtitle = "Finding shows inspired by your library…",
            ),
            OnboardingSuggestionsPresentation.loadingCopy(
                OnboardingUiState(reachedSuggestionsViaOpmlFlow = true),
            ),
        )
        assertEquals(
            OnboardingSuggestionsPresentation.LoadingCopy(
                title = "Designing your feed",
                subtitle = "Matching shows to your taste…",
            ),
            OnboardingSuggestionsPresentation.loadingCopy(OnboardingUiState()),
        )
    }

    @Test
    fun `finishCtaLabel for seed flows avoids start without subscribing`() {
        val seedOnly =
            OnboardingUiState(
                reachedSuggestionsViaSearchFlow = true,
                suggestionSeedCount = 2,
                subscribedPodcastIds = setOf("s1", "s2"),
            )
        assertEquals(
            "Start without adding",
            OnboardingSuggestionsPresentation.finishCtaLabel(seedOnly, selectedCount = 2),
        )

        val withExtras =
            seedOnly.copy(
                aiCurriculumRows =
                    listOf(
                        OnboardingCurriculumRowDto(
                            rowTitle = "Similar",
                            podcasts =
                                listOf(
                                    OnboardingCurriculumPodcastDto(id = 9, title = "Rec"),
                                ),
                        ),
                    ),
                subscribedPodcastIds = setOf("s1", "s2", "9"),
            )
        assertEquals(
            "Add 1 & start",
            OnboardingSuggestionsPresentation.finishCtaLabel(withExtras, selectedCount = 3),
        )

        assertEquals(
            "Subscribe & start · 4",
            OnboardingSuggestionsPresentation.finishCtaLabel(
                OnboardingUiState(subscribedPodcastIds = setOf("a", "b", "c", "d")),
                selectedCount = 4,
            ),
        )
        assertEquals(
            "Start without subscribing",
            OnboardingSuggestionsPresentation.finishCtaLabel(OnboardingUiState(), selectedCount = 0),
        )
    }

    @Test
    fun `withClearedSuggestionPayload keeps picks and clears rows`() {
        val pick = Podcast(id = "p1", title = "Kept", artist = "Host", imageUrl = "")
        val cleared =
            OnboardingSuggestionsPresentation.withClearedSuggestionPayload(
                state =
                    OnboardingUiState(
                        selectedPodcasts = mapOf(pick.id to pick),
                        searchQuery = "news",
                        aiCurriculumRows =
                            listOf(
                                OnboardingCurriculumRowDto(
                                    rowTitle = "Stale",
                                    podcasts = listOf(OnboardingCurriculumPodcastDto(id = 9, title = "X")),
                                ),
                            ),
                        isAiLoading = true,
                        isSynthesizing = true,
                        suggestionSeedCount = 2,
                        onboardingError = "boom",
                        reachedSuggestionsViaSearchFlow = true,
                    ),
                nextStep = OnboardingStep.SEARCH,
            )
        assertEquals(OnboardingStep.SEARCH, cleared.currentStep)
        assertTrue(cleared.aiCurriculumRows.isEmpty())
        assertFalse(cleared.isAiLoading)
        assertFalse(cleared.isSynthesizing)
        assertEquals(0, cleared.suggestionSeedCount)
        assertEquals(null, cleared.onboardingError)
        assertEquals(mapOf(pick.id to pick), cleared.selectedPodcasts)
        assertEquals("news", cleared.searchQuery)
        assertTrue(OnboardingSuggestionsPresentation.shouldClearSuggestionPayloadOnBack(OnboardingStep.SEARCH))
        assertTrue(OnboardingSuggestionsPresentation.shouldClearSuggestionPayloadOnBack(OnboardingStep.WELCOME))
        assertFalse(
            OnboardingSuggestionsPresentation.shouldClearSuggestionPayloadOnBack(OnboardingStep.AI_ONBOARDING),
        )
    }
}
