package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** New Episodes pull to refresh, swipe to queue and swipe to played, with their Undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class LatestEpisodeActionsTest {
    private val podcast: Podcast = TestFixtures.podcast(id = "p")
    private val episode: Episode = TestFixtures.episode(id = "e1")

    private val queued = mutableListOf<String>()
    private val removedUndone = mutableListOf<String>()
    private val markedPlayed = mutableListOf<String>()
    private val shownPlayed = CompletableDeferred<Unit>()

    private fun actions(
        scope: CoroutineScope,
        refreshNow: suspend () -> Unit = {},
        limit: Int = 50,
    ) = LatestEpisodeActions(
        scope = scope,
        refreshNow = refreshNow,
        queue = LatestQueue(
            queuedIds = { queued.toList() },
            add = { added, _ -> queued.size < limit && queued.add(added.id) },
            removeUndone = { id -> removedUndone += id },
            limit = limit,
        ),
        markPlayed = { played, _ -> markedPlayed += played.id },
        awaitShownPlayed = { shownPlayed.await() },
    )

    @Test
    fun `a second pull while one runs does not start another, and the spinner clears after`() = runTest {
        val release = CompletableDeferred<Unit>()
        var runs = 0
        val latest = actions(this, refreshNow = {
            runs++
            release.await()
        })

        latest.refresh()
        runCurrent()
        latest.refresh()
        runCurrent()
        assertEquals(1, runs)
        assertTrue(latest.isRefreshing.value)

        release.complete(Unit)
        runCurrent()
        assertFalse(latest.isRefreshing.value)

        latest.refresh()
        runCurrent()
        assertEquals(2, runs)
    }

    @Test
    fun `a failed refresh still clears the spinner`() = runTest {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, error -> failures += error },
        )
        val latest = actions(scope, refreshNow = { error("offline") })

        latest.refresh()
        runCurrent()

        assertFalse(latest.isRefreshing.value)
        assertEquals("offline", failures.single().message)
    }

    @Test
    fun `swiping to played holds the row and saves nothing until Undo closes`() = runTest {
        val latest = actions(this)

        latest.holdPlayed(episode.id)
        runCurrent()

        assertEquals(setOf("e1"), latest.pendingPlayedIds.value)
        assertEquals(emptyList<String>(), markedPlayed)
    }

    @Test
    fun `undo on played lets the row go and nothing is saved, even if commit comes after`() = runTest {
        val latest = actions(this)

        latest.holdPlayed(episode.id)
        latest.undoPlayed(episode.id)
        latest.commitPlayed(episode, podcast)
        runCurrent()

        assertEquals(emptySet<String>(), latest.pendingPlayedIds.value)
        assertEquals(emptyList<String>(), markedPlayed)
    }

    @Test
    fun `a committed swipe is saved and held until the list shows it played`() = runTest {
        val latest = actions(this)

        latest.holdPlayed(episode.id)
        latest.commitPlayed(episode, podcast)
        runCurrent()
        assertEquals(listOf("e1"), markedPlayed)
        assertEquals(setOf("e1"), latest.pendingPlayedIds.value)

        shownPlayed.complete(Unit)
        runCurrent()
        assertEquals(emptySet<String>(), latest.pendingPlayedIds.value)
    }

    @Test
    fun `a committed swipe lets go after the wait even if the list never shows it`() = runTest {
        val latest = LatestEpisodeActions(
            scope = this,
            refreshNow = {},
            queue = LatestQueue({ emptyList() }, { _, _ -> true }, {}, limit = 50),
            markPlayed = { played, _ -> markedPlayed += played.id },
            awaitShownPlayed = { awaitCancellation() },
        )

        latest.holdPlayed(episode.id)
        latest.commitPlayed(episode, podcast)
        advanceTimeBy(PLAYED_SETTLE_TIMEOUT_MS + 1)
        runCurrent()

        assertEquals(listOf("e1"), markedPlayed)
        assertEquals(emptySet<String>(), latest.pendingPlayedIds.value)
    }

    @Test
    fun `swipe to queue adds once, reports already queued and a full queue`() = runTest {
        val latest = actions(this, limit = 1)

        assertEquals(LatestQueueOutcome.Added, latest.addToQueue(episode, podcast))
        assertEquals(LatestQueueOutcome.AlreadyQueued, latest.addToQueue(episode, podcast))
        assertEquals(LatestQueueOutcome.QueueFull, latest.addToQueue(TestFixtures.episode(id = "e2"), podcast))
        assertEquals(listOf("e1"), queued)
    }

    @Test
    fun `undo on queue removes the episode without counting it as turned down`() = runTest {
        val latest = actions(this)

        latest.addToQueue(episode, podcast)
        latest.undoAddToQueue(episode.id)
        runCurrent()

        assertEquals(listOf("e1"), removedUndone)
    }
}
