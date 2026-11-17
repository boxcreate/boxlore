package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paced library catch-up after a foreground pass: one run at a time, in the foreground only,
 * every step tried. Runs on `backgroundScope`, which `runCurrent` drives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundCatchUpTest {
    @Test
    fun aRunTriesEveryStepEvenWhenOneFails() = runTest {
        val ran = mutableListOf<String>()
        val catchUp =
            ForegroundCatchUp(
                backgroundScope,
                MutableStateFlow(true),
                listOf(
                    {
                        ran += "stubs"
                        error("offline")
                    },
                    { ran += "opml" },
                ),
            )
        catchUp.request()
        runCurrent()
        assertEquals(listOf("stubs", "opml"), ran)
    }

    @Test
    fun aSecondRequestJoinsNothingWhileARunIsGoing() = runTest {
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val step: suspend () -> Unit = {
            runs++
            gate.await()
        }
        val catchUp = ForegroundCatchUp(backgroundScope, MutableStateFlow(true), listOf(step))
        catchUp.request()
        runCurrent()
        assertNull("one run at a time", catchUp.request())
        gate.complete(Unit)
        runCurrent()
        catchUp.request()
        runCurrent()
        assertEquals(2, runs)
    }

    @Test
    fun leavingTheForegroundStopsTheRunAndABackgroundRequestStartsNothing() = runTest {
        val foreground = MutableStateFlow(true)
        var finished = false
        val step: suspend () -> Unit = {
            delay(60_000L)
            finished = true
        }
        val catchUp = ForegroundCatchUp(backgroundScope, foreground, listOf(step))
        val job = catchUp.request()
        runCurrent()
        foreground.value = false
        runCurrent()
        assertTrue(job!!.isCompleted)
        assertTrue("the paced work stopped with the app in the background", !finished)
        assertNull(catchUp.request())
    }
}
