package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnboardingCurriculumLogicTest {
    @Test
    fun formatMappedSelections_includesMappedGenres() {
        val result =
            OnboardingCurriculumLogic.formatMappedSelections(
                selections = linkedSetOf("Commuting", "Cooking"),
                genreMap = mapOf("Commuting" to linkedSetOf("News", "Comedy")),
                focusingPrefix = "focusing on",
            )
        assertEquals(
            "Commuting (focusing on News, Comedy), Cooking",
            result,
        )
    }

    @Test
    fun defaultSelectedPodcastIds_singleRowTakesTwo() {
        val row = row("A", listOf(1L, 2L, 3L))
        assertEquals(
            setOf("1", "2"),
            OnboardingCurriculumLogic.defaultSelectedPodcastIds(listOf(row)),
        )
    }

    @Test
    fun defaultSelectedPodcastIds_multiRowTakesFirstEach() {
        val rows =
            listOf(
                row("A", listOf(10L, 11L)),
                row("B", listOf(20L, 21L)),
            )
        assertEquals(
            setOf("10", "20"),
            OnboardingCurriculumLogic.defaultSelectedPodcastIds(rows),
        )
    }

    private fun row(
        title: String,
        ids: List<Long>,
    ): OnboardingCurriculumRowDto =
        OnboardingCurriculumRowDto(
            rowTitle = title,
            podcasts =
                ids.map { id ->
                    OnboardingCurriculumPodcastDto(
                        id = id,
                        title = "P$id",
                        author = "A",
                        image = "",
                        artwork = "",
                        categories = emptyMap(),
                        description = null,
                    )
                },
            episodes = emptyList(),
        )
}
