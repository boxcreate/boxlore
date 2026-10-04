package cx.aswin.boxlore.feature.player.v2.logic

import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerProgressLogicTest {
    @Test
    fun movingWaveKeepsBothProgressEndpointsFixed() {
        listOf(0f, 1f, PI.toFloat(), (2 * PI).toFloat()).forEach { phase ->
            assertEquals(0f, miniPlayerWaveOffset(0f, 100f, 1.25f, 24f, phase), 0f)
            assertEquals(0f, miniPlayerWaveOffset(100f, 100f, 1.25f, 24f, phase), 0f)
        }
    }

    @Test
    fun waveFitsTheTrackEvenForVeryShortProgress() {
        listOf(0.1f, 1f, 4f, 100f).forEach { length ->
            for (step in 0..100) {
                val offset = miniPlayerWaveOffset(length * step / 100f, length, 1.25f, 24f, 0.7f)
                assertTrue(offset.isFinite())
                assertTrue(abs(offset) <= 1.25f)
            }
        }
    }

    @Test
    fun phaseMovesTheInteriorAndZeroAmplitudeFlattensIt() {
        val initial = miniPlayerWaveOffset(6f, 100f, 1.25f, 24f, 0f)
        val moved = miniPlayerWaveOffset(6f, 100f, 1.25f, 24f, (PI / 2).toFloat())
        assertEquals(1.25f, initial, 0.001f)
        assertEquals(0f, moved, 0.001f)
        assertEquals(0f, miniPlayerWaveOffset(6f, 100f, 0f, 24f, 1f), 0f)
    }

    @Test
    fun invalidGeometryCannotProduceInvalidDrawingCoordinates() {
        assertEquals(0f, miniPlayerWaveOffset(6f, 0f, 1f, 24f, 0f), 0f)
        assertEquals(0f, miniPlayerWaveOffset(6f, 100f, 1f, 0f, 0f), 0f)
        assertEquals(0f, miniPlayerWaveOffset(-1f, 100f, 1f, 24f, 0f), 0f)
        assertEquals(0f, miniPlayerWaveOffset(101f, 100f, 1f, 24f, 0f), 0f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(0f, miniPlayerWaveOffset(invalid, 100f, 1f, 24f, 0f), 0f)
            assertEquals(0f, miniPlayerWaveOffset(6f, invalid, 1f, 24f, 0f), 0f)
            assertEquals(0f, miniPlayerWaveOffset(6f, 100f, invalid, 24f, 0f), 0f)
            assertEquals(0f, miniPlayerWaveOffset(6f, 100f, 1f, invalid, 0f), 0f)
            assertEquals(0f, miniPlayerWaveOffset(6f, 100f, 1f, 24f, invalid), 0f)
        }
    }
}
