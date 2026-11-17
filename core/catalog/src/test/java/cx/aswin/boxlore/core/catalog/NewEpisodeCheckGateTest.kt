package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewEpisodeCheckGateTest {
    private val minute = 60_000L

    @BeforeEach
    fun resetProcessGuard() {
        SubscriptionForegroundSync.resetProcessGuardForTests()
    }

    private fun TestScope.syncCounting(onRun: suspend () -> Unit = {}): Pair<SubscriptionForegroundSync, () -> Int> {
        var runs = 0
        val sync =
            SubscriptionForegroundSync(
                scope = backgroundScope,
                initialDelayMs = 0L,
                syncAction = {
                    runs++
                    onRun()
                },
                nowMs = { testScheduler.currentTime },
            )
        return sync to { runs }
    }

    private fun TestScope.gateFor(sync: SubscriptionForegroundSync): NewEpisodeCheckGate = NewEpisodeCheckGate(
        scope = backgroundScope,
        setSyncForeground = sync::setForeground,
        requestPass = sync::requestRefresh,
        nowMs = { testScheduler.currentTime },
    )

    @Test
    fun `a car opening New episodes runs one automatic pass while boxlore is closed`() = runTest {
        // Before, the car only ever showed what the last on-screen pass saved: a closed app never checked.
        val (sync, runs) = syncCounting()
        val gate = gateFor(sync)
        gate.setAppVisible(false)
        runCurrent()
        assertEquals(0, runs())

        assertTrue(gate.carListsOpened())
        runCurrent()
        assertEquals(1, runs())

        // One pass, not the timer: hours later, still closed, nothing more ran.
        advanceTimeBy(3 * 60 * minute)
        runCurrent()
        assertEquals(1, runs())
    }

    @Test
    fun `the car's pass keeps the cooldown of the pass boxlore just ran`() = runTest {
        val (sync, runs) = syncCounting()
        val gate = gateFor(sync)
        gate.setAppVisible(true)
        sync.ensureStarted()
        runCurrent()
        assertEquals(1, runs())

        gate.setAppVisible(false)
        advanceTimeBy(minute)
        assertTrue(gate.carListsOpened())
        advanceTimeBy(NewEpisodeCheckGate.DEFAULT_CAR_WINDOW_MS + minute)
        runCurrent()
        assertEquals(1, runs())
    }

    @Test
    fun `the car asks for one check per half hour however often it browses or reconnects`() = runTest {
        val (sync, runs) = syncCounting()
        val gate = gateFor(sync)
        gate.setAppVisible(false)

        assertTrue(gate.carListsOpened())
        runCurrent()
        repeat(3) { assertFalse(gate.carListsOpened()) }
        gate.carDriveEnded()
        advanceTimeBy(NewEpisodeCheckGate.DEFAULT_CAR_MIN_INTERVAL_MS - minute)
        assertFalse(gate.carListsOpened())
        runCurrent()
        assertEquals(1, runs())

        advanceTimeBy(minute)
        assertTrue(gate.carListsOpened())
        runCurrent()
        assertEquals(2, runs())
    }

    @Test
    fun `the drive ending stops a pass still running and closes the gate`() = runTest {
        var cancelled = false
        val (sync, _) =
            syncCounting {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        val gate = gateFor(sync)
        gate.setAppVisible(false)
        gate.carListsOpened()
        runCurrent()
        assertFalse(cancelled)

        gate.carDriveEnded()
        runCurrent()
        assertTrue(cancelled)
    }

    @Test
    fun `a car pass reading a large library runs past five minutes to the end while the drive goes on`() = runTest {
        // An automatic pass reads up to AUTOMATIC_MAX feeds two at a time and can run well past five
        // minutes; the car window must not cut it off while the drive goes on.
        var cancelled = false
        var finished = false
        val (sync, runs) =
            syncCounting {
                try {
                    delay(8 * minute)
                    finished = true
                } finally {
                    if (!finished) cancelled = true
                }
            }
        val gate = gateFor(sync)
        gate.setAppVisible(false)
        assertTrue(gate.carListsOpened())
        advanceTimeBy(5 * minute + 1)
        runCurrent()
        assertFalse(cancelled)

        advanceTimeBy(3 * minute)
        runCurrent()
        assertTrue(finished)
        assertFalse(cancelled)
        assertEquals(1, runs())
    }

    @Test
    fun `a car pass still running when the window ends stops there`() = runTest {
        var cancelled = false
        val (sync, _) =
            syncCounting {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        val gate = gateFor(sync)
        gate.setAppVisible(false)
        gate.carListsOpened()
        advanceTimeBy(NewEpisodeCheckGate.DEFAULT_CAR_WINDOW_MS - 1)
        runCurrent()
        assertFalse(cancelled)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(cancelled)
    }

    @Test
    fun `the car window is long enough for a full automatic pass but shorter than the timer`() = runTest {
        // A full automatic pass: AUTOMATIC_MAX feeds, two at a time, at about ten seconds a read.
        val fullPassMs = DirectFeedRefreshBatch.AUTOMATIC_MAX / SubscriptionForegroundSync.DEFAULT_FEED_CONCURRENCY * 10_000L
        assertTrue(NewEpisodeCheckGate.DEFAULT_CAR_WINDOW_MS > fullPassMs)
        // Opened with boxlore closed, the window must never let the foreground timer start a second pass.
        assertTrue(NewEpisodeCheckGate.DEFAULT_CAR_WINDOW_MS < SubscriptionForegroundSync.DEFAULT_PERIODIC_INTERVAL_MS)

        val (sync, runs) = syncCounting()
        val gate = gateFor(sync)
        gate.setAppVisible(false)
        gate.carListsOpened()
        advanceTimeBy(NewEpisodeCheckGate.DEFAULT_CAR_WINDOW_MS + minute)
        runCurrent()
        assertEquals(1, runs())
    }

    @Test
    fun `the car window closes on its own and never outlasts boxlore's own foreground`() = runTest {
        val published = mutableListOf<Boolean>()
        var passes = 0
        val gate =
            NewEpisodeCheckGate(
                scope = backgroundScope,
                setSyncForeground = { published += it },
                requestPass = { passes++ },
                nowMs = { testScheduler.currentTime },
            )
        gate.setAppVisible(false)
        gate.carListsOpened()
        assertEquals(listOf(false, true), published)
        assertEquals(1, passes)

        // Opening and leaving boxlore during the window keeps it open until the window ends.
        gate.setAppVisible(true)
        gate.setAppVisible(false)
        assertTrue(published.last())
        advanceTimeBy(NewEpisodeCheckGate.DEFAULT_CAR_WINDOW_MS - 1)
        runCurrent()
        assertTrue(published.last())
        advanceTimeBy(1)
        runCurrent()
        assertFalse(published.last())

        // With boxlore on screen, the window ending changes nothing.
        advanceTimeBy(NewEpisodeCheckGate.DEFAULT_CAR_MIN_INTERVAL_MS)
        gate.setAppVisible(true)
        gate.carListsOpened()
        advanceTimeBy(NewEpisodeCheckGate.DEFAULT_CAR_WINDOW_MS + 1)
        runCurrent()
        assertTrue(published.last())
    }

    @Test
    fun `with no car and boxlore closed, nothing checks feeds`() = runTest {
        // Background checks need the listener's consent: a closed app with no player asks for none.
        val (sync, runs) = syncCounting()
        val gate = gateFor(sync)
        gate.setAppVisible(false)
        sync.ensureStarted()
        sync.requestRefresh()
        advanceTimeBy(3 * 60 * minute)
        runCurrent()
        assertEquals(0, runs())
    }
}
