package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalCatalogReadyLogicTest {
    @Test
    fun notReadyWhenMissingOrHttpOrBackfillOrShort() {
        assertFalse(LocalCatalogReadyLogic.isReady(null))
        assertFalse(LocalCatalogReadyLogic.isReady(feed(feedUrl = "http://x.com/f.xml", ready = true)))
        assertFalse(LocalCatalogReadyLogic.isReady(feed(needsFullBackfill = true, ready = true)))
        assertFalse(LocalCatalogReadyLogic.isReady(feed(itemCount = 1, copiedExtrasCount = 3, ready = true)))
    }

    @Test
    fun readyWhenFullCatalogPersisted() {
        assertTrue(
            LocalCatalogReadyLogic.isReady(
                feed(needsFullBackfill = false, itemCount = 10, copiedExtrasCount = 2, ready = true),
            ),
        )
    }

    @Test
    fun notReadyWhenUnsubscribeTtlIsSet() {
        assertFalse(
            LocalCatalogReadyLogic.isReady(
                feed(
                    needsFullBackfill = false,
                    itemCount = 10,
                    copiedExtrasCount = 2,
                    ready = true,
                    ttlExpiresAt = 1L,
                ),
            ),
        )
        assertTrue(
            LocalCatalogReadyLogic.isReady(
                feed(
                    needsFullBackfill = false,
                    itemCount = 10,
                    copiedExtrasCount = 2,
                    ready = true,
                    ttlExpiresAt = null,
                ),
            ),
        )
    }

    @Test
    fun listenerIdsResolveAllowsPositivePiAndCatalogHits() {
        assertTrue(
            LocalCatalogReadyLogic.listenerIdsResolve(
                catalogIds = setOf("-1", "99"),
                listenerIds = setOf("-1", "55"),
            ),
        )
        assertFalse(
            LocalCatalogReadyLogic.listenerIdsResolve(
                catalogIds = setOf("-1"),
                listenerIds = setOf("-9"),
            ),
        )
    }

    @Test
    fun tipIsSafeWhenUnchangedOrNewerPublish() {
        assertTrue(LocalCatalogReadyLogic.tipIsSafe("-1", 100L, "-1", 100L))
        assertTrue(LocalCatalogReadyLogic.tipIsSafe("-1", 100L, "-2", 200L))
        assertFalse(LocalCatalogReadyLogic.tipIsSafe("-1", 100L, "-2", 100L))
        assertTrue(LocalCatalogReadyLogic.tipIsSafe(null, null, "-2", 100L))
    }

    @Test
    fun isReadyToFlipRequiresCountAndListenerAndTip() {
        assertFalse(
            LocalCatalogReadyLogic.isReadyToFlip(
                feedReady = false,
                catalogIds = setOf("-1"),
                listenerIds = setOf("-1"),
                existingTipId = "-1",
                existingPublishedDate = 1L,
                newTipId = "-1",
                newPublishedDate = 1L,
            ),
        )
        assertTrue(
            LocalCatalogReadyLogic.isReadyToFlip(
                feedReady = true,
                catalogIds = setOf("-1"),
                listenerIds = setOf("-1"),
                existingTipId = "-1",
                existingPublishedDate = 1L,
                newTipId = "-1",
                newPublishedDate = 1L,
            ),
        )
    }

    private fun feed(
        feedUrl: String = "https://example.com/feed.xml",
        needsFullBackfill: Boolean = false,
        itemCount: Int = 10,
        copiedExtrasCount: Int = 0,
        ready: Boolean = false,
        ttlExpiresAt: Long? = null,
    ) = LocalEpisodeFeedEntity(
        podcastId = "1",
        feedUrl = feedUrl,
        feedEtag = null,
        feedLastModified = null,
        fetchedAt = 1L,
        itemCount = itemCount,
        feedOrder = LocalFeedOrder.MIXED,
        ttlExpiresAt = ttlExpiresAt,
        needsFullBackfill = needsFullBackfill,
        copiedExtrasCount = copiedExtrasCount,
        ready = ready,
        feedUrlLookupAt = 0L,
    )
}
