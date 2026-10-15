package cx.aswin.boxlore.feature.onboarding

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
}
