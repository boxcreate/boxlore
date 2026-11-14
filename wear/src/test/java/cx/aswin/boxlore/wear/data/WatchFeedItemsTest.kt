package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.ParsedFeedItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchFeedItemsTest {
    private val show = 7L

    private fun item(guid: String?, audio: String = "https://example.org/a.mp3", enclosures: List<String> = listOf(audio)) =
        ParsedFeedItem(
            episode =
                RssEpisodeEntity(
                    episodeId = "-1",
                    podcastId = show.toString(),
                    guid = guid,
                    title = "Octomom",
                    description = "",
                    audioUrl = audio,
                    imageUrl = " ",
                    duration = 2_040,
                    publishedDate = 1_793_923_200,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = null,
                ),
            documentIndex = 0,
            enclosureUrls = enclosures,
        )

    @Test
    fun `an item gets the ID every boxlore app gives it, from its GUID`() {
        val episode = WatchFeedItems.episode(item(guid = "  abc-123  "), epNs = show)!!

        assertEquals(EpisodeRecipeId.forNamespace(show, "abc-123"), episode.id)
        assertTrue(EpisodeRecipeId.isRecipeId(episode.id))
        assertEquals("Octomom", episode.title)
        assertEquals(2_040, episode.durationSeconds)
        assertEquals(1_793_923_200, episode.publishedAtSeconds)
        assertNull(episode.imageUrl)
    }

    @Test
    fun `an item with no GUID is identified by its enclosure address`() {
        val episode = WatchFeedItems.episode(item(guid = null), epNs = show)!!

        assertEquals(EpisodeRecipeId.forNamespace(show, "https://example.org/a.mp3"), episode.id)
    }

    @Test
    fun `the same item in another show is a different episode`() {
        val here = WatchFeedItems.episode(item(guid = "abc"), epNs = 7)!!
        val there = WatchFeedItems.episode(item(guid = "abc"), epNs = 8)!!

        assertNotEquals(here.id, there.id)
    }

    @Test
    fun `an item the shared ID recipe does not cover is left out`() {
        assertNull(WatchFeedItems.episode(item(guid = null, enclosures = emptyList()), epNs = show))
    }

    @Test
    fun `an item without playable audio is left out`() {
        assertNull(WatchFeedItems.episode(item(guid = "abc", audio = ""), epNs = show))
        assertNull(WatchFeedItems.episode(item(guid = "abc", audio = "file:///a.mp3"), epNs = show))
    }
}
