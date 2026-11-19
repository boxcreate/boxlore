package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneListRulesTest {
    private fun episode(id: String, published: Long) =
        Episode(id = id, title = id, description = "", audioUrl = "https://example.com/$id.mp3", publishedDate = published)

    // Regression: a show's new release did not reach the watch's New episodes until something else changed.
    @Test
    fun `a show's newer latest episode counts as a change to send, and nothing else new does not`() {
        val show = PodcastEntity(podcastId = "1", title = "Show", author = "Pub", imageUrl = "https://example.com/a.jpg", description = null, latestEpisode = episode("old", 100L))
        val key = PhoneListRules.showsKey(listOf(show))

        assertEquals(key, PhoneListRules.showsKey(listOf(show.copy(author = "Other"))))
        assertNotEquals(key, PhoneListRules.showsKey(listOf(show.copy(latestEpisode = episode("new", 200L)))))
        assertNotEquals(key, PhoneListRules.showsKey(listOf(show.copy(title = "Renamed"))))
        assertNotEquals(key, PhoneListRules.showsKey(emptyList()))
    }

    // Regression: the watch kept showing the phone's old episode list however long ago the feed was read.
    @Test
    fun `the watch's request for a fresh list is an ordinary feed read, and only for a show with a feed`() {
        val show = PodcastEntity(podcastId = "1", title = "Show", author = "Pub", imageUrl = "", description = null, feedUrl = "https://example.com/feed.xml")

        val request = PhoneListRules.watchRefresh(show)!!

        assertEquals("1", request.podcastIndexId)
        assertEquals("https://example.com/feed.xml", request.feedUrl)
        assertEquals(LocalEpisodeCatalogPort.RefreshReason.NORMAL, request.reason)
        // Regression: a watch-asked read queued auto-downloads in the foreground, past the phone's background download rules.
        assertFalse(request.runPostPersistCallback)
        assertEquals("Show", request.meta.title)
        assertNull(request.meta.imageUrl)
        assertNull(PhoneListRules.watchRefresh(show.copy(feedUrl = " ")))
        assertNull(PhoneListRules.watchRefresh(show.copy(feedUrl = null)))
    }

    @Test
    fun `a feed never read has no read time to send`() {
        assertNull(PhoneListRules.readAt(0L))
        assertEquals(5L, PhoneListRules.readAt(5L))
    }

    @Test
    fun `new episodes are each show's newest, without the finished ones, newest first`() {
        val latest = listOf(episode("old", 100L), episode("done", 300L), episode("new", 200L))

        assertEquals(listOf("new", "old"), PhoneListRules.newEpisodes(latest, completedIds = setOf("done")).map { it.id })
    }

    @Test
    fun `new episodes leave archived episodes out, unplayed or not`() {
        val latest = listOf(episode("trailer", 300L), episode("new", 200L), episode("old", 100L))

        assertEquals(listOf("new"), PhoneListRules.newEpisodes(latest, completedIds = setOf("old"), dismissedIds = setOf("trailer")).map { it.id })
    }

    @Test
    fun `a show's episode list on the watch leaves archived episodes out`() {
        val saved = listOf(episode("e2", 2L), episode("trailer", 1L), episode("e1", 0L))

        assertEquals(listOf("e2", "e1"), PhoneListRules.showEpisodes(saved, setOf("trailer")).map { it.id })
        assertEquals(saved, PhoneListRules.showEpisodes(saved, emptySet()))
    }

    @Test
    fun `a long library sends only the newest few`() {
        val latest = (1..50).map { episode("e$it", it.toLong()) }

        val sent = PhoneListRules.newEpisodes(latest, completedIds = emptySet())

        assertEquals(PhoneListRules.MAX_NEW, sent.size)
        assertEquals("e50", sent.first().id)
    }

    @Test
    fun `the Daily Mix is rebuilt only once it is half an hour old`() {
        assertFalse(PhoneListRules.mixIsFresh(builtAt = 0L, now = 1_000L))
        assertTrue(PhoneListRules.mixIsFresh(builtAt = 1_000L, now = 1_000L + 29 * 60_000L))
        assertFalse(PhoneListRules.mixIsFresh(builtAt = 1_000L, now = 1_000L + 31 * 60_000L))
    }
}
