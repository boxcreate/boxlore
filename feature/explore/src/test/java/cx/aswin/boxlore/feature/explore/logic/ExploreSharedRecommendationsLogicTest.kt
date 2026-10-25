package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.catalog.HomeRecommendations
import cx.aswin.boxlore.core.model.Episode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreSharedRecommendationsLogicTest {
    @Test
    fun `distinctRecommendations drops duplicate ids and titles`() {
        val a = episode(id = "1", title = "Hello")
        val b = episode(id = "1", title = "Hello again")
        val c = episode(id = "2", title = "Hello")
        val d = episode(id = "3", title = "Other")

        val result = ExploreSharedRecommendationsLogic.distinctRecommendations(listOf(a, b, c, d))

        assertEquals(listOf(a, d), result)
    }

    @Test
    fun `decodeCachedRecommendationsJson round trips via encode`() {
        val episodes = listOf(episode(id = "10", title = "Alpha"), episode(id = "11", title = "Beta"))
        val encoded = ExploreSharedRecommendationsLogic.encodeRecommendationsJson(episodes)
        val decoded = ExploreSharedRecommendationsLogic.decodeCachedRecommendationsJson(encoded)
        assertEquals(episodes.map { it.id }, decoded.map { it.id })
    }

    @Test
    fun `decodeCachedRecommendationsJson returns empty on null or garbage`() {
        assertTrue(ExploreSharedRecommendationsLogic.decodeCachedRecommendationsJson(null).isEmpty())
        assertTrue(ExploreSharedRecommendationsLogic.decodeCachedRecommendationsJson("").isEmpty())
        assertTrue(ExploreSharedRecommendationsLogic.decodeCachedRecommendationsJson("{not-json}").isEmpty())
    }

    @Test
    fun `a failed request with no last good picks keeps the shown picks while an empty answer clears them`() {
        assertNull(ExploreSharedRecommendationsLogic.freshRecommendations(HomeRecommendations(failed = true)))
        assertEquals(emptyList<Episode>(), ExploreSharedRecommendationsLogic.freshRecommendations(HomeRecommendations()))

        val a = episode(id = "1", title = "Hello")
        val fresh = ExploreSharedRecommendationsLogic.freshRecommendations(HomeRecommendations(listOf(a, a.copy(title = "Again")), isFallback = false))
        assertEquals(listOf(a), fresh)
    }

    private fun episode(
        id: String,
        title: String,
    ): Episode = Episode(
        id = id,
        title = title,
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        duration = 60,
        publishedDate = 0L,
        podcastId = "pod",
        podcastTitle = "Show",
    )
}
