package cx.aswin.boxlore.feature.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeCacheRestoreGateTest {
    @Test
    fun `first Home publication waits for the entire cache snapshot`() = runTest {
        val gate = HomeCacheRestoreGate()
        val finishCacheRead = CompletableDeferred<Unit>()
        var recommendations = emptyList<String>()
        var fallback = true
        val restore = launch {
            gate.restore {
                recommendations = listOf("cached episode")
                finishCacheRead.await()
                fallback = false
            }
        }
        val firstHome = async {
            gate.awaitRestore()
            recommendations to fallback
        }
        runCurrent()
        assertFalse(firstHome.isCompleted)
        finishCacheRead.complete(Unit)
        restore.join()
        assertEquals(listOf("cached episode") to false, firstHome.await())
    }

    @Test
    fun `fresh API results cannot be overwritten by a delayed startup cache read`() = runTest {
        val gate = HomeCacheRestoreGate()
        val finishCacheRead = CompletableDeferred<Unit>()
        var displayed = emptyList<String>()
        launch {
            gate.restore {
                finishCacheRead.await()
                displayed = listOf("cached episode")
            }
        }
        val refresh = launch {
            gate.awaitRestore()
            displayed = listOf("fresh episode")
        }
        runCurrent()
        assertFalse(refresh.isCompleted)
        finishCacheRead.complete(Unit)
        refresh.join()
        gate.awaitRestore()
        assertEquals(listOf("fresh episode"), displayed)
    }

    @Test
    fun `failed cache decoding still allows local content and API refreshes to proceed`() = runTest {
        val gate = HomeCacheRestoreGate()
        val failure = runCatching {
            gate.restore { throw IllegalArgumentException("Malformed cached JSON") }
        }
        assertTrue(failure.isFailure)
        gate.awaitRestore()
    }

    @Test
    fun `cancelled cache restoration does not strand waiters`() = runTest {
        val gate = HomeCacheRestoreGate()
        val blockedRead = CompletableDeferred<Unit>()
        val restore = launch { gate.restore { blockedRead.await() } }
        val firstHome = async { gate.awaitRestore() }
        runCurrent()
        assertFalse(firstHome.isCompleted)
        restore.cancel()
        restore.join()
        firstHome.await()
    }

    @Test
    fun `empty caches complete immediately and readiness stays available`() = runTest {
        val gate = HomeCacheRestoreGate()
        gate.restore { }
        repeat(2) { gate.awaitRestore() }
        assertEquals(0L, testScheduler.currentTime)
    }
}
