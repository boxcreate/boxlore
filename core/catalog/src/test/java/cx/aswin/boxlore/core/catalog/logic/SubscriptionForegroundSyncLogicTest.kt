package cx.aswin.boxlore.core.catalog.logic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionForegroundSyncLogicTest {
    @Test
    fun `in-flight always skips`() {
        assertTrue(
            SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                inFlight = true,
                lastCompletedAtMs = 0L,
                nowMs = 10_000L,
                cooldownMs = 5_000L,
            ),
        )
    }

    @Test
    fun `never-completed does not skip so subscriptions-first can fetch`() {
        assertFalse(
            SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                inFlight = false,
                lastCompletedAtMs = SubscriptionForegroundSyncLogic.NEVER_COMPLETED_MS,
                nowMs = 10_000L,
                cooldownMs = 5_000L,
            ),
        )
    }

    @Test
    fun `completed at time zero still honors cooldown`() {
        assertTrue(
            SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                inFlight = false,
                lastCompletedAtMs = 0L,
                nowMs = 0L,
                cooldownMs = 5_000L,
            ),
        )
    }

    @Test
    fun `cooldown skips until the window elapses`() {
        assertTrue(
            SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                inFlight = false,
                lastCompletedAtMs = 1_000L,
                nowMs = 5_999L,
                cooldownMs = 5_000L,
            ),
        )
        assertFalse(
            SubscriptionForegroundSyncLogic.shouldSkipRefresh(
                inFlight = false,
                lastCompletedAtMs = 1_000L,
                nowMs = 6_000L,
                cooldownMs = 5_000L,
            ),
        )
    }

    @Test
    fun `catalog ingest skips blank and rss library ids`() {
        assertFalse(SubscriptionForegroundSyncLogic.shouldRequestCatalogIngest(""))
        assertFalse(SubscriptionForegroundSyncLogic.shouldRequestCatalogIngest("   "))
        assertFalse(SubscriptionForegroundSyncLogic.shouldRequestCatalogIngest("rss:abc"))
        assertTrue(SubscriptionForegroundSyncLogic.shouldRequestCatalogIngest("123456"))
    }
}
