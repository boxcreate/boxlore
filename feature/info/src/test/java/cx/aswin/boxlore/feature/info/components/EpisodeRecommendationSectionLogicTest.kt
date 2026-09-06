package cx.aswin.boxlore.feature.info.components

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeRecommendationSectionLogicTest {
    @Test
    fun `filterEpisodes deduplicates by id and drops blank or null ids`() {
        val episodes = listOf(
            TestFixtures.episode(id = "ep-1", title = "First"),
            TestFixtures.episode(id = "ep-1", title = "Duplicate First"),
            TestFixtures.episode(id = "   ", title = "Blank"),
            TestFixtures.episode(id = "ep-2", title = "Second"),
        )
        val filtered = EpisodeRecommendationSectionLogic.filterEpisodes(episodes)
        assertEquals(listOf("ep-1", "ep-2"), filtered.map { it.id })
        assertEquals("First", filtered[0].title)
    }

    @Test
    fun `filterEpisodes returns empty list when all episodes have blank or invalid ids`() {
        val episodes = listOf(
            TestFixtures.episode(id = ""),
            TestFixtures.episode(id = "   "),
        )
        val filtered = EpisodeRecommendationSectionLogic.filterEpisodes(episodes)
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `shouldRender returns false when empty and not loading and no emptyMessage`() {
        val shouldRender = EpisodeRecommendationSectionLogic.shouldRender(
            isLoading = false,
            hasEpisodes = false,
            emptyMessage = null,
        )
        assertFalse(shouldRender)
    }

    @Test
    fun `shouldRender returns true when loading or has episodes or has emptyMessage`() {
        assertTrue(
            EpisodeRecommendationSectionLogic.shouldRender(
                isLoading = true,
                hasEpisodes = false,
                emptyMessage = null,
            )
        )
        assertTrue(
            EpisodeRecommendationSectionLogic.shouldRender(
                isLoading = false,
                hasEpisodes = true,
                emptyMessage = null,
            )
        )
        assertTrue(
            EpisodeRecommendationSectionLogic.shouldRender(
                isLoading = false,
                hasEpisodes = false,
                emptyMessage = "No recommendations available",
            )
        )
    }
}
