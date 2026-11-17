package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectFeedRefreshBatchTest {
    private val sixHours = 6 * 60 * 60 * 1000L

    /** Followed shows in title order, as `getSubscribedPodcasts()` lists them. */
    private fun library(size: Int) = (1..size).map { "show-%03d".format(it) }

    @Test fun automaticPassReadsHalfTheLibraryWithinItsBounds() {
        val batch = DirectFeedRefreshBatch()
        val automatic = listOf(0, 12, 79, 80, 81, 154, 199, 200, 201, 913).associateWith { batch.limitFor(libraryShows = it, manual = false) }
        assertEquals(
            mapOf(0 to 40, 12 to 40, 79 to 40, 80 to 40, 81 to 41, 154 to 77, 199 to 100, 200 to 100, 201 to 100, 913 to 100),
            automatic,
        )
        assertEquals(200, batch.limitFor(libraryShows = 0, manual = true))
        assertEquals(200, batch.limitFor(libraryShows = 913, manual = true))
    }

    /**
     * Regression: with 10 feeds a pass, an alphabetical walk that restarted at "A" with every app
     * start, and early shows due again after six hours, the end of a 154-show library was never
     * read. Each session here is a fresh process (a new batch), 90 minutes apart, for three days.
     */
    @Test fun everyShowOfALargeLibraryIsReadEvenWhenEverySessionIsAFreshStart() = runTest {
        val ids = library(154)
        val lastRead = mutableMapOf<String, Long>()
        val gaps = mutableMapOf<String, Long>()
        val sessionGap = 90 * 60 * 1000L
        var now = 0L
        while (now <= 3 * 24 * 60 * 60 * 1000L) {
            val at = now
            val batch = DirectFeedRefreshBatch(nowMs = { at })
            val read = batch.select(ids, null, lastReadAt = { lastRead[it] ?: 0L }) { at - (lastRead[it] ?: -sixHours) >= sixHours }
            read.forEach { id ->
                lastRead[id]?.let { previous -> gaps[id] = maxOf(gaps[id] ?: 0L, at - previous) }
                lastRead[id] = at
            }
            if (at == sessionGap) assertEquals(ids.toSet(), lastRead.keys, "the whole library is read in the first two sessions")
            now += sessionGap
        }
        assertEquals(ids.toSet(), gaps.keys)
        assertTrue(gaps.values.all { it <= sixHours + sessionGap }, "no show waits much past its six hours: ${gaps.values.max()}")
    }

    @Test fun longestUnreadShowsGoFirstAndRecentFeedsTakeNoSlot() = runTest {
        val batch = DirectFeedRefreshBatch(automaticMin = 2, automaticMax = 2)
        val lastRead = mapOf("a" to 300L, "b" to 100L, "c" to 0L, "d" to 200L)
        val picked = batch.select(listOf("a", "b", "c", "d", "fresh"), null, lastReadAt = { lastRead[it] ?: 0L }) { it != "fresh" }
        assertEquals(listOf("c", "b"), picked)
    }

    @Test fun aFeedThatKeepsFailingWaitsBehindTheOthersThenIsTriedAgain() = runTest {
        var now = 1_000L
        val batch = DirectFeedRefreshBatch(nowMs = { now }, automaticMin = 2, automaticMax = 2)
        val lastRead = mutableMapOf("a" to 10L, "b" to 20L, "c" to 30L)
        val ids = listOf("failing", "a", "b", "c")
        suspend fun pass(): List<String> {
            val picked = batch.select(ids, null, lastReadAt = { lastRead[it] ?: 0L }) { true }
            picked.forEach { id ->
                batch.markTried(id)
                if (id != "failing") lastRead[id] = now // "failing" never reads, so its saved read stays empty.
            }
            return picked
        }

        assertEquals(listOf("failing", "a"), pass())
        now = 2_000L
        assertEquals(listOf("b", "c"), pass(), "the failed feed waits behind feeds not read yet")
        now = 3_000L
        assertEquals(listOf("failing", "a"), pass(), "once the others were read, the failed feed is tried again")
    }

    @Test fun selectingRecordsNoTrySoReadsThatNeverStartedKeepTheirPlace() = runTest {
        val batch = DirectFeedRefreshBatch(nowMs = { 1_000L }, automaticMin = 2, automaticMax = 2)
        // List order (w, x, y, z) differs from read order (y, z, w, x), so only the read times can explain the picks.
        val lastRead = mapOf("y" to 10L, "z" to 20L, "w" to 30L, "x" to 40L)
        val ids = listOf("w", "x", "y", "z")
        assertEquals(listOf("y", "z"), batch.select(ids, null, lastReadAt = { lastRead.getValue(it) }) { true })

        batch.markTried("y") // The pass was cancelled after only "y" began reading.

        assertEquals(listOf("z", "w"), batch.select(ids, null, lastReadAt = { lastRead.getValue(it) }) { true })
    }

    /** Regression: tries were stamped at selection, so a cancelled pass sent its unread picks to the back. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aCancelledPassCountsOnlyTheReadsThatStarted() = runTest {
        val batch = DirectFeedRefreshBatch(nowMs = { 1_000L }, automaticMin = 3, automaticMax = 3)
        val lastRead = mapOf("y" to 10L, "z" to 20L, "w" to 30L, "x" to 40L, "v" to 50L)
        val ids = listOf("v", "w", "x", "y", "z")
        val started = mutableListOf<String>()
        val pass =
            launch {
                SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                    loadIds = { ids.toSet() },
                    loadPodcastMeta = { id -> DirectFeedTipMeta("https://feeds.example/$id.xml", id, null, null, null, null) },
                    syncChunk = { emptyMap() },
                    saveLatest = { _, _ -> },
                    directFeed =
                    DirectFeedSyncSeams(
                        loadOptedInIds = { ids.toSet() },
                        resolveFeedTip = { id, _ ->
                            started += id
                            awaitCancellation()
                        },
                        feedConcurrency = 1,
                        selectRefreshBatch = { candidates -> batch.select(candidates, null, lastReadAt = { lastRead.getValue(it) }) { true } },
                        onFeedReadStarted = batch::markTried,
                    ),
                )
            }
        runCurrent()
        pass.cancel()

        assertEquals(listOf("y"), started, "the pass picked y, z and w but only y began reading")
        assertEquals(
            listOf("z", "w", "x"),
            batch.select(ids, null, lastReadAt = { lastRead.getValue(it) }) { true },
            "z and w were never read, so they still go before x and v",
        )
    }

    @Test fun aReadTimeInTheFutureCountsAsNeverRead() = runTest {
        var now = 1_000L
        val batch = DirectFeedRefreshBatch(nowMs = { now }, automaticMin = 1, automaticMax = 1)
        // A wrong clock once saved "skewed" a year ahead; it must not stay last forever.
        val lastRead = mapOf("skewed" to 1_000L + 365L * 24 * 60 * 60 * 1000, "recent" to 900L)
        assertEquals(listOf("skewed"), batch.select(listOf("recent", "skewed"), null, lastReadAt = { lastRead.getValue(it) }) { true })

        now = 500L // The clock went back after a try was recorded.
        batch.markTried("recent")
        now = 400L
        val saved = mapOf("other" to 350L, "recent" to 300L)
        val picked = batch.select(listOf("other", "recent"), null, lastReadAt = { saved.getValue(it) }) { true }
        assertEquals(listOf("recent"), picked, "a try stamped after now counts as never")
    }

    @Test fun aVisibleShowThatIsNotDueTakesNoSlot() = runTest {
        val batch = DirectFeedRefreshBatch(automaticMin = 2, automaticMax = 2)
        val lastRead = mapOf("visible" to 0L, "a" to 10L, "b" to 20L, "c" to 30L)
        assertEquals(
            listOf("a", "b"),
            batch.select(listOf("visible", "a", "b", "c"), "visible", lastReadAt = { lastRead.getValue(it) }) { it != "visible" },
        )
    }

    @Test fun visibleShowComesFirstAndCountsTowardTheLimit() = runTest {
        val batch = DirectFeedRefreshBatch(automaticMin = 2, automaticMax = 2)
        val lastRead = mapOf("visible" to 500L)
        assertEquals(
            listOf("visible", "old"),
            batch.select(listOf("fresh", "old", "visible", "later"), "visible", lastReadAt = { lastRead[it] ?: 0L }) { it != "fresh" },
        )
        assertTrue(batch.select(emptyList(), "missing", lastReadAt = { 0L }) { true }.isEmpty())
    }

    @Test fun pullToRefreshReadsTheWholeLibraryUpToItsCap() = runTest {
        val batch = DirectFeedRefreshBatch()
        withContext(ManualRefreshPass()) {
            assertEquals(154, batch.select(library(154), null, lastReadAt = { 0L }) { true }.size)
        }

        val big = library(250)
        val lastRead = big.withIndex().associate { (index, id) -> id to (big.size - index).toLong() }
        val pulled = withContext(ManualRefreshPass()) { DirectFeedRefreshBatch().select(big, null, lastReadAt = { lastRead.getValue(it) }) { true } }
        assertEquals(200, pulled.size)
        assertEquals(big.takeLast(200).toSet(), pulled.toSet(), "the 200 longest-unread shows go first")
    }
}
