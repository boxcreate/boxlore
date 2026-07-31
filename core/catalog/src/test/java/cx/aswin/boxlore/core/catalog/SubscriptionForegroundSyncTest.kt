package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionForegroundSyncTest {
    @Test
    fun ensureStartedRunsSyncActionOnlyOnce() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = this,
                    initialDelayMs = 2_000L,
                    syncAction = { runs++ },
                )

            assertFalse(sync.hasStarted())
            sync.ensureStarted()
            sync.ensureStarted()
            sync.ensureStarted()
            assertTrue(sync.hasStarted())

            runCurrent()
            assertEquals(0, runs)
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(1, runs)
        }

    @Test
    fun ensureStartedHonorsInitialDelay() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = this,
                    initialDelayMs = 500L,
                    syncAction = { runs++ },
                )

            sync.ensureStarted()
            advanceTimeBy(499L)
            runCurrent()
            assertEquals(0, runs)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(1, runs)
        }
}
