package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingSuggestionsLanesTest {
    @Test
    fun `builds curriculum lanes and appends charts when present`() {
        val rows =
            listOf(
                OnboardingCurriculumRowDto(
                    rowTitle = "Deep Dives",
                    podcasts =
                    listOf(
                        OnboardingCurriculumPodcastDto(id = 1, title = "A", author = "Host"),
                    ),
                ),
            )
        val charts = listOf(samplePodcast("c1"))
        val lanes =
            OnboardingSuggestionsLanes.build(
                curriculumRows = rows,
                chartsPodcasts = charts,
                selectedGenres = setOf("News"),
            )
        assertEquals(2, lanes.size)
        assertEquals(UiText.raw("Deep Dives"), lanes[0].title)
        assertFalse(lanes[0].isCharts)
        assertTrue(lanes[1].isCharts)
        assertEquals(OnboardingSuggestionsLanes.CHARTS_LANE_ID, lanes[1].id)
        assertEquals(UiText.res(R.string.suggestions_lane_purpose_charts_one, UiText.res(R.string.onboarding_genre_news)), lanes[1].purpose)
    }

    @Test
    fun `a lane hidden for its repeats leaves its other shows to the lanes below`() {
        fun lane(id: String, vararg shows: String) =
            OnboardingSuggestionsLane(id, UiText.raw(id), UiText.raw(id), isCharts = false, podcasts = shows.map(::samplePodcast))
        // The second lane keeps only 4 and 5 after the first, too few to show; the third still shows 4, 5 and 6.
        val kept = OnboardingSuggestionsLanes.withoutRepeats(listOf(lane("a", "1", "2", "3"), lane("b", "1", "4", "5"), lane("c", "4", "5", "6")))
        assertEquals(listOf("a", "c"), kept.map { it.id })
        assertEquals(listOf("4", "5", "6"), kept.last().podcasts.map { it.id })
    }

    @Test
    fun `omits charts lane when empty`() {
        val lanes =
            OnboardingSuggestionsLanes.build(
                curriculumRows =
                listOf(OnboardingCurriculumRowDto(rowTitle = "For Winding Down", podcasts = emptyList())),
                chartsPodcasts = emptyList(),
            )
        assertEquals(1, lanes.size)
        assertFalse(lanes.single().isCharts)
        assertEquals(UiText.res(R.string.suggestions_lane_purpose_wind_down), lanes.single().purpose)
    }

    @Test
    fun `blank curriculum title falls back to For you`() {
        val lanes =
            OnboardingSuggestionsLanes.build(
                curriculumRows =
                listOf(
                    OnboardingCurriculumRowDto(rowTitle = "   ", podcasts = emptyList()),
                ),
                chartsPodcasts = emptyList(),
            )
        assertEquals(UiText.res(R.string.suggestions_lane_for_you), lanes.single().title)
    }

    @Test
    fun `unmatched curriculum title uses default purpose`() {
        val purpose = OnboardingSuggestionsLanes.purposeForCurriculumTitle("Quantum knitting hour")
        assertEquals(UiText.res(R.string.suggestions_lane_purpose_themed), purpose)
    }

    @Test
    fun `chartsPurpose without genres uses regional default`() {
        val lanes =
            OnboardingSuggestionsLanes.build(
                curriculumRows = emptyList(),
                chartsPodcasts = listOf(samplePodcast("c1")),
                selectedGenres = emptySet(),
            )
        assertEquals(UiText.res(R.string.suggestions_lane_purpose_charts_region), lanes.single().purpose)
        assertEquals(UiText.res(R.string.suggestions_lane_popular_now), lanes.single().title)
    }

    @Test
    fun `charts purpose names two genres in a whole sentence, and keeps an unknown key as it is`() {
        val lanes =
            OnboardingSuggestionsLanes.build(
                curriculumRows = emptyList(),
                chartsPodcasts = listOf(samplePodcast("c1")),
                selectedGenres = linkedSetOf("Technology", "Made-up genre", "News"),
            )
        assertEquals(
            UiText.res(R.string.suggestions_lane_purpose_charts_two, UiText.res(R.string.onboarding_genre_technology), UiText.raw("Made-up genre")),
            lanes.single().purpose,
        )
    }

    @Test
    fun `the app's own fallback row titles come from resources, server titles stay as sent`() {
        assertEquals(UiText.res(R.string.suggestions_lane_trending_hits), OnboardingSuggestionsLanes.titleForCurriculumRow(OnboardingSuggestionsLanes.FALLBACK_TRENDING_TITLE))
        assertEquals(UiText.res(R.string.suggestions_lane_trending_genres), OnboardingSuggestionsLanes.titleForCurriculumRow(OnboardingSuggestionsLanes.FALLBACK_GENRES_TITLE))
        assertEquals(UiText.raw("Trending in Spain"), OnboardingSuggestionsLanes.titleForCurriculumRow("Trending in Spain"))
    }

    @Test
    fun `clampIndex respects bounds`() {
        assertEquals(0, OnboardingSuggestionsLanes.clampIndex(-1, 3))
        assertEquals(2, OnboardingSuggestionsLanes.clampIndex(99, 3))
        assertEquals(0, OnboardingSuggestionsLanes.clampIndex(0, 0))
    }

    @Test
    fun `selectedCountInLane counts subscribed ids`() {
        val lane =
            OnboardingSuggestionsLane(
                id = "x",
                title = UiText.raw("T"),
                purpose = UiText.raw("P"),
                isCharts = false,
                podcasts = listOf(samplePodcast("1"), samplePodcast("2"), samplePodcast("3")),
            )
        assertEquals(2, OnboardingSuggestionsLanes.selectedCountInLane(lane, setOf("1", "3", "9")))
    }

    @Test
    fun `deduplicateLanes strips duplicate ids and blank ids`() {
        val lanes = listOf(
            OnboardingSuggestionsLane(id = "lane_1", title = UiText.raw("First"), purpose = UiText.raw(""), isCharts = false, podcasts = emptyList()),
            OnboardingSuggestionsLane(id = "lane_1", title = UiText.raw("Duplicate"), purpose = UiText.raw(""), isCharts = false, podcasts = emptyList()),
            OnboardingSuggestionsLane(id = "   ", title = UiText.raw("Blank"), purpose = UiText.raw(""), isCharts = false, podcasts = emptyList()),
            OnboardingSuggestionsLane(id = "lane_2", title = UiText.raw("Second"), purpose = UiText.raw(""), isCharts = false, podcasts = emptyList()),
        )
        val deduplicated = OnboardingSuggestionsLanes.deduplicateLanes(lanes)
        assertEquals(listOf("lane_1", "lane_2"), deduplicated.map { it.id })
        assertEquals(UiText.raw("First"), deduplicated[0].title)
    }

    @Test
    fun `deduplicatePodcasts strips duplicate ids and blank ids preserving first occurrence`() {
        val podcasts = listOf(
            samplePodcast("p1").copy(title = "First"),
            samplePodcast("p1").copy(title = "Duplicate First"),
            samplePodcast("   "),
            samplePodcast("p2").copy(title = "Second"),
        )
        val deduplicated = OnboardingSuggestionsLanes.deduplicatePodcasts(podcasts)
        assertEquals(listOf("p1", "p2"), deduplicated.map { it.id })
        assertEquals("First", deduplicated[0].title)
    }

    private fun samplePodcast(id: String) = Podcast(
        id = id,
        title = "Show $id",
        artist = "Host",
        imageUrl = "",
        description = "About $id",
        genre = "News",
    )
}
