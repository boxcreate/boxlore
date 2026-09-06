package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.toPodcast

/** One selectable lane on the suggestions screen (curriculum row or charts). */
internal data class OnboardingSuggestionsLane(
    val id: String,
    val title: String,
    val purpose: String,
    val isCharts: Boolean,
    val podcasts: List<Podcast>,
)

internal object OnboardingSuggestionsLanes {
    const val CHARTS_LANE_ID = "charts"

    fun build(
        curriculumRows: List<OnboardingCurriculumRowDto>,
        chartsPodcasts: List<Podcast>,
        selectedGenres: Set<String> = emptySet(),
    ): List<OnboardingSuggestionsLane> {
        val lanes =
            curriculumRows.mapIndexed { index, row ->
                OnboardingSuggestionsLane(
                    id = "curriculum_$index",
                    title = row.rowTitle.ifBlank { "For you" },
                    purpose = purposeForCurriculumTitle(row.rowTitle),
                    isCharts = false,
                    podcasts = row.podcasts.map { it.toPodcast() },
                )
            }
        if (chartsPodcasts.isEmpty()) return lanes
        return lanes +
            OnboardingSuggestionsLane(
                id = CHARTS_LANE_ID,
                title = "Popular now",
                purpose = chartsPurpose(selectedGenres),
                isCharts = true,
                podcasts = chartsPodcasts,
            )
    }

    fun deduplicateLanes(lanes: List<OnboardingSuggestionsLane>): List<OnboardingSuggestionsLane> =
        cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy.deduplicateById(lanes) { it.id }

    fun deduplicatePodcasts(podcasts: List<Podcast>): List<Podcast> =
        cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy.deduplicateById(podcasts) { it.id }

    fun clampIndex(
        index: Int,
        laneCount: Int,
    ): Int {
        if (laneCount <= 0) return 0
        return index.coerceIn(0, laneCount - 1)
    }

    fun selectedCountInLane(
        lane: OnboardingSuggestionsLane,
        subscribedIds: Set<String>,
    ): Int = lane.podcasts.count { it.id in subscribedIds }

    fun purposeForCurriculumTitle(title: String): String {
        val lower = title.trim().lowercase()
        if (lower.isEmpty()) return "Shows matched to this taste lane."
        return CURRICULUM_PURPOSE_RULES
            .firstOrNull { (keys, _) -> keys.any { key -> lower.contains(key) } }
            ?.second
            ?: "A themed set based on what you told us."
    }

    private val CURRICULUM_PURPOSE_RULES =
        listOf(
            listOf("wind", "sleep", "calm", "relax") to "Softer shows for winding down.",
            listOf("news", "daily", "brief") to "Stay current without the noise.",
            listOf("deep", "learn", "curious") to "Longer listens that go further.",
            listOf("laugh", "comedy", "fun") to "Lighter shows when you want a lift.",
            listOf("crime", "mystery", "true") to "Stories with tension and payoff.",
            listOf("similar", "selection") to "More like the shows you already picked.",
            listOf("curated", "feed") to "A starter mix from your preferences.",
        )

    private fun chartsPurpose(selectedGenres: Set<String>): String {
        if (selectedGenres.isEmpty()) return "Charting shows in your region right now."
        val label =
            selectedGenres
                .take(2)
                .joinToString(" · ")
        return "Trending in $label for your country."
    }
}
