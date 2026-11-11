package cx.aswin.boxlore.feature.widgets.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetProgressTest {
    private val minute = 60_000L

    @Test
    fun fractionIsZeroUntilTheLengthIsKnownAndNeverLeavesItsRange() {
        assertEquals(0f, WidgetProgress.fraction(positionMs = 5 * minute, durationMs = 0L), 0f)
        assertEquals(0.25f, WidgetProgress.fraction(15 * minute, 60 * minute), 0.0001f)
        assertEquals(1f, WidgetProgress.fraction(90 * minute, 60 * minute), 0f)
        assertEquals(0f, WidgetProgress.fraction(-1L, 60 * minute), 0f)
    }

    @Test
    fun minutesLeftRoundsUpSoItOnlyReachesZeroAtTheEnd() {
        assertNull(WidgetProgress.minutesLeft(0L, 0L))
        assertEquals(60, WidgetProgress.minutesLeft(0L, 60 * minute))
        assertEquals(36, WidgetProgress.minutesLeft(24 * minute, 60 * minute))
        assertEquals(1, WidgetProgress.minutesLeft(60 * minute - 1_000L, 60 * minute))
        assertEquals(0, WidgetProgress.minutesLeft(60 * minute, 60 * minute))
    }

    @Test
    fun timeLeftSplitsIntoHoursAndMinutesAndDisappearsWhenNothingIsLeft() {
        assertEquals(WidgetProgress.TimeLeft(hours = 0, minutes = 36), WidgetProgress.timeLeft(24 * minute, 60 * minute))
        assertEquals(WidgetProgress.TimeLeft(hours = 1, minutes = 12), WidgetProgress.timeLeft(14 * minute, 86 * minute))
        assertEquals(WidgetProgress.TimeLeft(hours = 2, minutes = 0), WidgetProgress.timeLeft(0L, 120 * minute))
        assertNull(WidgetProgress.timeLeft(60 * minute, 60 * minute))
        assertNull(WidgetProgress.timeLeft(10 * minute, 0L))
    }

    @Test
    fun ringStepsAreCoarseEnoughToIgnoreSecondBySecondTicks() {
        val duration = 60 * minute
        val step = WidgetProgress.ringStep(10 * minute, duration)
        assertEquals(step, WidgetProgress.ringStep(10 * minute + 500L, duration))
        assertEquals(step + 1, WidgetProgress.ringStep(10 * minute + 30_000L, duration))
        assertEquals(WidgetProgress.RING_STEPS, WidgetProgress.ringStep(duration, duration))
    }
}
