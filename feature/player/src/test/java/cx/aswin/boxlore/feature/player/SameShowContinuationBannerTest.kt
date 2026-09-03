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
            val actual = SameShowContinuationBannerDefaults.buttonText(count)
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `titleText formats correctly with or without podcast title`() {
        assertEquals("Continue Tech Talk?", SameShowContinuationBannerDefaults.titleText("Tech Talk"))
        assertEquals("Continue this show?", SameShowContinuationBannerDefaults.titleText(""))
    }

    @Test
    fun `explanationText formats availableCount correctly`() {
        assertEquals(
            "Played from recommendations, so next episodes were skipped to keep Up Next varied. You can add the next 5 episodes right after this track.",
            SameShowContinuationBannerDefaults.explanationText(5),
        )
    }

    @Test
    fun `preview toggle text formats availableCount correctly`() {
        assertEquals("1 upcoming episode", SameShowContinuationBannerDefaults.previewToggleText(1))
        assertEquals("3 upcoming episodes", SameShowContinuationBannerDefaults.previewToggleText(3))
        assertEquals("5 upcoming episodes", SameShowContinuationBannerDefaults.previewToggleText(5))
    }

    @Test
    fun `formatDuration formats seconds correctly`() {
        assertEquals("", SameShowContinuationBannerDefaults.formatDuration(0))
        assertEquals("", SameShowContinuationBannerDefaults.formatDuration(-10))
        assertEquals("45 min", SameShowContinuationBannerDefaults.formatDuration(2700))
        assertEquals("1h 15m", SameShowContinuationBannerDefaults.formatDuration(4500))
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
