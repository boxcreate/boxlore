package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WelcomeMotionClockTest {
    @Test
    fun `negative and zero frame deltas preserve elapsed time`() {
        for (delta in listOf(Long.MIN_VALUE, -1L, 0L)) {
            assertEquals(12_500_000.0, WelcomeFrameTiming.advanceElapsedNanos(12_500_000.0, delta, 1f))
        }
    }

    @Test
    fun `ordinary frames accumulate nanoseconds without changing their duration`() {
        val elapsed = WelcomeFrameTiming.advanceElapsedNanos(12_500_000.0, 16_000_000L, 1f)
        assertEquals(28_500_000.0, elapsed)
        assertEquals(28L, WelcomeFrameTiming.elapsedMillis(elapsed))
    }

    @Test
    fun `a frame at or beyond the limit advances by at most 100 milliseconds`() {
        for (delta in listOf(100_000_000L, 100_000_001L, Long.MAX_VALUE)) {
            val elapsed = WelcomeFrameTiming.advanceElapsedNanos(12_500_000.0, delta, 1f)
            assertEquals(112_500_000.0, elapsed)
            assertEquals(112L, WelcomeFrameTiming.elapsedMillis(elapsed))
        }
    }

    @Test
    fun `half duration scale doubles elapsed motion time`() {
        val elapsed = WelcomeFrameTiming.advanceElapsedNanos(0.0, 16_000_000L, 0.5f)
        assertEquals(32_000_000.0, elapsed)
        assertEquals(32L, WelcomeFrameTiming.elapsedMillis(elapsed))
    }

    @Test
    fun `tenfold duration scale slows elapsed motion time to one tenth`() {
        val elapsed = WelcomeFrameTiming.advanceElapsedNanos(0.0, 16_000_000L, 10f)
        assertEquals(1_600_000.0, elapsed)
        assertEquals(1L, WelcomeFrameTiming.elapsedMillis(elapsed))
    }

    @Test
    fun `long frame gaps are capped before applying the duration scale`() {
        assertEquals(200_000_000.0, WelcomeFrameTiming.advanceElapsedNanos(0.0, Long.MAX_VALUE, 0.5f))
        assertEquals(10_000_000.0, WelcomeFrameTiming.advanceElapsedNanos(0.0, Long.MAX_VALUE, 10f))
    }

    @Test
    fun `publishing milliseconds retains fractional time for the following frames`() {
        var elapsed = 0.0
        val publishedMillis = listOf(1L, 2L, 3L, 5L)
        repeat(4) {
            elapsed = WelcomeFrameTiming.advanceElapsedNanos(elapsed, 2_500_000L, 2f)
            assertEquals(publishedMillis[it], WelcomeFrameTiming.elapsedMillis(elapsed))
        }
        assertEquals(5_000_000.0, elapsed)
    }

    @Test
    fun `nanoseconds convert to milliseconds only at whole millisecond boundaries`() {
        assertEquals(0L, WelcomeFrameTiming.elapsedMillis(0.0))
        assertEquals(0L, WelcomeFrameTiming.elapsedMillis(999_999.0))
        assertEquals(1L, WelcomeFrameTiming.elapsedMillis(1_000_000.0))
        assertEquals(1L, WelcomeFrameTiming.elapsedMillis(1_999_999.0))
    }
}
