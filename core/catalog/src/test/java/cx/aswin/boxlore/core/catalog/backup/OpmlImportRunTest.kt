package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.ApiRouteBudget
import cx.aswin.boxlore.core.testing.TestFixtures
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The OPML run loop: misses, waits and giving up, progress on retries, and the screen import coming before the catch-up. */
class OpmlImportRunTest {
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    @Test
    fun `a catch-up gives an unanswered outline up only after five misses over a week`() = runTest {
        val pending = MemoryPendingOpmlImports()
        val feed = feed(1)
        pending.add(listOf(feed))
        var now = 0L

        repeat(OpmlImportRun.GIVE_UP_AFTER_MISSES) { miss ->
            catchUp(pending) { now }
            now += OpmlImportRun.retryWaitMs(miss + 1)
        }
        assertEquals(PendingOpmlMisses(5, 0L, now - OpmlImportRun.retryWaitMs(5)), pending.misses(feed))
        assertEquals(listOf(feed), pending.all(), "five misses in a few days (the phone may have been offline) lose nothing")

        now = 8 * day
        val report = catchUp(pending) { now }

        assertEquals(listOf(feed), report.gaveUp)
        assertTrue(report.pending.isEmpty() && pending.all().isEmpty())
    }

    @Test
    fun `a missed outline isn't asked again until its wait has passed, and the wait grows`() = runTest {
        val pending = MemoryPendingOpmlImports()
        val feed = feed(1)
        pending.add(listOf(feed))
        var now = 10 * day
        var tries = 0
        val catchUp = suspend {
            OpmlImportRun.run(
                pending.all(),
                pending,
                forgetWhenNothingImported = false,
                importOne = {
                    tries++
                    OpmlFeedImport.Pending
                },
                countMisses = true,
                nowMs = { now },
            )
        }

        catchUp()
        assertEquals(1, tries)
        now += hour / 2
        val waiting = catchUp()
        assertEquals(1, tries, "half an hour after its miss the pass sends nothing for it")
        assertEquals(listOf(feed), waiting.pending, "and it is still pending")
        assertEquals(1, pending.misses(feed)?.count, "a pass that skipped it counts no miss")

        now += hour / 2
        catchUp()
        assertEquals(2, tries, "an hour after its first miss it is asked again")
        now += 5 * hour
        catchUp()
        assertEquals(2, tries, "the second wait is longer")
        now += hour
        catchUp()
        assertEquals(3, tries)
        now += 23 * hour
        catchUp()
        assertEquals(3, tries, "from the third miss on it waits a day")
        now += hour
        catchUp()
        assertEquals(4, tries)
    }

    @Test
    fun `an outline that got an error isn't asked again in the same run`() = runTest {
        val feeds = (1..2).map(::feed)
        val tried = mutableMapOf<String, Int>()

        val report =
            OpmlImportRun.run(
                feeds,
                MemoryPendingOpmlImports(),
                forgetWhenNothingImported = false,
                importOne = { feed ->
                    tried.merge(feed.xmlUrl, 1, Int::plus)
                    if (feed == feeds[0]) OpmlFeedImport.Pending else OpmlFeedImport.Imported(TestFixtures.podcast(id = feed.xmlUrl))
                },
            )

        assertEquals(1, tried[feeds[0].xmlUrl], "a server error waits for a later catch-up, not a second round now")
        assertEquals(listOf(feeds[0]), report.pending)
    }

    @Test
    fun `the import screen counts no misses`() = runTest {
        val pending = MemoryPendingOpmlImports()
        val feed = feed(1)
        repeat(10) {
            OpmlImportRun.run(listOf(feed), pending, forgetWhenNothingImported = false, importOne = { OpmlFeedImport.Pending }, nowMs = { 30 * day })
        }
        assertEquals(PendingOpmlMisses(1, 0L), pending.missed(feed, 0L))
    }

    @Test
    fun `a retry round reports the outline it tries again`() = runTest {
        val feeds = (1..3).map(::feed)
        val tried = mutableMapOf<String, Int>()
        val progress = mutableListOf<Pair<Int, String>>()

        val report =
            OpmlImportRun.run(
                feeds,
                MemoryPendingOpmlImports(),
                forgetWhenNothingImported = true,
                importOne = { feed ->
                    val count = tried.merge(feed.xmlUrl, 1, Int::plus)!!
                    if (feed == feeds[0] && count == 1) OpmlFeedImport.Limited else OpmlFeedImport.Imported(TestFixtures.podcast(id = feed.xmlUrl))
                },
                onProgress = { index, feed, _ -> progress += index to feed.title },
            )

        assertEquals(3, report.imported.size)
        assertEquals(listOf(0 to "Show 1", 1 to "Show 2", 2 to "Show 3", 2 to "Show 1"), progress)
    }

    @Test
    fun `a run told to stop leaves what it didn't settle pending and counts no misses`() = runTest {
        val pending = MemoryPendingOpmlImports()
        val feeds = (1..4).map(::feed)
        var tries = 0

        val report =
            OpmlImportRun.run(
                feeds,
                pending,
                forgetWhenNothingImported = false,
                importOne = {
                    tries++
                    OpmlFeedImport.Pending
                },
                countMisses = true,
                shouldStop = { tries >= 2 },
            )

        assertEquals(2, tries)
        assertEquals(feeds.toSet(), report.pending.toSet())
        assertEquals(feeds.toSet(), pending.all().toSet())
        assertEquals(PendingOpmlMisses(1, 0L), pending.missed(feeds[0], 0L))
    }

    @Test
    fun `an import the listener starts makes a running catch-up stop after its current outline`() = runTest {
        val gate = OpmlRunGate()
        val pending = MemoryPendingOpmlImports()
        val waiting = (1..5).map(::feed)
        pending.add(waiting)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var catchUpTries = 0

        val catchUp =
            async {
                gate.catchUp { yieldToScreen ->
                    OpmlImportRun.run(
                        pending.all(),
                        pending,
                        forgetWhenNothingImported = false,
                        importOne = { feed ->
                            catchUpTries++
                            if (catchUpTries == 1) {
                                firstStarted.complete(Unit)
                                releaseFirst.await()
                            }
                            OpmlFeedImport.Imported(TestFixtures.podcast(id = feed.xmlUrl))
                        },
                        countMisses = true,
                        shouldStop = yieldToScreen,
                    )
                }
            }
        firstStarted.await()
        var screenRan = false
        val screen = launch { gate.screen { screenRan = true } }
        yield()
        assertFalse(screenRan, "the screen waits for the outline in progress")
        releaseFirst.complete(Unit)
        screen.join()
        val report = catchUp.await()!!

        assertTrue(screenRan)
        assertEquals(1, catchUpTries, "the catch-up stopped after its current outline")
        assertEquals(waiting.drop(1).toSet(), pending.all().toSet(), "the rest are still pending for the next catch-up")
        assertEquals(waiting.drop(1).toSet(), report.pending.toSet())
    }

    @Test
    fun `a catch-up waiting for room gives way to an import at once`() = runTest {
        val clock = AtomicLong(0L)
        val budget = ApiRouteBudget(perMinute = 1, nowMs = { clock.get() }, sleep = { clock.addAndGet(it) })
        var screenWaiting = false
        val slept = mutableListOf<Long>()
        val pace =
            OpmlCatchUpPace(budget, yieldToScreen = { screenWaiting }) { ms ->
                slept += ms
                clock.addAndGet(ms)
                screenWaiting = true
            }

        assertTrue(pace.pace(ApiRouteBudget.PODCAST))
        assertFalse(pace.pace(ApiRouteBudget.PODCAST), "full route: it checks within a second and gives way")
        assertEquals(listOf(OpmlCatchUpPace.POLL_MS), slept)
    }

    private suspend fun catchUp(pending: PendingOpmlImports, now: () -> Long) = OpmlImportRun.run(
        pending.all(),
        pending,
        forgetWhenNothingImported = false,
        importOne = { OpmlFeedImport.Pending },
        countMisses = true,
        nowMs = now,
    )

    @Test
    fun `outlines that couldn't be saved first are never asked for`() = runTest {
        // Reviewed: a failed save was ignored, so outlines asked for could be lost if the app closed.
        val unsaved = object : PendingOpmlImports by MemoryPendingOpmlImports() {
            override fun add(feeds: List<OpmlFeed>): Boolean = false
        }
        var asked = 0

        val report =
            OpmlImportRun.run((1..3).map(::feed), unsaved, forgetWhenNothingImported = true, importOne = {
                asked++
                OpmlFeedImport.Skipped
            })

        assertEquals(0, asked)
        assertTrue(report.saveFailed)
    }

    @Test
    fun `an import the rate limit held back entirely keeps its outlines for the catch-up`() = runTest {
        // Reviewed: an import that followed nothing forgot every outline, including those only held back by a 429.
        val feeds = (1..3).map(::feed)
        val pending = MemoryPendingOpmlImports()

        val report = OpmlImportRun.run(feeds, pending, forgetWhenNothingImported = true, importOne = { OpmlFeedImport.Limited })

        assertEquals(feeds.toSet(), pending.all().toSet())
        assertEquals(feeds.toSet(), report.pending.toSet())
    }

    @Test
    fun `a run whose outlines an account switch cleared stops instead of following them`() = runTest {
        // Reviewed: a run begun before an account switch kept following the old account's outlines.
        val feeds = (1..4).map(::feed)
        val pending = MemoryPendingOpmlImports()
        val asked = mutableListOf<OpmlFeed>()

        OpmlImportRun.run(feeds, pending, forgetWhenNothingImported = false, importOne = { feed ->
            asked += feed
            if (asked.size == 2) pending.clear()
            OpmlFeedImport.Imported(TestFixtures.podcast(id = feed.xmlUrl))
        })

        assertEquals(feeds.take(2), asked)
    }

    @Test
    fun `a file listing a show twice imports every show once and leaves nothing untried`() = runTest {
        // Reviewed: the second outline for a feed found it settled, which read as an account switch and stopped the run.
        val feeds = (1..4).map(::feed)
        val renamed = OpmlFeed("Show 3 (old name)", feeds[2].xmlUrl)
        val file = listOf(feeds[0], feeds[1], feeds[1], feeds[2], renamed, feeds[3])
        val pending = MemoryPendingOpmlImports()
        val asked = mutableListOf<OpmlFeed>()

        val report =
            OpmlImportRun.run(file, pending, forgetWhenNothingImported = true, importOne = { feed ->
                asked += feed
                OpmlFeedImport.Imported(TestFixtures.podcast(id = feed.xmlUrl))
            })

        assertEquals(feeds, asked, "each address once, the first outline for it, and the rest of the file in the same run")
        assertEquals(feeds.map { it.xmlUrl }, report.imported.map { it.id })
        assertTrue(report.pending.isEmpty(), "nothing untried")
        assertTrue(pending.all().isEmpty(), "and nothing left in the store")
    }

    @Test
    fun `a duplicate outline the API couldn't answer for stays pending once`() = runTest {
        val feeds = (1..2).map(::feed)
        val pending = MemoryPendingOpmlImports()

        val report =
            OpmlImportRun.run(listOf(feeds[0], feeds[0], feeds[1]), pending, forgetWhenNothingImported = false, importOne = { feed ->
                if (feed == feeds[0]) OpmlFeedImport.Pending else OpmlFeedImport.Imported(TestFixtures.podcast(id = feed.xmlUrl))
            })

        assertEquals(listOf(feeds[0]), report.pending)
        assertEquals(listOf(feeds[0]), pending.all())
    }

    private fun feed(index: Int) = OpmlFeed("Show $index", "https://feeds.example/$index")
}
