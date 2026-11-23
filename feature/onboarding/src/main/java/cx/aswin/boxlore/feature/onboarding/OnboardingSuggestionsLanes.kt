package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.toPodcast

/**
 * One selectable lane on the suggestions screen (curriculum row or charts). A server row title stays
 * as the server sent it ([UiText.Raw]); titles and purposes the app writes are resources.
 */
internal data class OnboardingSuggestionsLane(
    val id: String,
    val title: UiText,
    val purpose: UiText,
    val isCharts: Boolean,
    val podcasts: List<Podcast>,
)

internal object OnboardingSuggestionsLanes {
    const val CHARTS_LANE_ID = "charts"

    /** Row titles the app writes itself when synthesis falls back to charts; shown in the app's language. */
    const val FALLBACK_TRENDING_TITLE = "Trending Hits"
    const val FALLBACK_GENRES_TITLE = "Trending in your Genres"

    fun build(
        curriculumRows: List<OnboardingCurriculumRowDto>,
        chartsPodcasts: List<Podcast>,
        selectedGenres: Set<String> = emptySet(),
    ): List<OnboardingSuggestionsLane> {
        val lanes =
            curriculumRows.mapIndexed { index, row ->
                OnboardingSuggestionsLane(
                    id = "curriculum_$index",
                    title = titleForCurriculumRow(row.rowTitle),
                    purpose = purposeForCurriculumTitle(row.rowTitle),
                    isCharts = false,
                    podcasts = row.podcasts.map { it.toPodcast() },
                )
            }
        if (chartsPodcasts.isEmpty()) return lanes
        return lanes +
            OnboardingSuggestionsLane(
                id = CHARTS_LANE_ID,
                title = UiText.res(R.string.suggestions_lane_popular_now),
                purpose = chartsPurpose(selectedGenres),
                isCharts = true,
                podcasts = chartsPodcasts,
            )
    }

    fun deduplicateLanes(lanes: List<OnboardingSuggestionsLane>): List<OnboardingSuggestionsLane> =
        cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy.deduplicateById(lanes) { it.id }

    /**
     * Repeats are removed in display order. The charts lane stays whole (it is a ranking with its
     * own region); every other lane leaves out shows a lane above it already offers, and a lane
     * that loses shows that way and has fewer than [MinLaneShows] left is hidden. A lane that is
     * the top of the charts again (at least [ChartsRepeatShare] of its shows among the charts'
     * leading shows, as the server's genre fallback is) is hidden too; a lane that only shares a
     * few popular shows with the charts stays.
     */
    fun withoutRepeats(lanes: List<OnboardingSuggestionsLane>): List<OnboardingSuggestionsLane> {
        val charts = lanes.filter { it.isCharts }.flatMap { lane -> lane.podcasts.map { it.id } }
        val seen = mutableSetOf<String>()
        return lanes.mapNotNull { lane ->
            if (lane.isCharts) return@mapNotNull lane
            if (repeatsChartsTop(lane, charts)) return@mapNotNull null
            val fresh = lane.podcasts.filter { it.id !in seen }
            val kept = when {
                fresh.size == lane.podcasts.size -> lane
                fresh.size < MinLaneShows -> null
                else -> lane.copy(podcasts = fresh)
            }
            // Only a lane that stays claims its shows; a hidden one leaves them to the lanes below.
            kept?.also { seen += fresh.map { it.id } }
        }
    }

    private fun repeatsChartsTop(lane: OnboardingSuggestionsLane, charts: List<String>): Boolean {
        if (charts.isEmpty() || lane.podcasts.isEmpty()) return false
        val top = charts.take(lane.podcasts.size + ChartsTopSlack).toSet()
        return lane.podcasts.count { it.id in top } >= lane.podcasts.size * ChartsRepeatShare
    }

    /** The share of a lane's shows among the charts' leading shows that makes it the charts again. */
    private const val ChartsRepeatShare = 0.8f

    /** How far past the lane's own length the charts' leading shows reach. */
    private const val ChartsTopSlack = 2

    const val MinLaneShows = 3

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

    /** A server row title as sent; a blank one, or one of the app's own fallback titles, from resources. */
    fun titleForCurriculumRow(rowTitle: String): UiText = when (rowTitle.trim()) {
        "" -> UiText.res(R.string.suggestions_lane_for_you)
        FALLBACK_TRENDING_TITLE -> UiText.res(R.string.suggestions_lane_trending_hits)
        FALLBACK_GENRES_TITLE -> UiText.res(R.string.suggestions_lane_trending_genres)
        else -> UiText.raw(rowTitle)
    }

    /** Matches the server's English row titles; the result is in the app's language. */
    fun purposeForCurriculumTitle(title: String): UiText {
        val lower = title.trim().lowercase()
        if (lower.isEmpty()) return UiText.res(R.string.suggestions_lane_purpose_matched)
        return UiText.res(
            CURRICULUM_PURPOSE_RULES
                .firstOrNull { (keys, _) -> keys.any { key -> lower.contains(key) } }
                ?.second
                ?: R.string.suggestions_lane_purpose_themed,
        )
    }

    private val CURRICULUM_PURPOSE_RULES =
        listOf(
            listOf("wind", "sleep", "calm", "relax") to R.string.suggestions_lane_purpose_wind_down,
            listOf("news", "daily", "brief") to R.string.suggestions_lane_purpose_news,
            listOf("deep", "learn", "curious") to R.string.suggestions_lane_purpose_deep,
            listOf("laugh", "comedy", "fun") to R.string.suggestions_lane_purpose_lighter,
            listOf("crime", "mystery", "true") to R.string.suggestions_lane_purpose_tension,
            listOf("similar", "selection") to R.string.suggestions_lane_purpose_similar,
            listOf("curated", "feed") to R.string.suggestions_lane_purpose_starter,
        )

    /** Genre keys become their names in the app's language; one or two of them, as whole sentences. */
    private fun chartsPurpose(selectedGenres: Set<String>): UiText {
        val names = selectedGenres.take(2).map { key -> onboardingGenreNameRes(key)?.let { UiText.res(it) } ?: UiText.raw(key) }
        return when (names.size) {
            0 -> UiText.res(R.string.suggestions_lane_purpose_charts_region)
            1 -> UiText.res(R.string.suggestions_lane_purpose_charts_one, names[0])
            else -> UiText.res(R.string.suggestions_lane_purpose_charts_two, names[0], names[1])
        }
    }
}
