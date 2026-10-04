package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptivePlayerOverlayOffsetTest {
    @Test
    fun `contraction and docking ease to rest at their shared boundary`() {
        val delta = 0.001f
        assertTrue(adaptivePlayerContractionProgress(delta) < 0.00001f)
        assertTrue(1f - adaptivePlayerContractionProgress(0.65f - delta) < 0.00001f)
        assertEquals(1f, adaptivePlayerContractionProgress(0.65f + delta))
        assertEquals(0f, adaptivePlayerDockingProgress(0.65f - delta))
        assertTrue(adaptivePlayerDockingProgress(0.65f + delta) < 0.00003f)
        assertTrue(1f - adaptivePlayerDockingProgress(1f - delta) < 0.00003f)
        for (progress in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, adaptivePlayerContractionProgress(progress))
            assertEquals(0f, adaptivePlayerDockingProgress(progress))
        }
    }

    @Test
    fun `floating overlays reclaim the player row only after contraction finishes`() {
        for (progress in listOf(0f, 0.325f, 0.65f)) {
            assertEquals(0.dp, offset(progress))
        }
        assertEquals(36f, offset(0.825f).value, 0.001f)
        assertEquals(72.dp, offset(1f))
    }

    @Test
    fun `tab selectors and stacked Play All share clearance in both directions`() {
        val systemInset = 24.dp
        val expandedChrome = appBottomChromeContentPadding(NavigationStyle.Floating, true)
        val compactChrome = appBottomChromeContentPadding(NavigationStyle.Floating, false)
        // Explore tabs, Subscriptions tabs, Play All above the 44dp selector,
        // and Play All with top tabs all use the same reclaimed chrome row.
        for (extraClearance in listOf(16.dp, 16.dp, 16.dp + 44.dp + 12.dp, 16.dp)) {
            val expandedPadding = expandedChrome + systemInset + extraClearance
            assertEquals(expandedPadding, expandedPadding - offset(0f))
            assertEquals(compactChrome + systemInset + extraClearance, expandedPadding - offset(1f))
            var previousPadding = expandedPadding
            for (step in 0..100) {
                val padding = expandedPadding - offset(step / 100f)
                assertTrue(padding <= previousPadding)
                assertTrue(padding >= compactChrome + systemInset + extraClearance)
                previousPadding = padding
            }
            for (step in 100 downTo 0) {
                val padding = expandedPadding - offset(step / 100f)
                assertTrue(padding >= previousPadding)
                previousPadding = padding
            }
            assertEquals(expandedPadding, previousPadding)
        }
    }

    @Test
    fun `Classic and absent playback keep their existing placement`() {
        for (progress in listOf(0f, 0.825f, 1f)) {
            assertEquals(0.dp, appBottomChromeOverlayOffset(NavigationStyle.Classic, true, progress))
            assertEquals(0.dp, appBottomChromeOverlayOffset(NavigationStyle.Floating, false, progress))
        }
    }

    @Test
    fun `invalid and overshooting progress cannot move overlays outside chrome clearance`() {
        assertEquals(0.dp, offset(-1f))
        assertEquals(72.dp, offset(2f))
        for (progress in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0.dp, offset(progress))
        }
    }

    private fun offset(progress: Float) = appBottomChromeOverlayOffset(NavigationStyle.Floating, true, progress)
}
