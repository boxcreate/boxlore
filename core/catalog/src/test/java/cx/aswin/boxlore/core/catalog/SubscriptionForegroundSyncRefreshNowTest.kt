package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

    /** Regression: a pull over a large library kept its spinner up until every feed was read. */
    @Test
    fun aLongPullStopsWaitingAfterFifteenSecondsWhileItsPassCarriesOn() = runTest {
        var runs = 0
        var finished = false
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = {
                    runs++
                    delay(60_000L)
                    finished = true
                },
                periodicIntervalMs = 0L,
                nowMs = { testScheduler.currentTime },
            )

        sync.refreshNow()

        assertEquals(SubscriptionForegroundSync.PULL_WAIT_MAX_MS, testScheduler.currentTime)
        assertFalse(finished, "the pass is still reading")

        advanceTimeBy(5_000L)
        sync.refreshNow() // A second pull while the first pass still runs joins it.
        assertEquals(1, runs)

        advanceUntilIdle()
        assertTrue(finished, "the pass was never cancelled")
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

    @Test
    fun aPullReadsMoreFeedsAtOnceThanAnAutomaticPass() = runTest {
        suspend fun peakReads(): Int {
            var inFlight = 0
            var peak = 0
            val ids = (1..12).map { "p$it" }.toSet()
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { ids },
                loadPodcastMeta = { id -> DirectFeedTipMeta("https://feeds.example/$id.xml", id, null, null, null, null) },
                syncChunk = { emptyMap() },
                saveLatest = { _, _ -> },
                directFeed =
                DirectFeedSyncSeams(
                    loadOptedInIds = { ids },
                    resolveFeedTip = { _, _ ->
                        inFlight++
                        peak = maxOf(peak, inFlight)
                        delay(50)
                        inFlight--
                        DirectFeedResolveResult(tip = null, persisted = false)
                    },
                    feedConcurrency = 2,
                    manualFeedConcurrency = 4,
                ),
            )
            return peak
        }

        assertEquals(2, peakReads())
        assertEquals(4, withContext(ManualRefreshPass()) { peakReads() })
    }
}
