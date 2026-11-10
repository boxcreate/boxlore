package cx.aswin.boxlore.feature.widgets.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetProgressPollerTest {
    private val interval = 20_000L

    @Test
    fun asksForThePositionAtEachIntervalOnlyWhilePlaying() = runTest {
        val playing = MutableStateFlow(false)
        var refreshes = 0
        val job = launch { WidgetProgressPoller.run(playing, interval, hasWidgets = { true }) { refreshes++ } }

        advanceTimeBy(interval * 3)
        assertEquals("paused: nothing to refresh", 0, refreshes)

        playing.value = true
        runCurrent()
        advanceTimeBy(interval * 3 + 1)
        assertEquals(3, refreshes)

        // Pausing stops the polling straight away, and playing again starts a fresh interval.
        playing.value = false
        advanceTimeBy(interval * 5)
        assertEquals(3, refreshes)
        playing.value = true
        runCurrent()
        advanceTimeBy(interval + 1)
        assertEquals(4, refreshes)
        job.cancel()
    }

    @Test
    fun doesNotTouchThePlayerWhileNoPlaybackWidgetIsPlaced() = runTest {
        var placed = false
        var refreshes = 0
        val job = launch { WidgetProgressPoller.run(MutableStateFlow(true), interval, hasWidgets = { placed }) { refreshes++ } }

        advanceTimeBy(interval * 4 + 1)
        assertEquals(0, refreshes)

        // Placing a widget is picked up at the next interval, with no restart needed.
        placed = true
        advanceTimeBy(interval)
        assertEquals(1, refreshes)
        job.cancel()
    }
}
