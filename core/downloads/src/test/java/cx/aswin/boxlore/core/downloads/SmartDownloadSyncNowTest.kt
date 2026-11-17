package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sync Now checks followed feeds before Smart Downloads choose from the stored latest episodes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartDownloadSyncNowTest {
    @Test
    fun `Sync Now chooses downloads only after the feed check has finished`() = runTest {
        // The stored latest episode is old until the feed check reads the feed.
        var storedLatest = "old-episode"
        val feedSync =
            SubscriptionForegroundSync(
                scope = backgroundScope,
                initialDelayMs = 0L,
                syncAction = {
                    delay(5_000L)
                    storedLatest = "new-episode"
                },
            )
        var chosen: String? = null

        val synced =
            SmartDownloadSyncNow.run(
                checkFeeds = { feedSync.refreshNow() },
                sync = {
                    chosen = storedLatest
                    true
                },
            )

        assertTrue(synced)
        assertEquals("new-episode", chosen)
    }

    @Test
    fun `the feed check runs before the sync`() = runTest {
        val steps = mutableListOf<String>()

        SmartDownloadSyncNow.run(
            checkFeeds = { steps += "check feeds" },
            sync = {
                steps += "choose downloads"
                true
            },
        )

        assertEquals(listOf("check feeds", "choose downloads"), steps)
    }

    @Test
    fun `a feed check that runs out of time still lets Sync Now choose downloads`() = runTest {
        var storedLatest = "old-episode"
        // A pass on its own scope, like the followed-shows pass, which outlives Sync Now's wait.
        val feedPass =
            backgroundScope.launch {
                delay(10 * 60_000L)
                storedLatest = "new-episode"
            }
        var chosen: String? = null

        val synced =
            SmartDownloadSyncNow.run(
                checkFeeds = { feedPass.join() },
                sync = {
                    chosen = storedLatest
                    true
                },
            )

        assertTrue(synced)
        assertEquals("old-episode", chosen)
        assertEquals(SmartDownloadSyncNow.FEED_CHECK_TIMEOUT_MS, testScheduler.currentTime)
        // Only the wait stopped: the feed check keeps running on its own scope.
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals("new-episode", storedLatest)
    }

    @Test
    fun `a hung feed check is bounded by the timeout`() = runTest {
        var synced = false

        val result =
            SmartDownloadSyncNow.run(
                checkFeeds = { awaitCancellation() },
                sync = {
                    synced = true
                    true
                },
                feedCheckTimeoutMs = 1_000L,
            )

        assertTrue(result)
        assertTrue(synced)
        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun `a failed feed check still lets Sync Now choose downloads`() = runTest {
        var synced = false

        SmartDownloadSyncNow.run(
            checkFeeds = { error("offline") },
            sync = {
                synced = true
                false
            },
        )

        assertTrue(synced)
    }
}
