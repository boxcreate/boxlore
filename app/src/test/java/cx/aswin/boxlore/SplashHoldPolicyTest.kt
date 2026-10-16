package cx.aswin.boxlore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashHoldPolicyTest {
    @Test
    fun coldLaunchKeepsTheSplashBriefly() {
        assertTrue(SplashHoldPolicy.keepOnScreen(createdAtMillis = 1_000L, nowMillis = 1_000L, coldStart = true))
        assertTrue(SplashHoldPolicy.keepOnScreen(1_000L, 1_000L + SplashHoldPolicy.MIN_VISIBLE_MILLIS - 1L, coldStart = true))
        assertFalse(SplashHoldPolicy.keepOnScreen(1_000L, 1_000L + SplashHoldPolicy.MIN_VISIBLE_MILLIS, coldStart = true))
    }

    @Test
    fun recreationNeverHoldsTheSplash() {
        assertFalse(SplashHoldPolicy.keepOnScreen(1_000L, 1_000L, coldStart = false))
    }

    @Test
    fun holdAndFadeStayUnderOneSecond() {
        assertTrue(SplashHoldPolicy.MIN_VISIBLE_MILLIS + SplashHoldPolicy.EXIT_FADE_MILLIS < 1_000L)
    }
}
