package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoWaitTimingTest {
    @Test
    fun `nothing shows for the first four seconds, then the countdown starts at its full length`() {
        assertNull(EchoWaitTiming.status(0, 15))
        assertNull(EchoWaitTiming.status(3_999, 15))
        assertEquals(EchoWaitStatus(15, 0, overdue = false), EchoWaitTiming.status(4_000, 15))
        assertEquals(EchoWaitStatus(30, 0, overdue = false), EchoWaitTiming.status(4_000, 30))
    }

    @Test
    fun `the countdown ticks each second and stops at zero as overdue`() {
        assertEquals(10, EchoWaitTiming.status(9_000, 15)?.secondsLeft)
        assertEquals(1, EchoWaitTiming.status(18_999, 15)?.secondsLeft)
        val done = requireNotNull(EchoWaitTiming.status(19_000, 15))
        assertEquals(0, done.secondsLeft)
        assertTrue(done.overdue)
        assertEquals(0, EchoWaitTiming.status(120_000, 15)?.secondsLeft)
        assertFalse(requireNotNull(EchoWaitTiming.status(33_999, 30)).overdue)
        assertTrue(requireNotNull(EchoWaitTiming.status(34_000, 30)).overdue)
    }

    @Test
    fun `lines rotate every five seconds and wrap around their set`() {
        assertEquals(0, EchoWaitTiming.status(8_999, 15)?.rotation)
        assertEquals(1, EchoWaitTiming.status(9_000, 15)?.rotation)
        assertEquals(2, EchoWaitTiming.lineIndex(seed = 5, rotation = 0, count = 3))
        assertEquals(0, EchoWaitTiming.lineIndex(seed = 5, rotation = 1, count = 3))
        assertEquals(0, EchoWaitTiming.lineIndex(seed = 5, rotation = 1, count = 0))
    }

    @Test
    fun `each step has its own lines and countdown, and a build keeps one countdown across stages`() {
        assertEquals(15, EchoWaitStep.NextQuestion.countdownSeconds)
        assertFalse(EchoWaitStep.NextQuestion.building)
        assertEquals(EchoWaitStep.Gathering, EchoWaitStep.forBuild(AiLoadingStage.SYNTHESIZING_PREFERENCES))
        assertEquals(EchoWaitStep.Finding, EchoWaitStep.forBuild(AiLoadingStage.FETCHING_CATALOGS))
        assertEquals(EchoWaitStep.Sorting, EchoWaitStep.forBuild(AiLoadingStage.ASSEMBLING_FEED))
        assertTrue(EchoWaitStep.entries.filter { it.building }.all { it.countdownSeconds == 30 })
        assertEquals(EchoWaitStep.entries.size, EchoWaitStep.entries.map { it.lines }.distinct().size)
    }
}
