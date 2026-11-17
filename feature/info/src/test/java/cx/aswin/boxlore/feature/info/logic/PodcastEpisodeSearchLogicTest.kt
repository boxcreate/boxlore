package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastEpisodeSearchLogicTest {
    private val episodes = listOf(TestFixtures.episode(id = "first"), TestFixtures.episode(id = "second"))

    @Test
    fun `opening or clearing search keeps the loaded cards even if old results remain`() {
        assertEquals(episodes, podcastSearchDisplayEpisodes(true, "", emptyList(), episodes))
        assertEquals(episodes, podcastSearchDisplayEpisodes(true, "  ", emptyList(), episodes))
        assertEquals(episodes, podcastSearchDisplayEpisodes(false, "query", emptyList(), episodes))
    }

    @Test
    fun `displayed episodes never repeat an id`() {
        // The episode list keys rows by id; a repeated id crashed with "Key ... was already used".
        val repeated = episodes + TestFixtures.episode(id = "first")
        assertEquals(listOf("first", "second"), podcastSearchDisplayEpisodes(false, "", null, repeated).map { it.id })
        assertEquals(listOf("first", "second"), podcastSearchDisplayEpisodes(true, "query", repeated, episodes).map { it.id })
    }

    @Test
    fun `pending first query keeps loaded cards until real results arrive`() {
        assertEquals(episodes, podcastSearchDisplayEpisodes(true, "query", null, episodes))
        assertFalse(podcastSearchHasNoResults(true, "query", true, null))
    }

    @Test
    fun `results preserve repository order and a completed empty search shows recovery`() {
        val results = episodes.reversed()
        assertEquals(results, podcastSearchDisplayEpisodes(true, "query", results, episodes))
        assertTrue(podcastSearchDisplayEpisodes(true, "query", emptyList(), episodes).isEmpty())
        assertTrue(podcastSearchHasNoResults(true, "query", false, emptyList()))
    }

    @Test
    fun `empty results from a previous query do not show an empty state during loading or after close`() {
        assertFalse(podcastSearchHasNoResults(true, "query", true, emptyList()))
        assertFalse(podcastSearchHasNoResults(false, "query", false, emptyList()))
        assertFalse(podcastSearchHasNoResults(true, "", false, emptyList()))
    }

    @Test
    fun `a search that could not read the episodes shows Try again, not no matches`() {
        // Reported: a failed read of the show's list looked like a finished search with no matches.
        assertFalse(podcastSearchHasNoResults(true, "query", false, emptyList(), failed = true))
        assertTrue(podcastSearchShowsFailure(true, "query", false, failed = true))
        assertFalse(podcastSearchShowsFailure(true, "query", true, failed = true), "not while the retry runs")
        assertFalse(podcastSearchShowsFailure(false, "query", false, failed = true), "not after search closes")
        assertFalse(podcastSearchShowsFailure(true, "query", false, failed = false))
    }

    @Test
    fun `a search that covered only the newest says so once it finishes`() {
        assertEquals(11_000, podcastSearchCoverage(true, "query", false, false, 11_000))
        assertEquals(null, podcastSearchCoverage(true, "query", false, false, null), "covered every episode")
        assertEquals(null, podcastSearchCoverage(true, "query", true, false, 11_000), "not while it runs")
        assertEquals(null, podcastSearchCoverage(true, "query", false, true, 11_000), "not with a failure")
        assertEquals(null, podcastSearchCoverage(false, "query", false, false, 11_000), "not after search closes")
        assertEquals(null, podcastSearchCoverage(true, " ", false, false, 11_000))
    }

    @Test
    fun `late results after clear query change or show navigation cannot overwrite the list`() {
        assertFalse(podcastSearchResponseIsCurrent("old", "show", "", "show"))
        assertFalse(podcastSearchResponseIsCurrent("old", "show", "new", "show"))
        assertFalse(podcastSearchResponseIsCurrent("query", "old-show", "query", "new-show"))
        assertFalse(podcastSearchResponseIsCurrent("", "show", "", "show"))
        assertTrue(podcastSearchResponseIsCurrent("query", "show", "query", "show"))
    }
}
