package cx.aswin.boxlore.tv.ui

import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.show.EpisodeSearch
import cx.aswin.boxlore.tv.ui.show.LinkLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeSearchTest {
    private fun episode(id: String, title: String, notes: String = "") = TvEpisode(id, "show", title, "Show", null, 30, description = notes)

    private val episodes =
        listOf(
            episode("1", "The Tesla Roadster, again"),
            episode("2", "Macbook review", notes = "We talk about the new Tesla screen."),
            episode("3", "Phones of the year"),
        )

    @Test
    fun `every word must appear in the title or the notes, in any case`() {
        assertEquals(listOf("1", "2"), EpisodeSearch.filter(episodes, "tesla").map(TvEpisode::id))
        assertEquals(listOf("2"), EpisodeSearch.filter(episodes, "  TESLA   screen ").map(TvEpisode::id))
        assertEquals(emptyList<String>(), EpisodeSearch.filter(episodes, "tesla phones").map(TvEpisode::id))
    }

    @Test
    fun `an empty search keeps every episode in order`() {
        assertEquals(episodes, EpisodeSearch.filter(episodes, "   "))
    }

    @Test
    fun `a notes link is named short enough to fit, without its scheme`() {
        assertEquals("example.com/show/notes", LinkLabel.of("https://www.example.com/show/notes/"))
        val long = LinkLabel.of("https://example.com/" + "a".repeat(200))
        assertEquals(LinkLabel.MAX_LENGTH, long.length)
        assertTrue(long.endsWith("…"))
    }
}
