package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowListTooLongException
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Mark all as played or unplayed reads the whole show the way Select all does, page after page,
 * and writes only once every page was read, one Mark all at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastInfoMarkAllTest {
    /** A show of [count] episodes, newest first. */
    private fun show(count: Int): List<Episode> = (count downTo 1).map { TestFixtures.episode(id = "e$it", publishedDate = it.toLong()) }

    private fun page(loaded: List<Episode>) = PodcastInfoUiState.Success(
        podcast = TestFixtures.podcast(id = "p1"),
        episodes = loaded,
        isSubscribed = true,
    )

    /** The page's own whole-show read: [loadPodcastEpisodeSelectionWindow] over [show], newest first. */
    private fun wholeShow(
        show: List<Episode>,
        loaded: List<Episode> = show.take(20),
        failAt: Int? = null,
        offsets: MutableList<Int> = mutableListOf(),
    ): suspend () -> List<Episode> = {
        loadPodcastEpisodeSelectionWindow(page(loaded), EpisodeSort.NEWEST) { limit, offset, _ ->
            offsets += offset
            if (offset == failAt) throw IOException("HTTP 429")
            val slice = show.drop(offset).take(limit)
            PodcastRepository.EpisodePage(slice, hasMore = offset + slice.size < show.size)
        }.episodes
    }

    @Test
    fun `mark all reaches every episode of a show longer than 200, once each`() = runTest {
        // Reported: Mark all as played changed only the newest 200 episodes though the dialog said all.
        val show = show(450)
        val offsets = mutableListOf<Int>()
        val written = mutableListOf<List<Episode>>()

        val marked = markWholeShow(wholeShow(show, offsets = offsets)) { written += it }

        assertEquals(MarkAllOutcome.MARKED, marked)
        assertEquals(1, written.size, "one batched write")
        assertEquals(show.map { it.id }.toSet(), written.single().map { it.id }.toSet())
        assertEquals(450, written.single().size)
        assertEquals(listOf(0, 100, 200, 300, 400), offsets)
    }

    @Test
    fun `a page that fails writes nothing and reports the failure`() = runTest {
        val written = mutableListOf<List<Episode>>()
        val failures = mutableListOf<Exception>()

        val marked = markWholeShow(wholeShow(show(450), failAt = 100), onFailure = { failures += it }) { written += it }

        assertEquals(MarkAllOutcome.FAILED, marked)
        assertTrue(written.isEmpty(), "never the loaded episodes passed off as all of them")
        assertEquals(1, failures.size)
    }

    @Test
    fun `episodes the page lists that the pages don't are marked too`() = runTest {
        // A feed-only extra the page already shows, beyond a long show's pages.
        val extra = TestFixtures.episode(id = "-9", publishedDate = 1_000)
        val show = show(250)
        val written = mutableListOf<Episode>()

        markWholeShow(wholeShow(show, loaded = listOf(extra) + show.take(20))) { written += it }

        assertEquals(251, written.size)
        assertTrue(written.any { it.id == "-9" })
    }

    @Test
    fun `a source that answers an empty last page after a failure would end the read early, so the whole-show read throws instead`() = runTest {
        // Reviewed: the server's list swallowed a failed page as an empty last page, so Mark all marked part of the show.
        val show = show(450)
        val written = mutableListOf<List<Episode>>()
        val strict: suspend () -> List<Episode> = {
            loadPodcastEpisodeSelectionWindow(page(show.take(20)), EpisodeSort.NEWEST) { limit, offset, _ ->
                // The whole-show read: a failed page throws rather than answering EpisodePage(empty, hasMore = false).
                if (offset == 200) throw IOException("/v2/episodes failed at offset 200")
                val slice = show.drop(offset).take(limit)
                PodcastRepository.EpisodePage(slice, hasMore = offset + slice.size < show.size)
            }.episodes
        }

        assertEquals(MarkAllOutcome.FAILED, markWholeShow(strict) { written += it })
        assertTrue(written.isEmpty(), "never the first pages and the loaded episodes passed off as all of them")
    }

    private val uiState = MutableStateFlow<PodcastInfoUiState>(page(emptyList()))

    private fun state() = uiState.value as PodcastInfoUiState.Success

    @Test
    fun `a second tap while Mark all reads the show starts no second read, and the page shows it running`() = runTest {
        // Reviewed: Mark all now reads the whole show first, and a repeat tap started a second whole-show read.
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val written = mutableListOf<List<Episode>>()
        val markAll =
            PodcastInfoMarkAll(this, uiState, loadWholeShow = {
                reads++
                gate.await()
                show(3)
            })

        assertTrue(markAll.start("p1") { written += it })
        runCurrent()
        assertTrue(state().isMarkingAll)
        assertFalse(markAll.start("p1") { written += it }, "ignored while the first runs")
        runCurrent()
        assertEquals(1, reads)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(state().isMarkingAll)
        assertEquals(1, written.size)
        assertTrue(markAll.start("p1") { written += it }, "a later tap runs again")
        advanceUntilIdle()
        assertEquals(2, reads)
    }

    @Test
    fun `a Mark all that fails says nothing was marked and stops running`() = runTest {
        val markAll = PodcastInfoMarkAll(this, uiState, loadWholeShow = { throw IOException("HTTP 429") })
        val written = mutableListOf<List<Episode>>()

        markAll.start("p1") { written += it }
        advanceUntilIdle()

        assertTrue(written.isEmpty())
        assertFalse(state().isMarkingAll)
        assertEquals(MARK_ALL_FAILED_MESSAGE, state().userMessage)
    }

    @Test
    fun `leaving the page while Mark all reads the show still marks every episode`() = runTest {
        // Reviewed: Mark all ran in the page's scope, so pressing Back during a long read silently marked nothing.
        val pageScope = TestScope(testScheduler)
        val appScope = TestScope(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val written = mutableListOf<List<Episode>>()
        val markAll =
            PodcastInfoMarkAll(
                pageScope,
                uiState,
                loadWholeShow = {
                    gate.await()
                    show(250)
                },
                workScope = appScope,
            )

        markAll.start("p1") { written += it }
        runCurrent()
        pageScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, written.size, "one batched write after the page closed")
        assertEquals(250, written.single().size)
        assertFalse(state().isMarkingAll)
    }

    @Test
    fun `a read the page owned that stops with the page counts as a failure, never a part of the show`() = runTest {
        val written = mutableListOf<List<Episode>>()
        val failures = mutableListOf<Exception>()

        val marked =
            markWholeShow({ throw CancellationException("the page's feed read stopped") }, { failures += it }) {
                written += it
            }

        assertEquals(MarkAllOutcome.FAILED, marked)
        assertTrue(written.isEmpty())
        assertEquals(1, failures.size)
    }

    @Test
    fun `a show longer than the server's list pages through marks nothing and says it is too long, not to check the connection`() = runTest {
        // Reviewed: the route's last offset failed like a dropped connection, so every retry read 11 pages and failed again.
        val written = mutableListOf<List<Episode>>()
        val tooLong: suspend () -> List<Episode> = {
            loadPodcastEpisodeSelectionWindow(page(show(20)), EpisodeSort.NEWEST) { _, offset, _ ->
                if (offset >= 11_000) throw ShowListTooLongException()
                PodcastRepository.EpisodePage(show(1_000).map { it.copy(id = "o$offset-${it.id}") }, hasMore = true)
            }.episodes
        }

        assertEquals(MarkAllOutcome.TOO_LONG, markWholeShow(tooLong) { written += it })
        assertTrue(written.isEmpty())

        PodcastInfoMarkAll(this, uiState, tooLong).start("p1") { written += it }
        advanceUntilIdle()
        assertTrue(written.isEmpty())
        assertEquals(MARK_ALL_TOO_LONG_MESSAGE, state().userMessage)
    }

    @Test
    fun `a page reload while Mark all runs keeps the page showing it running`() = runTest {
        // Reviewed: the show loader's fresh state cleared the flag, so the menu came back while Mark all still ran.
        val gate = CompletableDeferred<Unit>()
        val markAll = PodcastInfoMarkAll(this, uiState, loadWholeShow = {
            gate.await()
            show(3)
        })

        markAll.start("p1") {}
        runCurrent()
        uiState.value = page(show(20))
        runCurrent()
        assertTrue(state().isMarkingAll)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(state().isMarkingAll)
    }

    @Test
    fun `Mark all from a page opened again waits for the first, so the last tap's write lands last`() = runTest {
        // Reviewed: each page guarded only its own Mark all, so a slower first read could write after the second.
        val appScope = TestScope(testScheduler)
        val firstGate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val first =
            PodcastInfoMarkAll(TestScope(testScheduler), uiState, loadWholeShow = {
                firstGate.await()
                show(3)
            }, workScope = appScope)
        val reopened = PodcastInfoMarkAll(TestScope(testScheduler), uiState, loadWholeShow = { show(3) }, workScope = appScope)

        first.start("p1") { writes += "played" }
        runCurrent()
        reopened.start("p1") { writes += "unplayed" }
        runCurrent()
        assertTrue(writes.isEmpty(), "the second waits for the first")

        firstGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("played", "unplayed"), writes)
    }

    @Test
    fun `a Mark all that fails after the page closed writes nothing`() = runTest {
        val pageScope = TestScope(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val written = mutableListOf<List<Episode>>()
        val markAll =
            PodcastInfoMarkAll(pageScope, uiState, loadWholeShow = {
                gate.await()
                throw IOException("HTTP 429")
            }, workScope = this)

        markAll.start("p1") { written += it }
        runCurrent()
        pageScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(written.isEmpty())
    }
}
