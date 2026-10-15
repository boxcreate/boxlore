package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingAiStartTest {
    private fun podcast(id: String) = Podcast(id = id, title = "Show $id", artist = "Host", imageUrl = "", description = null, genre = "Podcast")

    @Test
    fun `a restart keeps the stored region instead of resetting it to us`() {
        val previous = OnboardingUiState(currentStep = OnboardingStep.WELCOME, currentRegion = "in", initialRegion = "in", aiCurrentTurn = 5)
        val fresh = OnboardingAiStart.freshState(previous)
        assertEquals(OnboardingStep.AI_ONBOARDING, fresh.currentStep)
        assertEquals("in", fresh.currentRegion)
        assertEquals("in", fresh.initialRegion)
        assertEquals(1, fresh.aiCurrentTurn)
        assertTrue(fresh.aiHistory.isEmpty())
    }

    @Test
    fun `building opens suggestions from Echo and drops old results with their picks`() {
        val rows = listOf(OnboardingCurriculumRowDto("Cold cases", listOf(OnboardingCurriculumPodcastDto(1, title = "Serial"), OnboardingCurriculumPodcastDto(2, title = "Criminal"))))
        val state = OnboardingUiState(
            currentStep = OnboardingStep.AI_ONBOARDING,
            aiCurriculumRows = rows,
            reachedSuggestionsViaSearchFlow = true,
            subscribedPodcastIds = setOf("1", "99"),
            selectedPodcasts = mapOf("1" to podcast("1"), "99" to podcast("99")),
        )
        val building = OnboardingAiStart.buildingState(state)
        assertEquals(OnboardingStep.AI_SUGGESTIONS, building.currentStep)
        assertTrue(building.reachedSuggestionsViaAiFlow)
        assertEquals(false, building.reachedSuggestionsViaSearchFlow)
        assertTrue(building.aiCurriculumRows.isEmpty())
        // Show 99 came from a search side trip, not the rows, so it stays picked.
        assertEquals(setOf("99"), building.subscribedPodcastIds)
        assertEquals(setOf("99"), building.selectedPodcasts.keys)
    }

    @Test
    fun `changing answers preserves selected chat picks even when curriculum repeats them`() {
        val rows = listOf(
            OnboardingCurriculumRowDto(
                rowTitle = "Shows",
                podcasts = listOf(
                    OnboardingCurriculumPodcastDto(1, title = "Chat pick"),
                    OnboardingCurriculumPodcastDto(2, title = "Suggestion"),
                    OnboardingCurriculumPodcastDto(3, title = "Removed chat pick"),
                ),
            ),
        )
        val state = OnboardingUiState(
            aiCurriculumRows = rows,
            aiPicks = listOf(podcast("1"), podcast("3")),
            selectedPodcasts = mapOf("1" to podcast("1"), "2" to podcast("2")),
            subscribedPodcastIds = setOf("1", "2"),
        )
        val changed = OnboardingAiStart.withoutResults(state)
        assertTrue(changed.aiCurriculumRows.isEmpty())
        assertEquals(setOf("1"), changed.subscribedPodcastIds)
        assertEquals(setOf("1"), changed.selectedPodcasts.keys)
    }

    @Test
    fun `nothing to drop leaves the state untouched`() {
        val state = OnboardingUiState(subscribedPodcastIds = setOf("5"))
        assertSame(state, OnboardingAiStart.withoutResults(state))
    }
}
