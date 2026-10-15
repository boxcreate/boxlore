package cx.aswin.boxlore.feature.onboarding

/**
 * Pure helpers for onboarding curriculum assembly (testable without ViewModel).
 */
object OnboardingCurriculumLogic {
    fun formatMappedSelections(
        selections: Set<String>,
        genreMap: Map<String, Set<String>>,
        focusingPrefix: String,
    ): String = selections.joinToString(", ") { key ->
        val mapped = genreMap[key]
        if (!mapped.isNullOrEmpty()) {
            "$key ($focusingPrefix ${mapped.joinToString(", ")})"
        } else {
            key
        }
    }
}
