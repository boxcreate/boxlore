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
    fun `isError only when error set and content empty`() {
        assertTrue(
            OnboardingSuggestionsPresentation.isError(
                OnboardingUiState(onboardingError = "boom"),
            ),
        )
        assertFalse(OnboardingSuggestionsPresentation.isError(OnboardingUiState()))
        assertFalse(
            OnboardingSuggestionsPresentation.isError(
                OnboardingUiState(
                    onboardingError = "boom",
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
        assertFalse(
            OnboardingSuggestionsPresentation.isError(
                OnboardingUiState(
                    onboardingError = "boom",
                    genreChartsPodcasts =
                    listOf(Podcast(id = "c1", title = "Chart", artist = "Host", imageUrl = "")),
                ),
            ),
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
        assertFalse(cleared.reachedSuggestionsViaSearchFlow)
        assertFalse(cleared.reachedSuggestionsViaOpmlFlow)
        assertFalse(cleared.reachedSuggestionsViaAiFlow)
    }

    private fun show(id: String) = Podcast(id = id, title = "Show $id", artist = "Host", imageUrl = "")

    private fun rows(vararg ids: Long) =
        listOf(OnboardingCurriculumRowDto(rowTitle = "Cold cases", podcasts = ids.map { OnboardingCurriculumPodcastDto(id = it, title = "Show $it") }))

    @Test
    fun `each flow is told apart, and only search and import count suggestions alone`() {
        assertEquals(SuggestionsOrigin.Echo, OnboardingSuggestionsPresentation.origin(OnboardingUiState(reachedSuggestionsViaAiFlow = true)))
        assertEquals(SuggestionsOrigin.Search, OnboardingSuggestionsPresentation.origin(OnboardingUiState(reachedSuggestionsViaSearchFlow = true)))
        assertEquals(SuggestionsOrigin.Library, OnboardingSuggestionsPresentation.origin(OnboardingUiState(reachedSuggestionsViaOpmlFlow = true)))
        assertEquals(SuggestionsOrigin.Genres, OnboardingSuggestionsPresentation.origin(OnboardingUiState()))

        // Search picks are in the library before suggestions load: "Add N" counts only new suggestions.
        val search = OnboardingUiState(
            reachedSuggestionsViaSearchFlow = true,
            aiCurriculumRows = rows(9, 10),
            selectedPodcasts = mapOf("s1" to show("s1"), "s2" to show("s2")),
            subscribedPodcastIds = setOf("s1", "s2"),
        )
        assertEquals(0, OnboardingSuggestionsPresentation.addCount(search))
        assertEquals(1, OnboardingSuggestionsPresentation.addCount(search.copy(subscribedPodcastIds = setOf("s1", "s2", "9"))))
        assertEquals(listOf("s1", "s2"), OnboardingSuggestionsPresentation.seedShows(search).map { it.id })

        // Echo counts everything picked, taste-test and search picks included.
        val echo = OnboardingUiState(reachedSuggestionsViaAiFlow = true, aiCurriculumRows = rows(9), subscribedPodcastIds = setOf("p1", "9"))
        assertEquals(2, OnboardingSuggestionsPresentation.addCount(echo))
    }

    @Test
    fun `Echo's own picks lead in their lane and leave the others, nothing else is selected`() {
        val echo = OnboardingUiState(
            reachedSuggestionsViaAiFlow = true,
            aiCurriculumRows = rows(1, 2),
            aiPicks = listOf(show("1"), show("7")),
        )
        val lanes = OnboardingSuggestionsPresentation.lanes(echo, "Your picks")
        assertEquals(OnboardingSuggestionsPresentation.PICKS_LANE_ID, lanes.first().id)
        assertEquals(listOf("1", "7"), lanes.first().podcasts.map { it.id })
        assertEquals(listOf("2"), lanes[1].podcasts.map { it.id })

        // Other flows never get a picks lane.
        val genres = echo.copy(reachedSuggestionsViaAiFlow = false)
        assertTrue(OnboardingSuggestionsPresentation.lanes(genres, "Your picks").none { it.id == OnboardingSuggestionsPresentation.PICKS_LANE_ID })
        // A lane emptied by the picks disappears.
        val onlyPicked = echo.copy(aiCurriculumRows = rows(1))
        assertEquals(1, OnboardingSuggestionsPresentation.lanes(onlyPicked, "Your picks").size)
    }
}
