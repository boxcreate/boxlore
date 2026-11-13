package cx.aswin.boxlore.core.designsystem.components

import cx.aswin.boxlore.core.model.SleepTimerConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepPromptMotionTest {
    @Test
    fun `a prompt that also offers the watch back waits five seconds longer, and only then`() {
        assertEquals(8_000L, SleepPromptMotion.autoHideMillis(8_000L, offersWatch = false))
        assertEquals(13_000L, SleepPromptMotion.autoHideMillis(8_000L, offersWatch = true))
    }

    @Test
    fun `the prompt drops in, the moon turns into place and the choices pop in after it`() {
        val start = SleepPromptMotion.card(0f)
        assertEquals(0f, start.alpha)
        assertEquals(-28f, start.offsetDp, 0.01f)
        assertEquals(0f, SleepPromptMotion.moon(0f).alpha)
        assertEquals(0f, SleepPromptMotion.buttonPop(0f, 0).alpha)
        assertEquals(0f, SleepPromptMotion.endPop(0f).alpha)
        // The times pop in one after another.
        assertTrue(SleepPromptMotion.buttonPop(400f, 0).alpha > SleepPromptMotion.buttonPop(400f, 3).alpha)
        val end = SleepPromptMotion.ENTER_MILLIS
        assertEquals(SleepPromptMotion.Pose(), SleepPromptMotion.card(end))
        assertEquals(SleepPromptMotion.Pose(), SleepPromptMotion.moon(end))
        for (i in 0..3) assertEquals(SleepPromptMotion.Pose(), SleepPromptMotion.buttonPop(end, i))
        assertEquals(SleepPromptMotion.Pose(), SleepPromptMotion.endPop(end))
    }

    @Test
    fun `after a pick the moon sets, the badge rises in its place and the answer replaces the question`() {
        // At the pick: the question and choices are there, the moon in place, the badge below the band.
        assertEquals(1f, SleepPromptMotion.questionAlpha(0f))
        assertEquals(1f, SleepPromptMotion.choicesHeight(0f))
        assertEquals(0f, SleepPromptMotion.moonSet(0f))
        assertEquals(110f, SleepPromptMotion.badge(0f).offsetDp, 0.01f)
        assertEquals(0f, SleepPromptMotion.answer(0f).alpha)
        // The badge starts rising before the moon has gone, so the band is never empty.
        assertTrue(SleepPromptMotion.badge(1_100f).offsetDp < 110f)
        assertTrue(SleepPromptMotion.moonSet(1_100f) < 110f)
        val done = SleepPromptMotion.CONFIRM_MILLIS
        assertEquals(0f, SleepPromptMotion.questionAlpha(done))
        assertEquals(0f, SleepPromptMotion.choicesHeight(done), 0.001f)
        assertEquals(0f, SleepPromptMotion.choicesAlpha(done))
        assertEquals(110f, SleepPromptMotion.moonSet(done), 0.01f)
        assertEquals(SleepPromptMotion.Pose(), SleepPromptMotion.badge(done))
        assertEquals(SleepPromptMotion.Pose(), SleepPromptMotion.answer(done))
        // The confirmation is fully shown well before the prompt leaves.
        assertTrue(SleepPromptMotion.CONFIRM_HOLD_MILLIS >= done + 1_000)
    }

    @Test
    fun `the stars take turns to twinkle and stay within their range`() {
        for (step in 0..20) {
            for (i in SleepPromptMotion.Stars.indices) {
                val glow = SleepPromptMotion.twinkle(step / 20f, i)
                assertTrue(glow.alpha in 0.35f..1f)
                assertTrue(glow.scale in 0.75f..1f)
            }
        }
        // At 0.1 the three stars sit at clearly different points of the cycle.
        val phase = 0.1f
        val brightness = SleepPromptMotion.Stars.indices.map { SleepPromptMotion.twinkle(phase, it).alpha }
        assertTrue(brightness.distinct().size == brightness.size)
    }

    @Test
    fun `the badge shows whole hours as hours, other times as minutes, and the end of the episode as a mark`() {
        assertEquals(SleepTimerBadge.Time(30, hours = false), sleepTimerBadge(30))
        assertEquals(SleepTimerBadge.Time(45, hours = false), sleepTimerBadge(45))
        assertEquals(SleepTimerBadge.Time(1, hours = true), sleepTimerBadge(60))
        assertEquals(SleepTimerBadge.Time(2, hours = true), sleepTimerBadge(120))
        assertEquals(SleepTimerBadge.Time(90, hours = false), sleepTimerBadge(90))
        assertEquals(SleepTimerBadge.EndOfEpisode, sleepTimerBadge(SleepTimerConstants.END_OF_EPISODE_MINUTES))
    }
}
