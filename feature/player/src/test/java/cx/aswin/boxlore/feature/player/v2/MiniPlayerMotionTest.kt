package cx.aswin.boxlore.feature.player.v2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerMotionTest {
    @Test
    fun motionRunsOnlyDuringActivePlaybackInTheFullyCompactPlayer() {
        assertTrue(canAnimateCompactPlayer(true, false, 1f, 0f))
        assertFalse(canAnimateCompactPlayer(false, false, 1f, 0f))
        assertFalse(canAnimateCompactPlayer(true, true, 1f, 0f))
        assertFalse(canAnimateCompactPlayer(true, false, 0f, 0f))
        assertFalse(canAnimateCompactPlayer(true, false, 0.8f, 0f))
        assertFalse(canAnimateCompactPlayer(true, false, 1f, 0.1f))
        assertFalse(canAnimateCompactPlayer(true, false, Float.NaN, 0f))
    }

    @Test
    fun scallopMovementCompletesOneLobeCycleEveryTwoPointFourSeconds() {
        assertEquals(CompactPlayerRotationPeriod / 2f, advanceCompactPlayerRotation(0f, 1_200_000_000L), 0.0001f)
        assertEquals(0f, advanceCompactPlayerRotation(0f, 2_400_000_000L), 0.0001f)
        var phase = 0f
        repeat(72) { phase = advanceCompactPlayerRotation(phase, 33_333_333L) }
        assertTrue(phase < 0.0001f || CompactPlayerRotationPeriod - phase < 0.0001f)
    }

    @Test
    fun pausePreservesPhaseAndResumeContinuesWithoutResetting() {
        val paused = advanceCompactPlayerRotation(0f, 600_000_000L)
        assertEquals(paused, advanceCompactPlayerRotation(paused, 0L))
        assertEquals(paused, advanceCompactPlayerRotation(paused, -1L))
        val resumed = advanceCompactPlayerRotation(paused, 600_000_000L)
        assertEquals(CompactPlayerRotationPeriod / 2f, resumed, 0.0001f)
    }
}
