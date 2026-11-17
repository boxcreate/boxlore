package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedLoadsTest {
    @Test
    fun callersOfOneKeyShareOneLoadAndACancelledCallerDoesNotStopIt() = runTest {
        val loads = SharedLoads<String, String>(backgroundScope)
        val release = CompletableDeferred<Unit>()
        var runs = 0
        val load: suspend () -> String = {
            runs++
            release.await()
            "chapters"
        }

        val first = async { loads.load("a", load) }
        val cancelled = async { loads.load("a", load) }
        val third = async { loads.load("a", load) }
        advanceUntilIdle()
        cancelled.cancel()
        release.complete(Unit)

        assertEquals("chapters", first.await())
        assertEquals("chapters", third.await())
        assertEquals(1, runs)
        advanceUntilIdle()
        assertEquals(0, loads.runningCount())

        assertEquals("chapters", loads.load("a", load))
        assertEquals(2, runs)
    }
}
