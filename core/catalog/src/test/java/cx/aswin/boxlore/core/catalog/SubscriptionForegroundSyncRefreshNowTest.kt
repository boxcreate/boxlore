package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun refreshNowWaitsForARunningPassInsteadOfStartingAnother() = runTest {
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

        sync.requestRefresh()
        runCurrent()
        assertEquals(1, runs)
        advanceTimeBy(100L)

        sync.refreshNow()

        assertEquals(1, runs)
        assertTrue(testScheduler.currentTime >= 1_000L)
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
