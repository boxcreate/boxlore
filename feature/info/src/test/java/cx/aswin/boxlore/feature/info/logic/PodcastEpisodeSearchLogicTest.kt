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
    fun `late results after clear query change or show navigation cannot overwrite the list`() {
        assertFalse(podcastSearchResponseIsCurrent("old", "show", "", "show"))
        assertFalse(podcastSearchResponseIsCurrent("old", "show", "new", "show"))
        assertFalse(podcastSearchResponseIsCurrent("query", "old-show", "query", "new-show"))
        assertFalse(podcastSearchResponseIsCurrent("", "show", "", "show"))
        assertTrue(podcastSearchResponseIsCurrent("query", "show", "query", "show"))
    }
}
