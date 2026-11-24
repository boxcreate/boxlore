package cx.aswin.boxlore.feature.player.v2

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueRemovalUndoTest {
    private val undone = mutableListOf<String>()
    private val confirmed = mutableListOf<String>()

    @Test
    fun `a message cancelled by a layout change still confirms the removal once`() = runTest {
        val waiting = CompletableDeferred<Boolean>()
        val job = launch {
            settleQueueRemoval("episode-2", awaitUndo = { waiting.await() }, undo = { undone += it }, confirm = { confirmed += it })
        }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(listOf("episode-2"), confirmed)
        assertEquals(emptyList<String>(), undone)
    }

    @Test
    fun `Undo restores the episode and never confirms it`() = runTest {
        settleQueueRemoval("episode-2", awaitUndo = { true }, undo = { undone += it }, confirm = { confirmed += it })
        assertEquals(listOf("episode-2"), undone)
        assertEquals(emptyList<String>(), confirmed)
    }

    @Test
    fun `a message that times out confirms the removal once`() = runTest {
        settleQueueRemoval("episode-2", awaitUndo = { false }, undo = { undone += it }, confirm = { confirmed += it })
        assertEquals(listOf("episode-2"), confirmed)
        assertEquals(emptyList<String>(), undone)
    }
}
