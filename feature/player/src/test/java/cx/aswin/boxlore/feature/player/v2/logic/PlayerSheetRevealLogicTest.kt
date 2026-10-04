package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSheetRevealLogicTest {
    @Test
    fun endpointsShowOnlyTheMatchingPlayer() {
        val collapsed = calculatePlayerSheetReveal(0f)
        val expanded = calculatePlayerSheetReveal(1f)

        assertEquals(1f, collapsed.miniAlpha)
        assertEquals(0f, collapsed.fullAlpha)
        assertEquals(0.975f, collapsed.fullScale)
        assertEquals(1f, collapsed.entranceRemaining)
        assertEquals(0f, expanded.miniAlpha)
        assertEquals(1f, expanded.fullAlpha)
        assertEquals(1f, expanded.fullScale)
        assertEquals(0f, expanded.entranceRemaining)
    }

    @Test
    fun revealStartsAfterCookieClippingAndFinishesBeforeTheSheetSettles() {
        assertEquals(calculatePlayerSheetReveal(0f), calculatePlayerSheetReveal(0.06f))
        assertEquals(calculatePlayerSheetReveal(1f), calculatePlayerSheetReveal(0.44f))
        assertEquals(0.5f, calculatePlayerSheetReveal(0.25f).fullAlpha, 0.0001f)
    }

    @Test
    fun handoffHasNoEmptyFrameAndMovesContinuouslyInBothDirections() {
        val forward = (0..1000).map { calculatePlayerSheetReveal(it / 1000f) }
        val backward = (1000 downTo 0).map { calculatePlayerSheetReveal(it / 1000f) }

        assertEquals(forward.reversed(), backward)
        forward.forEach { reveal ->
            assertEquals(1f, reveal.miniAlpha + reveal.fullAlpha, 0.0001f)
            assertTrue(reveal.miniAlpha in 0f..1f)
            assertTrue(reveal.fullAlpha in 0f..1f)
            assertTrue(reveal.fullScale in 0.975f..1f)
        }
        forward.zipWithNext().forEach { (before, after) ->
            assertTrue(after.fullAlpha >= before.fullAlpha)
            assertTrue(after.fullAlpha - before.fullAlpha < 0.004f)
            assertTrue(after.fullScale >= before.fullScale)
            assertTrue(after.entranceRemaining <= before.entranceRemaining)
        }
    }

    @Test
    fun revealEasesIntoAndOutOfItsHandoffRatherThanJumping() {
        val startStep = calculatePlayerSheetReveal(0.061f).fullAlpha
        val middleStep = calculatePlayerSheetReveal(0.251f).fullAlpha - calculatePlayerSheetReveal(0.25f).fullAlpha
        val endStep = 1f - calculatePlayerSheetReveal(0.439f).fullAlpha

        assertTrue(startStep > 0f && startStep < middleStep / 100f)
        assertTrue(endStep > 0f && endStep < middleStep / 100f)
    }

    @Test
    fun invalidInputAndSpringOvershootUseSafeEndpoints() {
        val collapsed = calculatePlayerSheetReveal(0f)
        assertEquals(collapsed, calculatePlayerSheetReveal(-1f))
        assertEquals(collapsed, calculatePlayerSheetReveal(Float.NaN))
        assertEquals(collapsed, calculatePlayerSheetReveal(Float.POSITIVE_INFINITY))
        assertEquals(collapsed, calculatePlayerSheetReveal(Float.NEGATIVE_INFINITY))
        assertEquals(calculatePlayerSheetReveal(1f), calculatePlayerSheetReveal(2f))
    }
}
