package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationIndicatorMotionTest {
    @Test
    fun `leaving Lore targets Library directly without an intermediate Home position`() {
        val lore = navigationIndicatorSelectedIndex("learn?entryPoint=bottom_nav")
        val library = navigationIndicatorSelectedIndex("library")
        assertEquals(3, lore)
        assertEquals(2, library)
        assertEquals(3, navigationIndicatorTargetIndex(lore, previousIndex = 0))
        assertEquals(2, navigationIndicatorTargetIndex(library, previousIndex = lore))
        val animation = slide(lore.toFloat(), library.toFloat())
        for (millis in 0L..600L step 16) {
            assertTrue(animation.getValueFromNanos(millis * 1_000_000) in 2f..3f)
        }
    }

    @Test
    fun `unselected routes retain the last indicator position for every destination`() {
        for ((index, route) in listOf("home", "explore", "library", "learn").withIndex()) {
            val selected = navigationIndicatorSelectedIndex(route)
            assertEquals(index, selected)
            val hidden = navigationIndicatorSelectedIndex("podcast/42")
            assertEquals(-1, hidden)
            assertEquals(index, navigationIndicatorTargetIndex(hidden, previousIndex = selected))
        }
        assertEquals(-1, navigationIndicatorSelectedIndex("learn_history"))
    }

    @Test
    fun `interrupted slide keeps position and moves directly toward the latest tab`() {
        val first = slide(3f, 0f)
        val elapsed = 96_000_000L
        val position = first.getValueFromNanos(elapsed)
        val velocity = first.getVelocityVectorFromNanos(elapsed).value
        val reversed = slide(position, 2f, velocity)
        assertEquals(position, reversed.getValueFromNanos(0L))
        assertTrue(reversed.getValueFromNanos(16_000_000L) > position)
        for (millis in 0L..650L step 16) {
            assertTrue(reversed.getValueFromNanos(millis * 1_000_000) <= 2f)
        }
        assertEquals(2f, reversed.getValueFromNanos(reversed.durationNanos))
    }

    @Test
    fun `aurora stays absent on primary tabs and blends into the same Lore indicator`() {
        for (index in 0..200) {
            assertEquals(0f, floatingNavigationLoreAuroraBlend(index / 100f))
        }
        assertEquals(0.5f, floatingNavigationLoreAuroraBlend(2.5f))
        assertEquals(1f, floatingNavigationLoreAuroraBlend(3f))
        var previous = 0f
        for (step in 0..100) {
            val blend = floatingNavigationLoreAuroraBlend(2f + step / 100f)
            assertTrue(blend in previous..1f)
            previous = blend
        }
        assertTrue(floatingNavigationLoreAuroraBlend(2.001f) < 0.00001f)
        assertTrue(1f - floatingNavigationLoreAuroraBlend(2.999f) < 0.00001f)
    }

    @Test
    fun `aurora follows current position when a Lore transition is reversed`() {
        val leaving = slide(3f, 2f)
        val position = leaving.getValueFromNanos(80_000_000L)
        val before = floatingNavigationLoreAuroraBlend(position)
        val returning = slide(position, 3f)
        assertEquals(before, floatingNavigationLoreAuroraBlend(returning.getValueFromNanos(0L)))
        assertTrue(floatingNavigationLoreAuroraBlend(returning.getValueFromNanos(32_000_000L)) > before)
        assertEquals(0f, floatingNavigationLoreAuroraBlend(leaving.getValueFromNanos(leaving.durationNanos)))
        assertEquals(1f, floatingNavigationLoreAuroraBlend(returning.getValueFromNanos(returning.durationNanos)))
    }

    @Test
    fun `aurora blend clamps overshoot and hides invalid positions`() {
        assertEquals(0f, floatingNavigationLoreAuroraBlend(-1f))
        assertEquals(1f, floatingNavigationLoreAuroraBlend(4f))
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, floatingNavigationLoreAuroraBlend(invalid))
        }
    }

    private fun slide(start: Float, end: Float, velocity: Float = 0f) = TargetBasedAnimation(
        animationSpec = FloatingNavigationIndicatorMotion,
        typeConverter = Float.VectorConverter,
        initialValue = start,
        targetValue = end,
        initialVelocity = velocity,
    )

    @Test
    fun `rail indicator travel is unchanged for equal 64dp cells`() {
        // At 1px per dp: icons 6dp into 64dp cells 80dp apart, the indicator 2dp into each icon box.
        val equalCells = listOf(6f, 86f, 166f, 246f)
        for (step in 0..300) {
            val position = step / 100f
            val today = 8f + position * 80f
            assertEquals(today, railIndicatorTop(position, equalCells, 6f, 80f, 2f), 0.001f)
            // Before the items are placed the fixed-cell path is used.
            assertEquals(today, railIndicatorTop(position, emptyList(), 6f, 80f, 2f), 0.001f)
        }
    }

    @Test
    fun `rail indicator follows cells that grew for two-line labels`() {
        // Library's label wrapped: its cell is taller, so Lore sits lower than the fixed path.
        val grown = listOf(6f, 86f, 160f, 254f)
        assertEquals(162f, railIndicatorTop(2f, grown, 6f, 80f, 2f), 0.001f)
        assertEquals(256f, railIndicatorTop(3f, grown, 6f, 80f, 2f), 0.001f)
        assertEquals((162f + 256f) / 2f, railIndicatorTop(2.5f, grown, 6f, 80f, 2f), 0.001f)
        // Overshoot and invalid positions stay on the rail.
        assertEquals(256f, railIndicatorTop(3.2f, grown, 6f, 80f, 2f), 0.001f)
        assertEquals(8f, railIndicatorTop(Float.NaN, grown, 6f, 80f, 2f), 0.001f)
        // A half-measured rail keeps the fixed path.
        assertEquals(8f + 2f * 80f, railIndicatorTop(2f, listOf(6f, Float.NaN, 160f, 254f), 6f, 80f, 2f), 0.001f)
    }
}
