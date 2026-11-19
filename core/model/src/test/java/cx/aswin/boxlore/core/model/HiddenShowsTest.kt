package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Hidden shows match a show by every ID its listening counts under. */
class HiddenShowsTest {
    private val rss = Podcast(
        id = "rss:https://feed.example/a",
        title = "Curious world",
        artist = "",
        imageUrl = "https://art/a",
        sourceType = Podcast.SOURCE_RSS,
        linkedPodcastIndexId = "42",
    )
    private val pi = Podcast(id = "42", title = "Curious world", artist = "", imageUrl = "https://art/a")
    private val other = Podcast(id = "7", title = "Night owls", artist = "", imageUrl = "")

    @Test
    fun `a hidden RSS copy hides its Podcast Index show and its episodes`() {
        val hidden = HiddenShows.matchIds(listOf(HiddenShow.of(rss)))
        assertEquals(setOf("rss:https://feed.example/a", "42"), hidden)
        assertEquals(listOf(other), listOf(rss, pi, other).withoutHiddenShows(hidden))
        val episodes = listOf(Episode("e1", "One", "", "", podcastId = "42"), Episode("e2", "Two", "", "", podcastId = "7"), Episode("e3", "Three", "", ""))
        assertEquals(listOf("e2", "e3"), episodes.withoutHiddenShowEpisodes(hidden).map(Episode::id))
    }

    @Test
    fun `a hidden Podcast Index show hides an RSS copy linked to it`() {
        val hidden = HiddenShows.matchIds(listOf(HiddenShow.of(pi)))
        assertTrue(HiddenShows.hides(hidden, rss))
        assertFalse(HiddenShows.hides(hidden, other))
        assertFalse(HiddenShows.hidesShow(hidden, null))
    }

    @Test
    fun `nothing hidden returns the same list without copying it`() {
        val podcasts = listOf(pi, other)
        assertSame(podcasts, podcasts.withoutHiddenShows(emptySet()))
        val episodes = listOf(Episode("e1", "One", "", "", podcastId = "42"))
        assertSame(episodes, episodes.withoutHiddenShowEpisodes(emptySet()))
    }

    @Test
    fun `a hidden show keeps its name and artwork for the hidden list`() {
        val show = HiddenShow.of(rss)
        assertEquals("Curious world", show.title)
        assertEquals("https://art/a", show.imageUrl)
        assertEquals(setOf("42"), show.aliasIds)
        assertEquals(emptySet<String>(), HiddenShow.of(other).aliasIds)
    }
}
