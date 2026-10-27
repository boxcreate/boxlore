package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules a renumbered show's follow moves by ([RenumberedFollowMerge]). */
class RenumberedFollowMergeTest {
    @Test
    fun aFollowedNewShowKeepsItsOwnChoicesAndFollowTimeAndTakesAnyOnSwitch() {
        val old = row("1", subscribedAt = 100L).copy(notificationsEnabled = true, customGenre = "Old genre", preferredSort = "oldest", skipEndingOverrideMs = 5_000L)
        val current = row("2", subscribedAt = 200L).copy(autoDownloadEnabled = true, customGenre = "New genre", customGenreIcon = "icon")

        val move = RenumberedFollowMerge.move(old, "2", current, answer = null, nowMs = 1_000L)

        assertEquals("the follow time is never moved earlier", 200L, move.kept.subscribedAt)
        assertTrue(move.kept.notificationsEnabled)
        assertTrue(move.kept.autoDownloadEnabled)
        assertEquals("New genre", move.kept.customGenre)
        assertEquals("icon", move.kept.customGenreIcon)
        assertEquals("oldest", move.kept.preferredSort)
        assertEquals(5_000L, move.kept.skipEndingOverrideMs)
        assertTrue(move.notificationsMoved)
        assertFalse("auto-download was already on", move.autoDownloadMoved)
        assertFalse(move.retired.isSubscribed)
        assertEquals(1_000L, move.retired.unsubscribedAt)
        assertTrue(move.kept.isDirty && move.retired.isDirty)
    }

    @Test
    fun anUnfollowedStoredNewShowKeepsItsNamespaceAndTakesTheOldFollow() {
        val old = row("1", subscribedAt = 100L).copy(epNs = 1L, customGenre = "Crime")
        val stored = row("2", subscribedAt = 0L).copy(isSubscribed = false, unsubscribedAt = 50L, epNs = 2L, title = "Stored title")

        val move = RenumberedFollowMerge.move(old, "2", stored, answer = null, nowMs = 1_000L)

        assertTrue(move.kept.isSubscribed)
        assertEquals("followed at the move, never at the old show's earlier time", 1_000L, move.kept.subscribedAt)
        assertEquals(0L, move.kept.unsubscribedAt)
        assertEquals("Crime", move.kept.customGenre)
        assertEquals("Stored title", move.kept.title)
        assertEquals("a stored row keeps its own namespace, never the old show's", 2L, move.kept.epNs)
    }

    @Test
    fun aMovedFollowIsNeverOlderThanTheNewShowsLastUnfollow() {
        val old = row("1", subscribedAt = 100L)
        // A clock behind the stored unfollow (another device's, pulled earlier) still follows after it.
        val stored = row("2", subscribedAt = 0L).copy(isSubscribed = false, unsubscribedAt = 5_000L)

        val move = RenumberedFollowMerge.move(old, "2", stored, answer = null, nowMs = 1_000L)

        assertEquals(5_001L, move.kept.subscribedAt)
    }

    @Test
    fun sharedFeedsAreFoundByAddressOrGuidNeverByTitle() {
        val followed =
            listOf(
                row("1", feedUrl = "https://feeds.acast.com/public/shows/x"),
                row("2", feedUrl = "http://www.feeds.acast.com/public/shows/x/"),
                row("3", feedUrl = "https://feeds.megaphone.fm/x").copy(title = "Same title"),
                row("4", feedUrl = "https://other.example/y").copy(title = "Same title"),
                row("5", feedUrl = "https://a.example").copy(podcastGuid = "GUID-1"),
                row("6", feedUrl = "https://b.example").copy(podcastGuid = "guid-1"),
                row("rss:7", feedUrl = "https://feeds.acast.com/public/shows/x").copy(sourceType = PodcastEntity.SOURCE_RSS),
            )

        assertEquals(setOf("1", "2", "5", "6"), RenumberedFollowMerge.sharedFeedCandidates(followed).toSet())
    }

    @Test
    fun idListsReplaceTheOldIdInPlaceOnce() {
        assertEquals(listOf("a", "2", "b"), RenumberedFollowMerge.replacedId(listOf("a", "1", "b"), "1", "2"))
        assertEquals(listOf("2", "a"), RenumberedFollowMerge.replacedId(listOf("2", "a", "1"), "1", "2"))
        assertEquals(listOf("a"), RenumberedFollowMerge.replacedId(listOf("a"), "1", "2"))
    }

    private fun row(id: String, subscribedAt: Long = 100L, feedUrl: String? = null) = PodcastEntity(
        podcastId = id,
        title = "Show $id",
        author = "",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        subscribedAt = subscribedAt,
        feedUrl = feedUrl,
    )
}
