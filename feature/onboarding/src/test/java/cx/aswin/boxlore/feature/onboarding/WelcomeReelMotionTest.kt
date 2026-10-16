package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WelcomeReelMotionTest {
    @Test
    fun `disabling animations completes the text reveal and enabling them again never hides it`() {
        val disabledDuringEntry = WelcomeReelMotion.isRevealComplete(100L, animationsDisabled = true, alreadyComplete = false)
        assertTrue(disabledDuringEntry)
        assertTrue(WelcomeReelMotion.isRevealComplete(100L, animationsDisabled = false, alreadyComplete = disabledDuringEntry))
    }

    @Test
    fun `the final reply completes its reveal once and stays visible`() {
        val end = WelcomeEntryTiming.LastLineEndMillis
        assertFalse(WelcomeReelMotion.isRevealComplete(end - 1L, animationsDisabled = false, alreadyComplete = false))
        assertTrue(WelcomeReelMotion.isRevealComplete(end, animationsDisabled = false, alreadyComplete = false))
        assertTrue(WelcomeReelMotion.isRevealComplete(0L, animationsDisabled = false, alreadyComplete = true))
    }

    @Test
    fun `negative elapsed time stays at the initial position`() {
        assertEquals(0.0, WelcomeReelMotion.distanceDp(Long.MIN_VALUE))
        assertEquals(0.0, WelcomeReelMotion.distanceDp(-1L))
        assertEquals(0.0, WelcomeReelMotion.distanceDp(0L))
    }

    private fun speedAt(millis: Long, rowDelay: Long = 0L): Double =
        (WelcomeReelMotion.distanceDp(millis + 1L, rowDelay) - WelcomeReelMotion.distanceDp(millis - 1L, rowDelay)) * 500.0

    @Test
    fun `covers glide a short distance then keep drifting without ever stopping`() {
        // The pre-refresh welcome motion: a 120dp ease-out glide with a slow drift underneath.
        val glideEnd = WelcomeReelMotion.EntranceMillis
        val drifted = (glideEnd - WelcomeReelMotion.DriftDelayMillis) / 1000.0 * WelcomeReelMotion.DriftSpeedDpPerSecond
        assertEquals(WelcomeReelMotion.EntranceTravelDp + drifted, WelcomeReelMotion.distanceDp(glideEnd), 0.0001)
        for (millis in listOf(150L, 400L, 700L, 900L, 1100L, 3000L, 60_000L)) {
            assertTrue(speedAt(millis) > 0.0, "moving at $millis ms")
        }
        assertEquals(WelcomeReelMotion.DriftSpeedDpPerSecond, speedAt(5_000L), 0.0001)
    }

    @Test
    fun `the glide eases out after a soft start`() {
        assertTrue(speedAt(100L) < speedAt(300L))
        assertTrue(speedAt(500L) > speedAt(1000L))
    }

    @Test
    fun `the lower row starts its glide a beat later`() {
        val delay = WelcomeReelMotion.RowStaggerMillis
        assertTrue(WelcomeReelMotion.distanceDp(200L, delay) < WelcomeReelMotion.distanceDp(200L))
        // Before the shared drift starts, the lower row's glide is the upper row's, shifted in time.
        assertEquals(WelcomeReelMotion.distanceDp(300L), WelcomeReelMotion.distanceDp(300L + delay, delay), 0.0001)
    }

    @Test
    fun `covers fade up from part opacity during the glide`() {
        assertEquals(WelcomeReelMotion.FadeFromAlpha, WelcomeReelMotion.coverAlpha(0L), 0.0001f)
        assertTrue(WelcomeReelMotion.coverAlpha(400L) in WelcomeReelMotion.FadeFromAlpha..1f)
        assertEquals(1f, WelcomeReelMotion.coverAlpha(WelcomeReelMotion.FadeMillis), 0.0001f)
    }

    @Test
    fun `the reel always moves forward`() {
        val positions = (0L..6000L step 25L).map { WelcomeReelMotion.distanceDp(it) }
        assertTrue(positions.zipWithNext().all { (earlier, later) -> later >= earlier })
    }

    @Test
    fun `whole cycles return the same repeated row seam`() {
        val width = 208.0
        for (loops in listOf(0, 1, 2, 100, 10_000)) {
            assertEquals(0f, WelcomeReelMotion.wrappedOffsetDp(width * loops, width))
            assertEquals(-208f, WelcomeReelMotion.wrappedOffsetDp(width * loops, width, reverse = true))
        }

        val beforeWrap = WelcomeReelMotion.wrappedOffsetDp(width - 0.01, width)
        val afterWrap = WelcomeReelMotion.wrappedOffsetDp(width + 0.01, width)
        // Modulo one repeated copy, travel across the seam still advances by 0.02dp.
        assertEquals(-0.02, afterWrap - beforeWrap - width, 0.00002)
    }

    @Test
    fun `fractional positions remain identical after arbitrary multiple loops`() {
        val width = 208.0
        for (reverse in listOf(false, true)) {
            val first = WelcomeReelMotion.wrappedOffsetDp(13.75, width, reverse, phaseFraction = 0.3)
            for (loops in listOf(1, 7, 251, 10_000)) {
                val repeated =
                    WelcomeReelMotion.wrappedOffsetDp(13.75 + loops * width, width, reverse, phaseFraction = 0.3)
                assertEquals(first, repeated, 0.0001f)
                assertTrue(repeated >= -width && repeated <= 0.0)
            }
        }
    }

    @Test
    fun `reverse travels rightward while forward travels leftward`() {
        assertEquals(-30f, WelcomeReelMotion.wrappedOffsetDp(30.0, 200.0))
        assertEquals(-170f, WelcomeReelMotion.wrappedOffsetDp(30.0, 200.0, reverse = true))
        assertEquals(-10f, WelcomeReelMotion.wrappedOffsetDp(190.0, 200.0, reverse = true))

        val beforeWrap = WelcomeReelMotion.wrappedOffsetDp(199.99, 200.0, reverse = true)
        val afterWrap = WelcomeReelMotion.wrappedOffsetDp(200.01, 200.0, reverse = true)
        assertEquals(0.02, afterWrap - beforeWrap + 200.0, 0.00002)
    }

    @Test
    fun `phase is independent of travel and normalizes whole cycles`() {
        assertEquals(-50f, WelcomeReelMotion.wrappedOffsetDp(0.0, 200.0, phaseFraction = 0.25))
        assertEquals(-150f, WelcomeReelMotion.wrappedOffsetDp(0.0, 200.0, reverse = true, phaseFraction = 0.25))
        assertEquals(-50f, WelcomeReelMotion.wrappedOffsetDp(0.0, 200.0, phaseFraction = 100.25))
        assertEquals(-150f, WelcomeReelMotion.wrappedOffsetDp(0.0, 200.0, phaseFraction = -0.25))
        assertEquals(-150f, WelcomeReelMotion.wrappedOffsetDp(-50.0, 200.0))
    }

    @Test
    fun `long running clocks stay finite and bounded`() {
        val distance = WelcomeReelMotion.distanceDp(Long.MAX_VALUE)
        assertTrue(distance.isFinite() && distance > 0.0)
        for (reverse in listOf(false, true)) {
            val offset = WelcomeReelMotion.wrappedOffsetDp(distance, 208.0, reverse)
            assertTrue(offset.isFinite() && offset >= -208f && offset <= 0f)
        }
    }

    @Test
    fun `invalid widths or nonfinite inputs produce a safe stationary offset`() {
        for (width in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE)) {
            assertEquals(0f, WelcomeReelMotion.wrappedOffsetDp(20.0, width))
        }
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(0f, WelcomeReelMotion.wrappedOffsetDp(invalid, 208.0))
            assertEquals(0f, WelcomeReelMotion.wrappedOffsetDp(20.0, 208.0, phaseFraction = invalid))
        }
    }
}
