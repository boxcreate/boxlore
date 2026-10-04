package cx.aswin.boxlore.feature.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSerialResolutionLimiterTest {
    @Test
    fun `large subscription library never resolves more than two shows concurrently`() = runTest {
        val limiter = HomeSerialResolutionLimiter()
        val finish = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        var completed = 0
        repeat(20) {
            launch {
                limiter.resolve {
                    active++
                    peak = maxOf(active, peak)
                    finish.await()
                    active--
                    completed++
                }
            }
        }
        runCurrent()
        assertEquals(2, active)
        finish.complete(Unit)
        runCurrent()
        assertEquals(2, peak)
        assertEquals(20, completed)
    }

    @Test
    fun `cancellation releases a resolution slot`() = runTest {
        val limiter = HomeSerialResolutionLimiter()
        val blocker = CompletableDeferred<Unit>()
        val first = launch { limiter.resolve { blocker.await() } }
        val second = launch { limiter.resolve { blocker.await() } }
        var thirdStarted = false
        launch { limiter.resolve { thirdStarted = true } }
        runCurrent()
        assertEquals(false, thirdStarted)
        first.cancel()
        runCurrent()
        assertEquals(true, thirdStarted)
        second.cancel()
    }
}
