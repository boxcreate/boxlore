package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSheetRevealLogicTest {
    @Test
    fun endpointsShowOnlyTheMatchingPlayer() {
        for (compact in listOf(0f, 1f)) {
            val collapsed = calculatePlayerSheetReveal(0f, compact)
            val expanded = calculatePlayerSheetReveal(1f, compact)
            assertEquals(1f, collapsed.miniAlpha)
            assertEquals(0f, collapsed.fullAlpha)
            assertEquals(0.95f, collapsed.fullScale)
            assertEquals(1f, collapsed.entranceRemaining)
            assertEquals(0f, expanded.miniAlpha)
            assertEquals(1f, expanded.fullAlpha)
            assertEquals(1f, expanded.fullScale)
            assertEquals(0f, expanded.entranceRemaining)
        }
    }

    @Test
    fun miniContentLeavesBeforeTheFullPlayerArrivesSoTheyNeverDoubleExpose() {
        for (compact in listOf(0f, 0.5f, 1f)) {
            assertEquals(0f, calculatePlayerSheetReveal(MiniContentExitEnd, compact).miniAlpha)
            for (step in 0..1000) {
                val reveal = calculatePlayerSheetReveal(step / 1000f, compact)
                assertTrue(minOf(reveal.miniAlpha, reveal.fullAlpha) < 0.05f, "both visible at ${step / 1000f}")
            }
        }
    }

    @Test
    fun fromTheCompactCookieTheFullPlayerWaitsForALargerContainer() {
        assertEquals(0f, calculatePlayerSheetReveal(0.16f, 0f).fullAlpha)
        assertEquals(1f, calculatePlayerSheetReveal(0.56f, 0f).fullAlpha)
        assertEquals(0f, calculatePlayerSheetReveal(0.32f, 1f).fullAlpha)
        assertEquals(1f, calculatePlayerSheetReveal(0.78f, 1f).fullAlpha)
        assertTrue(calculatePlayerSheetReveal(0.4f, 1f).fullAlpha < calculatePlayerSheetReveal(0.4f, 0f).fullAlpha)
    }

    @Test
    fun motionIsContinuousMonotonicAndReversible() {
        for (compact in listOf(0f, 1f)) {
            val forward = (0..1000).map { calculatePlayerSheetReveal(it / 1000f, compact) }
            val backward = (1000 downTo 0).map { calculatePlayerSheetReveal(it / 1000f, compact) }
            assertEquals(forward.reversed(), backward)
            forward.zipWithNext().forEach { (before, after) ->
                assertTrue(after.fullAlpha >= before.fullAlpha && after.fullAlpha - before.fullAlpha < 0.006f)
                assertTrue(after.miniAlpha <= before.miniAlpha && before.miniAlpha - after.miniAlpha < 0.009f)
                assertTrue(after.fullScale >= before.fullScale)
                assertTrue(after.entranceRemaining <= before.entranceRemaining)
            }
        }
    }

    @Test
    fun cornersHoldThroughTheFirstHalfThenSquareOffAndWidthLeadsHeight() {
        assertEquals(1f, playerCornerHold(0f))
        assertEquals(1f, playerCornerHold(0.5f))
        assertEquals(0.5f, playerCornerHold(0.75f), 0.0001f)
        assertEquals(0f, playerCornerHold(1f))
        for (step in 1 until 100) {
            val expansion = step / 100f
            assertTrue(playerSheetWiden(expansion) > expansion)
        }
        assertEquals(0f, playerSheetWiden(0f))
        assertEquals(1f, playerSheetWiden(1f))
    }

    @Test
    fun invalidInputAndSpringOvershootUseSafeEndpoints() {
        val collapsed = calculatePlayerSheetReveal(0f)
        assertEquals(collapsed, calculatePlayerSheetReveal(-1f))
        assertEquals(collapsed, calculatePlayerSheetReveal(Float.NaN))
        assertEquals(collapsed, calculatePlayerSheetReveal(Float.POSITIVE_INFINITY))
        assertEquals(collapsed, calculatePlayerSheetReveal(Float.NEGATIVE_INFINITY))
        assertEquals(calculatePlayerSheetReveal(1f), calculatePlayerSheetReveal(2f))
        assertEquals(calculatePlayerSheetReveal(0.3f, 0f), calculatePlayerSheetReveal(0.3f, Float.NaN))
        assertEquals(1f, playerCornerHold(Float.NaN))
        assertEquals(0f, playerSheetWiden(Float.NaN))
    }
}
