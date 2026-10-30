package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.ShowFeedStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Regression: subscribing showed a spinner in the top-bar pill instead of the body's status line. */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastInfoPostSubscribeLogicTest {
    private fun page(
        id: String = "p1",
        chip: DirectFeedChipState = DirectFeedChipState.Hidden,
        status: ShowFeedStatus = ShowFeedStatus.NONE,
    ) = PodcastInfoUiState.Success(
        podcast = TestFixtures.podcast(id = id),
        episodes = emptyList(),
        isSubscribed = true,
        directFeedChip = chip,
        feedStatus = status,
    )

    private fun PodcastInfoUiState.success() = this as PodcastInfoUiState.Success

    @Test
    fun `subscribing shows the body line, not the pill, while the refresh runs, then clears it`() = runTest {
        for (chip in listOf(DirectFeedChipState.Hidden, DirectFeedChipState.Offer)) {
            val ui = MutableStateFlow<PodcastInfoUiState>(page(chip = chip))
            var during: PodcastInfoUiState.Success? = null
            PodcastInfoPostSubscribeLogic.withStatusLine(ui, "p1", { "p1" }) {
                during = ui.value.success()
            }
            assertEquals(ShowFeedStatus.READING_NEWEST, during!!.feedStatus)
            assertEquals(DirectFeedChipState.Hidden, during.directFeedChip, "pill for $chip")
            assertEquals(ShowFeedStatus.NONE, ui.value.success().feedStatus)
        }
    }

    @Test
    fun `an opted-in pill stays as it is and the remounted pill survives the clear`() = runTest {
        val ui = MutableStateFlow<PodcastInfoUiState>(page(chip = DirectFeedChipState.Updated, status = ShowFeedStatus.CACHED))
        PodcastInfoPostSubscribeLogic.withStatusLine(ui, "p1", { "p1" }) {
            assertEquals(DirectFeedChipState.Updated, ui.value.success().directFeedChip)
            assertEquals(ShowFeedStatus.READING_NEWEST, ui.value.success().feedStatus)
            // The refreshed page remounts with its own pill, keeping the line it was copied with.
            ui.value = ui.value.success().copy(directFeedChip = DirectFeedChipState.Updated, episodes = listOf(TestFixtures.episode(id = "e1")))
        }
        val after = ui.value.success()
        assertEquals(ShowFeedStatus.NONE, after.feedStatus)
        assertEquals(DirectFeedChipState.Updated, after.directFeedChip)
        assertEquals(listOf("e1"), after.episodes.map { it.id })
    }

    @Test
    fun `a failed refresh clears the line and still fails`() = runTest {
        val ui = MutableStateFlow<PodcastInfoUiState>(page())
        val failure =
            runCatching {
                PodcastInfoPostSubscribeLogic.withStatusLine(ui, "p1", { "p1" }) {
                    assertEquals(ShowFeedStatus.READING_NEWEST, ui.value.success().feedStatus)
                    error("ingest failed")
                }
            }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(ShowFeedStatus.NONE, ui.value.success().feedStatus)
        assertFalse(ui.value.success().directFeedChip == DirectFeedChipState.Fetching)
    }

    @Test
    fun `a cancelled refresh clears the line`() = runTest {
        val ui = MutableStateFlow<PodcastInfoUiState>(page())
        val never = CompletableDeferred<Unit>()
        val job = launch { PodcastInfoPostSubscribeLogic.withStatusLine(ui, "p1", { "p1" }) { never.await() } }
        runCurrent()
        assertEquals(ShowFeedStatus.READING_NEWEST, ui.value.success().feedStatus)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(ShowFeedStatus.NONE, ui.value.success().feedStatus)
    }

    @Test
    fun `a feed-first line already showing is never replaced or cleared`() = runTest {
        for (status in listOf(ShowFeedStatus.COMPLETING_ARCHIVE, ShowFeedStatus.READING_NEWEST, ShowFeedStatus.FALLBACK, ShowFeedStatus.OFFLINE)) {
            val ui = MutableStateFlow<PodcastInfoUiState>(page(status = status))
            PodcastInfoPostSubscribeLogic.withStatusLine(ui, "p1", { "p1" }) {
                assertEquals(status, ui.value.success().feedStatus)
            }
            assertEquals(status, ui.value.success().feedStatus)
        }
    }

    @Test
    fun `a status a feed-first read sets meanwhile is not clobbered`() = runTest {
        val ui = MutableStateFlow<PodcastInfoUiState>(page())
        PodcastInfoPostSubscribeLogic.withStatusLine(ui, "p1", { "p1" }) {
            ui.value = ui.value.success().copy(feedStatus = ShowFeedStatus.FALLBACK)
        }
        assertEquals(ShowFeedStatus.FALLBACK, ui.value.success().feedStatus)
    }

    @Test
    fun `a page that moved to another show keeps that show's line`() = runTest {
        val ui = MutableStateFlow<PodcastInfoUiState>(page())
        var current = "p1"
        PodcastInfoPostSubscribeLogic.withStatusLine(ui, "p1", { current }) {
            current = "p2"
            ui.value = page(id = "p2", status = ShowFeedStatus.READING_NEWEST)
        }
        assertEquals("p2", ui.value.success().podcast.id)
        assertEquals(ShowFeedStatus.READING_NEWEST, ui.value.success().feedStatus)

        // Another show's page at the start is left alone too.
        val other = page(id = "p2", chip = DirectFeedChipState.Offer)
        val moved = MutableStateFlow<PodcastInfoUiState>(other)
        PodcastInfoPostSubscribeLogic.withStatusLine(moved, "p1", { "p2" }) {}
        assertSame(other, moved.value)
    }

    @Test
    fun `finished leaves loading and error states alone`() {
        assertSame(PodcastInfoUiState.Loading, PodcastInfoPostSubscribeLogic.finished(PodcastInfoUiState.Loading, "p1", "p1"))
        assertSame(PodcastInfoUiState.Error, PodcastInfoPostSubscribeLogic.finished(PodcastInfoUiState.Error, "p1", "p1"))
    }
}
