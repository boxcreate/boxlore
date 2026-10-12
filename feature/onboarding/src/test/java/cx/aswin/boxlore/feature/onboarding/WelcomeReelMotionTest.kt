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
        assertFalse(WelcomeReelMotion.isRevealComplete(1299L, animationsDisabled = false, alreadyComplete = false))
        assertTrue(WelcomeReelMotion.isRevealComplete(1300L, animationsDisabled = false, alreadyComplete = false))
        assertTrue(WelcomeReelMotion.isRevealComplete(0L, animationsDisabled = false, alreadyComplete = true))
    }

    @Test
    fun `negative elapsed time stays at the initial position`() {
        assertEquals(0.0, WelcomeReelMotion.distanceDp(Long.MIN_VALUE))
        assertEquals(0.0, WelcomeReelMotion.distanceDp(-1L))
        assertEquals(0.0, WelcomeReelMotion.distanceDp(0L))
    }

    @Test
    fun `intro integrates the fast hold and quadratic slowdown before steady travel`() {
        assertEquals(2400L, WelcomeReelMotion.IntroDurationMillis)
        assertEquals(144.0, WelcomeReelMotion.distanceDp(600L), 0.000001)
        assertEquals(276.0, WelcomeReelMotion.distanceDp(1500L), 0.000001)
        assertEquals(307.2, WelcomeReelMotion.distanceDp(2400L), 0.000001)
        assertEquals(323.2, WelcomeReelMotion.distanceDp(3400L), 0.000001)
        assertEquals(16.0, WelcomeReelMotion.distanceDp(7100L) - WelcomeReelMotion.distanceDp(6100L), 0.000001)
    }

    @Test
    fun `brisk movement remains at 240dp per second throughout the first 600ms`() {
        for (millis in listOf(0L, 100L, 300L, 599L)) {
            val distance = WelcomeReelMotion.distanceDp(millis)
            val speed = (WelcomeReelMotion.distanceDp(millis + 1L) - distance) * 1000.0
            assertEquals(millis * 0.24, distance, 0.000001)
            assertEquals(240.0, speed, 0.000001)
        }
        assertEquals(144.0, WelcomeReelMotion.distanceDp(600L), 0.000001)
    }

    @Test
    fun `distance and velocity stay continuous when the initial hold starts slowing`() {
        val handoff = 600L
        val atHandoff = WelcomeReelMotion.distanceDp(handoff)
        val beforeHandoff = WelcomeReelMotion.distanceDp(handoff - 1L)
        val afterHandoff = WelcomeReelMotion.distanceDp(handoff + 1L)
        // Three-point derivatives approach the join without averaging the immediate slowdown.
        val incomingSpeed =
            (3.0 * atHandoff - 4.0 * beforeHandoff + WelcomeReelMotion.distanceDp(handoff - 2L)) * 500.0
        val outgoingSpeed =
            (-3.0 * atHandoff + 4.0 * afterHandoff - WelcomeReelMotion.distanceDp(handoff + 2L)) * 500.0

        assertEquals(144.0, atHandoff, 0.000001)
        assertEquals(240.0, incomingSpeed, 0.001)
        assertEquals(240.0, outgoingSpeed, 0.001)
        assertEquals(incomingSpeed, outgoingSpeed, 0.001)
        assertEquals(0.24, atHandoff - beforeHandoff, 0.000001)
        assertEquals(0.24, afterHandoff - atHandoff, 0.00013)
    }

    @Test
    fun `distance and velocity stay continuous at the intro handoff`() {
        val handoff = WelcomeReelMotion.IntroDurationMillis
        val atHandoff = WelcomeReelMotion.distanceDp(handoff)
        val beforeHandoff = WelcomeReelMotion.distanceDp(handoff - 1L)
        val afterHandoff = WelcomeReelMotion.distanceDp(handoff + 1L)
        val incomingSpeed = (atHandoff - beforeHandoff) * 1000.0
        val outgoingSpeed = (afterHandoff - atHandoff) * 1000.0

        assertEquals(16.0, incomingSpeed, 0.001)
        assertEquals(16.0, outgoingSpeed, 0.001)
        assertEquals(incomingSpeed, outgoingSpeed, 0.001)
        assertTrue(atHandoff > beforeHandoff && afterHandoff > atHandoff)
        assertEquals(0.016, afterHandoff - atHandoff, 0.000001)
    }

    @Test
    fun `after the initial hold speed slows monotonically toward the steady speed`() {
        val speeds =
            listOf(600L, 1050L, 1500L, 1950L, 2400L).map { millis ->
                (WelcomeReelMotion.distanceDp(millis + 1L) - WelcomeReelMotion.distanceDp(millis)) * 1000.0
            }

        assertEquals(240.0, speeds.first(), 0.13)
        assertEquals(16.0, speeds.last(), 0.001)
        assertTrue(speeds.zipWithNext().all { (earlier, later) -> earlier > later })
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
