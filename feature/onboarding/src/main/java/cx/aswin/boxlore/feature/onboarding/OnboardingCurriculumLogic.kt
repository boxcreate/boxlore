package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto

/**
 * Pure helpers for onboarding curriculum assembly (testable without ViewModel).
 */
object OnboardingCurriculumLogic {
    fun formatMappedSelections(
        selections: Set<String>,
        genreMap: Map<String, Set<String>>,
        focusingPrefix: String,
    ): String =
        selections.joinToString(", ") { key ->
            val mapped = genreMap[key]
            if (!mapped.isNullOrEmpty()) {
                "$key ($focusingPrefix ${mapped.joinToString(", ")})"
            } else {
                key
            }
        }

    fun defaultSelectedPodcastIds(rows: List<OnboardingCurriculumRowDto>): Set<String> =
        buildSet {
            if (rows.size == 1) {
                rows
                    .firstOrNull()
                    ?.podcasts
                    ?.take(2)
                    ?.forEach { add(it.id.toString()) }
            } else {
                rows.forEach { row ->
                    row.podcasts.firstOrNull()?.let { add(it.id.toString()) }
                }
            }
        }
}
