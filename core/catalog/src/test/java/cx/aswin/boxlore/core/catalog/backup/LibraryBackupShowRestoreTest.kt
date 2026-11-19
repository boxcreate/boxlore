package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.database.PodcastEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryBackupShowRestoreTest {
    private val rssRow = PodcastEntity(
        podcastId = "rss:private",
        title = "Members Feed",
        author = "Host",
        imageUrl = "https://example.com/art.jpg",
        description = null,
        isSubscribed = true,
        subscribedAt = 1_000L,
        sourceType = PodcastEntity.SOURCE_RSS,
        feedUrl = "https://example.com/private.xml",
        rssRefreshCapability = PodcastEntity.RSS_REFRESH_AUTOMATIC,
        rssCatalogStale = false,
        linkedPodcastIndexId = "42",
    )

    @Test
    fun `an rss show whose feed can't be read is followed from the backup and marked for a later read`() {
        val podcast = LibraryBackupShowRestore.unreadRssPodcast(rssRow)!!

        assertEquals("rss:private", podcast.id)
        assertEquals("Members Feed", podcast.title)
        assertEquals("https://example.com/art.jpg", podcast.imageUrl)
        assertEquals("https://example.com/private.xml", podcast.feedUrl)
        assertEquals("42", podcast.linkedPodcastIndexId)
        assertTrue(podcast.isRss)
        assertTrue(podcast.rssCatalogStale)
        assertEquals(PodcastEntity.RSS_REFRESH_AUTOMATIC, podcast.rssRefreshCapability)
    }

    @Test
    fun `a row without an address or an rss id has nothing to follow`() {
        assertNull(LibraryBackupShowRestore.unreadRssPodcast(rssRow.copy(feedUrl = null)))
        assertNull(LibraryBackupShowRestore.unreadRssPodcast(rssRow.copy(feedUrl = " ")))
        assertNull(LibraryBackupShowRestore.unreadRssPodcast(rssRow.copy(podcastId = "1001")))
    }

    @Test
    fun `a podcast index row restores as saved`() {
        val podcast = LibraryBackupShowRestore.podcastFromBackup(
            rssRow.copy(podcastId = "1001", sourceType = PodcastEntity.SOURCE_PODCAST_INDEX, rssCatalogStale = false),
        )

        assertEquals("1001", podcast.id)
        assertFalse(podcast.isRss)
        assertFalse(podcast.rssCatalogStale)
    }
}
