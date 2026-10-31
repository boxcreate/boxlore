package cx.aswin.boxlore.core.playback

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** `throttleLatest`: the first value at once, then the latest at most once a period. */
class FlowThrottleTest {
    @Test
    fun theFirstValueAtOnceThenTheLatestOncePerPeriod() = runTest {
        val out = mutableListOf<Pair<Long, Int>>()
        flow {
            emit(1)
            emit(2)
            emit(3)
            delay(2_500)
            emit(4)
        }.throttleLatest(1_000).collect { out += currentTime to it }

        // 2 is dropped for 3, the latest when the first period ends; 4 comes after a quiet stretch, at once.
        assertEquals(listOf(0L to 1, 1_000L to 3, 2_500L to 4), out)
    }
}
