package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WelcomeWordLoopMotionTest {
    @Test
    fun `reply waits for the invitation and ignores negative clock values`() {
        for (elapsed in listOf(Long.MIN_VALUE, -1L, 0L, 899L)) {
            val cue = WelcomeWordLoopMotion.cue(elapsed)
            val frame = WelcomeWordLoopMotion.frame(cue)
            assertEquals(0, cue.index)
            assertEquals(0L, cue.localMillis)
            assertFalse(cue.visible)
            assertEquals(0f, frame.wordAlpha)
            assertEquals(0f, frame.iconAlpha)
        }
        assertTrue(WelcomeWordLoopMotion.cue(900L).visible)
    }

    @Test
    fun `all seven terms appear in order before looping back to the first`() {
        val starts = listOf(0L, 3300L, 6600L, 9900L, 13200L, 16500L, 19800L)
        starts.forEachIndexed { index, offset ->
            val cue = WelcomeWordLoopMotion.cue(900L + offset)
            assertEquals(index, cue.index)
            assertEquals(0L, cue.localMillis)
            assertTrue(cue.visible)
        }

        assertEquals(7, WelcomeWordLoopMotion.TermCount)
        assertEquals(6, WelcomeWordLoopMotion.LoreIndex)
        assertEquals(WelcomeWordLoopMotion.cue(900L), WelcomeWordLoopMotion.cue(25000L))
    }

    @Test
    fun `lore holds a full second longer than each other term`() {
        for (index in 0 until 6) {
            val lastFrame = WelcomeWordLoopMotion.cue(900L + index * 3300L + 3299L)
            assertEquals(index, lastFrame.index)
            assertEquals(3299L, lastFrame.localMillis)
            assertEquals(index + 1, WelcomeWordLoopMotion.cue(900L + (index + 1) * 3300L).index)
        }

        assertEquals(6, WelcomeWordLoopMotion.cue(900L + 19800L + 3299L).index)
        assertEquals(6, WelcomeWordLoopMotion.cue(24999L).index)
        assertEquals(4299L, WelcomeWordLoopMotion.cue(24999L).localMillis)
        assertEquals(0, WelcomeWordLoopMotion.cue(25000L).index)
    }

    @Test
    fun `cycle position remains exact even at the largest elapsed time`() {
        val elapsed = Long.MAX_VALUE
        val expectedCyclePosition = (elapsed - 900L) % 24100L
        val cue = WelcomeWordLoopMotion.cue(elapsed)
        assertEquals(expectedCyclePosition, cue.index * 3300L + cue.localMillis)
        assertTrue(cue.index in 0..6)
        assertTrue(cue.localMillis in 0L until if (cue.index == 6) 4300L else 3300L)

        val earlierCycle = WelcomeWordLoopMotion.cue(elapsed - 24100L)
        assertEquals(cue, earlierCycle)
        assertFinite(WelcomeWordLoopMotion.frame(cue))
    }

    @Test
    fun `disabled animations display settled lore immediately at any clock value`() {
        for (elapsed in listOf(Long.MIN_VALUE, 0L, 900L, 13200L, Long.MAX_VALUE)) {
            val cue = WelcomeWordLoopMotion.cue(elapsed, animationsDisabled = true)
            assertEquals(6, cue.index)
            assertEquals(520L, cue.localMillis)
            assertTrue(cue.visible)
            assertEquals(settledFrame(), WelcomeWordLoopMotion.frame(cue))
        }
    }

    @Test
    fun `icon leads the text and the entrance finishes with both settled`() {
        val initial = frameAt(0L)
        assertEquals(0f, initial.iconAlpha)
        assertEquals(0.55f, initial.iconScale, 0.000001f)
        assertEquals(-22f, initial.iconRotationDegrees)
        assertEquals(0f, initial.wordAlpha)
        assertEquals(0.45f, initial.wordTranslationFraction)

        val iconLead = frameAt(70L)
        assertTrue(iconLead.iconAlpha > 0f)
        assertEquals(0f, iconLead.wordAlpha)
        val textEntry = frameAt(250L)
        assertEquals(0.5f, textEntry.wordAlpha, 0.000001f)
        assertEquals(0.225f, textEntry.wordTranslationFraction, 0.000001f)
        assertEquals(1f, textEntry.iconAlpha)
        assertEquals(1f, frameAt(430L).wordAlpha)
        assertEquals(settledFrame(), frameAt(520L))
    }

    @Test
    fun `the icon has a modest overshoot while word opacity and translation never overshoot`() {
        val enteringFrames = (0L..520L).map(::frameAt)
        assertTrue(enteringFrames.any { it.iconScale > 1.02f })
        assertTrue(enteringFrames.all { it.iconScale in 0.55f..1.04f })
        assertTrue(enteringFrames.all { it.iconRotationDegrees in -22f..2f })
        assertTrue(enteringFrames.all { it.wordAlpha in 0f..1f })
        assertTrue(enteringFrames.all { it.wordTranslationFraction in 0f..0.45f })
        assertTrue(enteringFrames.map { it.wordAlpha }.zipWithNext().all { (before, after) -> after >= before })
    }

    @Test
    fun `each word gets a readable unmoving hold before exiting`() {
        for (index in 0..6) {
            val exitStart = if (index == 6) 4120L else 3120L
            for (local in listOf(520L, 1800L, exitStart)) {
                assertEquals(settledFrame(), frameAt(local, index))
            }
        }
    }

    @Test
    fun `exit fades both elements together with a small upward translation`() {
        for (index in 0..6) {
            val duration = if (index == 6) 4300L else 3300L
            val halfExit = frameAt(duration - 90L, index)
            assertEquals(0.5f, halfExit.wordAlpha, 0.000001f)
            assertEquals(halfExit.wordAlpha, halfExit.iconAlpha)
            assertEquals(-0.125f, halfExit.wordTranslationFraction, 0.000001f)
            assertEquals(0.94f, halfExit.iconScale, 0.000001f)
            assertEquals(4f, halfExit.iconRotationDegrees)

            val exited = frameAt(duration, index)
            assertEquals(0f, exited.wordAlpha)
            assertEquals(0f, exited.iconAlpha)
            assertEquals(-0.25f, exited.wordTranslationFraction)
            assertEquals(0.88f, exited.iconScale, 0.000001f)
        }
    }

    @Test
    fun `term and full cycle handoffs never show two opaque words`() {
        for (boundary in listOf(4200L, 7500L, 10800L, 14100L, 17400L, 20700L, 25000L)) {
            val outgoing = WelcomeWordLoopMotion.frame(WelcomeWordLoopMotion.cue(boundary - 1L))
            val incoming = WelcomeWordLoopMotion.frame(WelcomeWordLoopMotion.cue(boundary))
            assertTrue(outgoing.wordAlpha < 0.0001f)
            assertTrue(outgoing.iconAlpha < 0.0001f)
            assertEquals(0f, incoming.wordAlpha)
            assertEquals(0f, incoming.iconAlpha)
        }
    }

    @Test
    fun `entrance and exit join the hold without visible jumps`() {
        val beforeHold = frameAt(519L)
        val hold = frameAt(520L)
        assertEquals(beforeHold.wordAlpha, hold.wordAlpha)
        assertEquals(beforeHold.iconScale, hold.iconScale, 0.00001f)
        assertEquals(beforeHold.iconRotationDegrees, hold.iconRotationDegrees, 0.0002f)

        val startExit = frameAt(3120L)
        val afterExitStart = frameAt(3121L)
        assertEquals(startExit.wordAlpha, afterExitStart.wordAlpha, 0.0001f)
        assertEquals(startExit.wordTranslationFraction, afterExitStart.wordTranslationFraction, 0.00003f)
    }

    @Test
    fun `manually constructed cues remain bounded and finite`() {
        for (index in listOf(-1, 0, 6, Int.MAX_VALUE)) {
            for (local in listOf(Long.MIN_VALUE, -1L, 0L, 520L, 3300L, Long.MAX_VALUE)) {
                val frame = frameAt(local, index)
                assertFinite(frame)
                assertTrue(frame.wordAlpha in 0f..1f)
                assertTrue(frame.iconAlpha in 0f..1f)
                assertTrue(frame.wordTranslationFraction in -0.25f..0.45f)
                assertTrue(frame.iconScale in 0.55f..1.04f)
            }
        }
    }

    private fun frameAt(
        localMillis: Long,
        index: Int = 0,
    ): WelcomeWordLoopMotion.Frame = WelcomeWordLoopMotion.frame(WelcomeWordLoopMotion.Cue(index, localMillis, visible = true))

    private fun settledFrame(): WelcomeWordLoopMotion.Frame = WelcomeWordLoopMotion.Frame(1f, 0f, 1f, 1f, 0f)

    private fun assertFinite(frame: WelcomeWordLoopMotion.Frame) {
        assertTrue(frame.wordAlpha.isFinite())
        assertTrue(frame.wordTranslationFraction.isFinite())
        assertTrue(frame.iconAlpha.isFinite())
        assertTrue(frame.iconScale.isFinite())
        assertTrue(frame.iconRotationDegrees.isFinite())
    }
}
