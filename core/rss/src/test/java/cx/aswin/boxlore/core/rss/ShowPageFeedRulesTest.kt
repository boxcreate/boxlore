package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.database.UnfollowedCatalogCopy
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShowPageFeedRulesTest {
    @Test
    fun aCopyOfTheNewestItemsNeverAnswersAnArchiveReadWithA304() {
        val partial = copy(archiveComplete = false)

        assertEquals("etag" to "lm", ShowPageFeedRules.validators(wholeFeed = false, cached = partial))
        assertNull(ShowPageFeedRules.validators(wholeFeed = true, cached = partial), "an archive read of a partial copy goes out unconditional")
        assertFalse(ShowPageFeedRules.acceptsNotModified(wholeFeed = true, cachedArchiveComplete = false))
        assertTrue(ShowPageFeedRules.acceptsNotModified(wholeFeed = false, cachedArchiveComplete = false))
    }

    @Test
    fun aCompleteCopySendsItsValidatorsForEitherRead() {
        val complete = copy(archiveComplete = true)

        assertEquals("etag" to "lm", ShowPageFeedRules.validators(wholeFeed = true, cached = complete))
        assertTrue(ShowPageFeedRules.acceptsNotModified(wholeFeed = true, cachedArchiveComplete = true))
    }

    @Test
    fun noCopyOrNoValidatorsReadsUnconditionally() {
        assertNull(ShowPageFeedRules.validators(wholeFeed = false, cached = null))
        assertNull(ShowPageFeedRules.validators(wholeFeed = false, cached = copy(itemCount = 0)))
        assertNull(ShowPageFeedRules.validators(wholeFeed = false, cached = copy(etag = null, lastModified = null)))
    }

    @Test
    fun anOldestFirstFeedIsAlwaysReadWhole() {
        assertTrue(ShowPageFeedRules.readsWholeFeed(Scope.NEWEST, copy(order = LocalFeedOrder.OLDEST_FIRST)))
        assertFalse(ShowPageFeedRules.readsWholeFeed(Scope.NEWEST, copy(order = LocalFeedOrder.NEWEST_FIRST)))
        assertTrue(ShowPageFeedRules.readsWholeFeed(Scope.ARCHIVE, null))
    }

    @Test
    fun cleanupRemovesCopiesUnusedForThirtyDaysButNeverTheOneOpening() {
        val now = 100L * DAY
        val copies =
            listOf(
                UnfollowedCatalogCopy("old", lastOpenedAt = now - 31 * DAY, fetchedAt = now - 40 * DAY, episodeCount = 10),
                UnfollowedCatalogCopy("recent", lastOpenedAt = now - 2 * DAY, fetchedAt = now - 2 * DAY, episodeCount = 10),
                UnfollowedCatalogCopy("refetched", lastOpenedAt = 0L, fetchedAt = now - DAY, episodeCount = 10),
                UnfollowedCatalogCopy("opening", lastOpenedAt = now - 60 * DAY, fetchedAt = now - 60 * DAY, episodeCount = 10),
            )

        assertEquals(listOf("old"), ShowPageFeedRules.cleanupVictims(copies, now, keep = "opening"))
    }

    @Test
    fun cleanupKeepsTheMostRecentlyOpenedCopiesUnderTheCaps() {
        val now = 100L * DAY
        val many = (0 until ShowPageFeedRules.MAX_UNFOLLOWED_SHOWS + 3).map { UnfollowedCatalogCopy("s$it", now - it * 1_000L, 0L, 1) }
        assertEquals(listOf("s40", "s41", "s42"), ShowPageFeedRules.cleanupVictims(many, now, keep = null))

        val big =
            listOf(
                UnfollowedCatalogCopy("newest", now - 1, 0L, 15_000),
                UnfollowedCatalogCopy("middle", now - 2, 0L, 6_000),
                UnfollowedCatalogCopy("small", now - 3, 0L, 4_000),
            )
        // 15,000 + 6,000 is over the 20,000 episode cap; the next copy that fits still stays.
        assertEquals(listOf("middle"), ShowPageFeedRules.cleanupVictims(big, now, keep = null))
    }

    private fun copy(
        archiveComplete: Boolean = false,
        itemCount: Int = 100,
        etag: String? = "etag",
        lastModified: String? = "lm",
        order: String = LocalFeedOrder.NEWEST_FIRST,
    ) = LocalEpisodeFeedEntity(
        podcastId = "920666",
        feedUrl = "https://feeds.example/920666.xml",
        feedEtag = etag,
        feedLastModified = lastModified,
        fetchedAt = 1L,
        itemCount = itemCount,
        feedOrder = order,
        ttlExpiresAt = null,
        needsFullBackfill = !archiveComplete,
        copiedExtrasCount = 0,
        ready = false,
        feedUrlLookupAt = 0L,
        archiveComplete = archiveComplete,
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
