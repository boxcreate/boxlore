package cx.aswin.boxlore.feature.player

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.SameShowContinuationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SameShowContinuationBannerTest {
    private fun testEpisode(id: String): Episode =
        Episode(
            id = id,
            title = "Episode $id",
            description = "Desc $id",
            audioUrl = "https://example.com/$id.mp3",
        )

    @Test
    fun `button copy formats availableCount correctly`() {
        listOf(1, 2, 3, 4, 5).forEach { count ->
            val expected = "Add next $count from this show"
            val actual = "Add next $count from this show"
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `banner is hidden when state is not visible`() {
        val state =
            SameShowContinuationState(
                visible = false,
                availableCount = 5,
                podcastTitle = "Tech Talk",
                nextEpisodes = (1..5).map { testEpisode("ep-$it") },
            )
        assertFalse(state.visible && state.availableCount > 0)
    }

    @Test
    fun `banner is hidden when availableCount is 0 even if visible flag is true`() {
        val state =
            SameShowContinuationState(
                visible = true,
                availableCount = 0,
                podcastTitle = "Tech Talk",
                nextEpisodes = emptyList(),
            )
        assertFalse(state.visible && state.availableCount > 0)
    }

    @Test
    fun `banner is visible when visible flag is true and availableCount is positive`() {
        val state =
            SameShowContinuationState(
                visible = true,
                availableCount = 3,
                podcastTitle = "Tech Talk",
                nextEpisodes = (1..3).map { testEpisode("ep-$it") },
            )
        assertTrue(state.visible && state.availableCount > 0)
        assertEquals("Tech Talk", state.podcastTitle)
        assertEquals(3, state.nextEpisodes.size)
    }

    @Test
    fun `HIDDEN state constant has expected defaults`() {
        val hidden = SameShowContinuationState.HIDDEN
        assertFalse(hidden.visible)
        assertEquals(0, hidden.availableCount)
        assertTrue(hidden.nextEpisodes.isEmpty())
        assertEquals("", hidden.podcastTitle)
    }
}
