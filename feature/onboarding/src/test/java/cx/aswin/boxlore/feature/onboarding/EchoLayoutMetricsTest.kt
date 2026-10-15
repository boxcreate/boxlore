package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoLayoutMetricsTest {
    @Test
    fun `height picks the tier and larger text drops one`() {
        assertEquals(EchoLayoutTier.Spacious, EchoLayout.tier(700.dp, 1f))
        assertEquals(EchoLayoutTier.Roomy, EchoLayout.tier(640.dp, 1f))
        assertEquals(EchoLayoutTier.Regular, EchoLayout.tier(540.dp, 1f))
        assertEquals(EchoLayoutTier.Compact, EchoLayout.tier(420.dp, 1f))
        assertEquals(EchoLayoutTier.Roomy, EchoLayout.tier(700.dp, 1.3f))
        assertEquals(EchoLayoutTier.Compact, EchoLayout.tier(420.dp, 2f))
    }

    @Test
    fun `smaller tiers shrink Echo and rows but never below the touch target`() {
        val tiers = listOf(700.dp, 640.dp, 540.dp, 420.dp).map { EchoLayout.metrics(it, 1f, keyboardOpen = false) }
        assertEquals(tiers.sortedByDescending { it.avatarWidth }, tiers)
        assertEquals(tiers.sortedByDescending { it.rowHeight }, tiers)
        assertTrue(tiers.all { it.rowHeight >= 48.dp && it.tileHeight >= 48.dp })
        // Stacked topic tiles (badge above label) only where there is height for them.
        assertEquals(listOf(true, true, false, false), tiers.map { it.tileStacked })
        assertTrue(tiers.all { it.questionMin < it.questionMax })
    }

    @Test
    fun `an open keyboard or the tightest screens fold Echo away`() {
        assertFalse(EchoLayout.metrics(700.dp, 1f, keyboardOpen = true).showEcho)
        assertTrue(EchoLayout.metrics(700.dp, 1f, keyboardOpen = false).showEcho)
        assertTrue(EchoLayout.metrics(420.dp, 1f, keyboardOpen = false).showEcho)
        assertFalse(EchoLayout.metrics(380.dp, 1f, keyboardOpen = false).showEcho)
        assertTrue(EchoLayout.metrics(700.dp, 2f, keyboardOpen = false).showEcho)
        assertFalse(EchoLayout.metrics(440.dp, 2f, keyboardOpen = false).showEcho)
        assertEquals(EchoLayoutTier.Compact, EchoLayout.metrics(440.dp, 2f, keyboardOpen = false).tier)
    }

    @Test
    fun `the answer slot minimum covers four rows, their gaps and Something else`() {
        assertEquals(60.dp * 4 + EchoLayout.GroupGap * 3 + EchoLayout.SomethingElseHeight, EchoLayout.answersMinHeight(60.dp))
        assertTrue(EchoLayout.SpareAboveShare + EchoLayout.SpareBetweenShare < 1f)
        assertTrue(EchoLayout.GrowShare in 0f..1f)
    }

    @Test
    fun `badge shapes cycle without running out`() {
        assertEquals(0, echoBadgeShapeIndex(0))
        assertEquals(0, echoBadgeShapeIndex(EchoBadgeShapes.size))
        assertEquals(EchoBadgeShapes.size - 1, echoBadgeShapeIndex(-1))
        assertEquals(EchoBadgeShapes.size, EchoBadgeShapes.distinct().size)
    }
}
