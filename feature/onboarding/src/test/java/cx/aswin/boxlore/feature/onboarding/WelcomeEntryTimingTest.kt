package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WelcomeEntryTimingTest {
    @Test
    fun `copy arrives in order while the reel gathers speed`() {
        assertTrue(WelcomeEntryTiming.FirstLineDelayMillis < WelcomeEntryTiming.SecondLineDelayMillis)
        assertTrue(WelcomeEntryTiming.SecondLineDelayMillis < WelcomeEntryTiming.ReplyDelayMillis)
        // The headline arrives while the covers are still gliding in.
        assertTrue(WelcomeEntryTiming.FirstLineDelayMillis < WelcomeReelMotion.EntranceMillis)
    }

    @Test
    fun `echo rises after the headline has landed`() {
        val headlineEnd = WelcomeEntryTiming.SecondLineDelayMillis + WelcomeEntryTiming.LineDurationMillis
        assertTrue(WelcomeEntryTiming.EchoRiseDelayMillis >= headlineEnd)
    }

    @Test
    fun `line progress eases from hidden to fully shown`() {
        assertEquals(0f, WelcomeEntryTiming.lineProgress(0L, 220L), 0.0001f)
        assertEquals(1f, WelcomeEntryTiming.lineProgress(220L + WelcomeEntryTiming.LineDurationMillis, 220L), 0.0001f)
        // Decelerating: most of the travel happens early, then it settles.
        assertTrue(WelcomeEntryTiming.lineProgress(220L + WelcomeEntryTiming.LineDurationMillis / 4, 220L) > 0.5f)
    }
}
