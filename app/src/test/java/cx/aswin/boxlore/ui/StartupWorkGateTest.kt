package cx.aswin.boxlore.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupWorkGateTest {
    @Test
    fun `leaving before first content still starts application background work`() = runTest {
        val gate = StartupWorkGate()
        gate.onUiCreated()
        var started = false
        launch { gate.runWhenReady { started = true } }
        gate.onMainQueueIdle()
        runCurrent()
        assertEquals(false, started)
        gate.onUiStopped()
        runCurrent()
        assertEquals(true, started)
        gate.markReady()
        runCurrent()
        assertEquals(true, started)
    }

    @Test
    fun `headless process releases optional work at the first idle queue`() = runTest {
        val gate = StartupWorkGate()
        var started = false
        launch { gate.runWhenReady { started = true } }
        runCurrent()
        assertEquals(false, started)
        gate.onMainQueueIdle()
        runCurrent()
        assertEquals(true, started)
    }

    @Test
    fun `idle queue during loading does not release UI startup work`() = runTest {
        val gate = StartupWorkGate()
        gate.onUiCreated()
        var started = false
        launch { gate.runWhenReady { started = true } }
        gate.onMainQueueIdle()
        runCurrent()
        assertEquals(false, started)
        gate.markReady()
        runCurrent()
        assertEquals(true, started)
    }

    @Test
    fun `optional initializers do not run concurrently after readiness`() = runTest {
        val gate = StartupWorkGate()
        val finishFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        launch {
            gate.runWhenReady {
                events += "first"
                finishFirst.await()
                events += "first_done"
            }
        }
        launch { gate.runWhenReady { events += "second" } }
        gate.markReady()
        runCurrent()
        assertEquals(listOf("first"), events)
        finishFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "first_done", "second"), events)
    }

    @Test
    fun `cancelled initializer releases the next optional task`() = runTest {
        val gate = StartupWorkGate()
        gate.markReady()
        val first = launch { gate.runWhenReady { CompletableDeferred<Unit>().await() } }
        runCurrent()
        var started = false
        launch { gate.runWhenReady { started = true } }
        runCurrent()
        assertEquals(false, started)
        first.cancel()
        runCurrent()
        assertEquals(true, started)
    }

    @Test
    fun `background tasks wait for committed content and start only once`() = runTest {
        val gate = StartupWorkGate()
        var started = 0
        repeat(3) {
            launch {
            gate.awaitReady()
            started++
        }
        }
        runCurrent()
        assertEquals(0, started)
        gate.markReady()
        runCurrent()
        assertEquals(3, started)
        gate.markReady()
        runCurrent()
        assertEquals(3, started)
    }

    @Test
    fun `cancelled screen waiter does not cancel the shared startup gate`() = runTest {
        val gate = StartupWorkGate()
        val cancelled = launch { gate.awaitReady() }
        runCurrent()
        cancelled.cancel()
        var started = false
        launch {
            gate.awaitReady()
            started = true
        }
        runCurrent()
        assertEquals(false, started)
        gate.markReady()
        runCurrent()
        assertEquals(true, started)
    }
}
