package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.core.network.model.EpisodeItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvMappingTest {
    @Test
    fun `show notes read as plain text with their paragraphs`() {
        val html = "<p>Jack tells   the story &amp; more.</p><p>Sponsors:<br/>Visit <a href=\"https://example.com/a\">us</a></p>"
        assertEquals("Jack tells the story & more.\nSponsors:\nVisit us", TvMapping.notes(html))
        assertEquals("", TvMapping.notes(null))
        assertTrue(TvMapping.notes("x".repeat(5_000)).endsWith("…"))
    }

    @Test
    fun `links come from the notes once each, at most four`() {
        val html = "See https://a.example/1. and <a href=\"https://a.example/1\">it</a> https://b.example https://c.example https://d.example https://e.example"
        assertEquals(listOf("https://a.example/1", "https://b.example", "https://c.example", "https://d.example"), TvMapping.links(html))
    }

    @Test
    fun `an API episode needs a web audio address, and its length rounds up to whole minutes`() {
        val item = EpisodeItem(id = 7, title = "T", enclosureUrl = " https://example.com/7.mp3 ", duration = 61, feedId = 3, feedTitle = "Show")
        val episode = TvMapping.episode(item, show = null)!!
        assertEquals("https://example.com/7.mp3", episode.audioUrl)
        assertEquals("3", episode.showId)
        assertEquals(2, episode.durationMinutes)
        assertNull(TvMapping.episode(item.copy(enclosureUrl = "ftp://example.com/7.mp3"), show = null))
        assertEquals(0, TvMapping.minutes(null))
    }

    @Test
    fun `only a short episode its feed calls a trailer counts as one`() {
        assertTrue(TvMapping.episode(EpisodeItem(id = 1, title = "T", enclosureUrl = "https://example.com/1.mp3", duration = 120, feedId = 3, episodeType = "trailer"), show = null)!!.trailer)
        assertEquals(false, TvMapping.trailer("trailer", durationSeconds = 3_600))
        assertEquals(false, TvMapping.trailer("full", durationSeconds = 60))
    }
}
