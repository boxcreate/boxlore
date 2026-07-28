package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto

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
        assertEquals("Deep Dives", lanes[0].title)
        assertFalse(lanes[0].isCharts)
        assertTrue(lanes[1].isCharts)
        assertEquals(OnboardingSuggestionsLanes.CHARTS_LANE_ID, lanes[1].id)
        assertTrue(lanes[1].purpose.contains("News"))
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
        assertTrue(lanes.single().purpose.contains("winding", ignoreCase = true))
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
        assertEquals("For you", lanes.single().title)
    }

    @Test
    fun `unmatched curriculum title uses default purpose`() {
        val purpose = OnboardingSuggestionsLanes.purposeForCurriculumTitle("Quantum knitting hour")
        assertEquals("A themed set based on what you told us.", purpose)
    }

    @Test
    fun `chartsPurpose without genres uses regional default`() {
        val lanes =
            OnboardingSuggestionsLanes.build(
                curriculumRows = emptyList(),
                chartsPodcasts = listOf(samplePodcast("c1")),
                selectedGenres = emptySet(),
            )
        assertEquals("Charting shows in your region right now.", lanes.single().purpose)
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
                title = "T",
                purpose = "P",
                isCharts = false,
                podcasts = listOf(samplePodcast("1"), samplePodcast("2"), samplePodcast("3")),
            )
        assertEquals(2, OnboardingSuggestionsLanes.selectedCountInLane(lane, setOf("1", "3", "9")))
    }

    private fun samplePodcast(id: String) =
        Podcast(
            id = id,
            title = "Show $id",
            artist = "Host",
            imageUrl = "",
            description = "About $id",
            genre = "News",
        )
}
