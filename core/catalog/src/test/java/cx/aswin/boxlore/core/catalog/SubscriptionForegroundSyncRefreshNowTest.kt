package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Pull to refresh on New Episodes: [SubscriptionForegroundSync.refreshNow]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionForegroundSyncRefreshNowTest {
    @BeforeEach
    fun resetProcessGuard() {
        SubscriptionForegroundSync.resetProcessGuardForTests()
    }

    @Test
    fun refreshNowRunsInsideTheCooldown() = runTest {
        var runs = 0
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = { runs++ },
                periodicIntervalMs = 0L,
                nowMs = { testScheduler.currentTime },
            )

        sync.refreshNow()
        sync.refreshNow()

        assertEquals(2, runs)
    }

    @Test
    fun refreshNowLetsAnAutomaticPassFinishThenReadsEveryFeed() = runTest {
        val manualPasses = mutableListOf<Boolean>()
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = {
                    manualPasses += isManualRefreshPass()
                    delay(1_000L)
                },
                periodicIntervalMs = 0L,
                nowMs = { testScheduler.currentTime },
            )

        sync.requestRefresh()
        runCurrent()
        advanceTimeBy(100L)

        sync.refreshNow()

        // The screen's own pass skips feeds inside their wait, so the pull runs a pass of its own.
        assertEquals(listOf(false, true), manualPasses)
        assertTrue(testScheduler.currentTime >= 2_000L)
    }

    @Test
    fun aSecondPullJoinsTheFirstInsteadOfStartingAnother() = runTest {
        var runs = 0
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = {
                    runs++
                    delay(1_000L)
                },
                periodicIntervalMs = 0L,
                nowMs = { testScheduler.currentTime },
            )

        val first = launch { sync.refreshNow() }
        runCurrent()
        advanceTimeBy(100L)
        sync.refreshNow()
        first.join()

        assertEquals(1, runs)
    }

    @Test
    fun aPullReadsFeedsStillInsideTheirWait() = runTest {
        val notPastWait: suspend (String) -> Boolean = { false }

        assertFalse(isFeedDueThisPass("https://example.com/feed.xml", notPastWait))
        withContext(ManualRefreshPass()) {
            assertTrue(isFeedDueThisPass("https://example.com/feed.xml", notPastWait))
            // A show with no feed address still has nothing to read.
            assertFalse(isFeedDueThisPass(null, notPastWait))
        }
        assertTrue(isFeedDueThisPass("https://example.com/feed.xml") { true })
    }

    @Test
    fun refreshNowDoesNothingInTheBackground() = runTest {
        var runs = 0
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = { runs++ },
                periodicIntervalMs = 0L,
                nowMs = { testScheduler.currentTime },
            )
        sync.setForeground(false)

        sync.refreshNow()

        assertEquals(0, runs)
    }
}
