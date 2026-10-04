package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloatingNavigationChromeTest {
    @Test
    fun `floating player is a full capsule and Classic retains its distinct corners`() {
        val floating = navigationChromeMetrics(NavigationStyle.Floating)
        val classic = navigationChromeMetrics(NavigationStyle.Classic)

        assertEquals(64.dp, floating.miniPlayerHeight)
        assertEquals(floating.miniPlayerHeight / 2, floating.miniPlayerTopCornerRadius)
        assertEquals(floating.miniPlayerHeight / 2, floating.miniPlayerBottomCornerRadius)
        assertEquals(72.dp, classic.miniPlayerHeight)
        assertEquals(26.dp, classic.miniPlayerTopCornerRadius)
        assertEquals(14.dp, classic.miniPlayerBottomCornerRadius)
    }

    @Test
    fun `navigation chrome padding reserves each presentation consistently`() {
        assertEquals(68.dp, AppBottomNavigationClearance)
        assertEquals(
            68.dp,
            appBottomChromeContentPadding(
                style = NavigationStyle.Floating,
                isMiniPlayerVisible = false,
            ),
        )
        assertEquals(
            140.dp,
            appBottomChromeContentPadding(
                style = NavigationStyle.Floating,
                isMiniPlayerVisible = true,
            ),
        )
        assertEquals(
            80.dp,
            appBottomChromeContentPadding(
                style = NavigationStyle.Classic,
                isMiniPlayerVisible = false,
            ),
        )
        assertEquals(
            160.dp,
            appBottomChromeContentPadding(
                style = NavigationStyle.Classic,
                isMiniPlayerVisible = true,
            ),
        )
    }

    @Test
    fun `destination selection accepts only root or query routes`() {
        assertEquals(true, isNavDestinationSelected("explore", "explore"))
        assertEquals(true, isNavDestinationSelected("explore?entryPoint=bottom_nav", "explore"))
        assertEquals(false, isNavDestinationSelected("explore/detail", "explore"))
        assertEquals(false, isNavDestinationSelected("learn", "explore"))
        assertEquals(true, isNavDestinationSelected("learn", "learn"))
        assertEquals(true, isNavDestinationSelected("learn?entryPoint=bottom_nav", "learn"))
        assertEquals(false, isNavDestinationSelected("learn_history", "learn"))
    }

    @Test
    fun `resting floating navigation keeps three labels and external Lore geometry`() {
        val layout = floatingNavigationLayout(availableWidth = 328.dp, compactProgress = 0f)

        assertEquals(262.dp, layout.pillWidth)
        assertEquals(52.dp, layout.loreSize)
        assertEquals(14.dp, layout.loreStart - layout.pillWidth)
        assertEquals(328.dp, layout.loreStart + layout.loreSize)
        assertEquals(250f / 3f, layout.tabWidth.value, 0.001f)
    }

    @Test
    fun `compact floating navigation reserves external slot for player and centers Lore in fourth tab`() {
        val layout = floatingNavigationLayout(availableWidth = 328.dp, compactProgress = 1f)

        assertEquals(262.dp, layout.pillWidth)
        assertEquals(62.5.dp, layout.tabWidth)
        assertEquals(48.dp, layout.loreSize)
        assertEquals(200.75.dp, layout.loreStart)
        assertEquals(
            6.dp + layout.tabWidth * 3.5f,
            layout.loreStart + layout.loreSize / 2,
        )
        assertTrue(layout.loreStart + layout.loreSize <= layout.pillWidth - 6.dp)
    }

    @Test
    fun `Lore finishes joining the pill before player docking begins`() {
        val compact = floatingNavigationLayout(328.dp, 1f)
        assertEquals(compact, floatingNavigationLayout(328.dp, 0.65f))
        assertEquals(compact, floatingNavigationLayout(328.dp, 0.825f))
    }

    @Test
    fun `one indicator remains centered on Lore throughout the player morph`() {
        for (step in 0..100) {
            val progress = step / 100f
            val layout = floatingNavigationLayout(328.dp, progress)
            val indicator = floatingNavigationIndicatorBounds(328.dp, progress, 3f)
            assertEquals(
                (layout.loreStart + layout.loreSize / 2).value,
                (indicator.start + indicator.width / 2).value,
                0.001f,
            )
            assertTrue(indicator.height in 44.dp..52.dp)
        }
        val normal = floatingNavigationIndicatorBounds(328.dp, 0f, 3f)
        assertEquals(52.dp, normal.width)
        assertEquals(52.dp, normal.height)
        val compact = floatingNavigationIndicatorBounds(328.dp, 1f, 3f)
        assertEquals(62.5.dp, compact.width)
        assertEquals(44.dp, compact.height)
    }

    @Test
    fun `shared indicator has one continuous bounded path across all four tabs`() {
        for (screenWidth in listOf(320, 360, 420, 600)) {
            val availableWidth = screenWidth.dp - AppNavigationBarHorizontalInset * 2
            for (compact in listOf(0f, 0.325f, 0.65f, 1f)) {
                var previousCenter = 0.dp
                for (step in 0..300) {
                    val bounds = floatingNavigationIndicatorBounds(availableWidth, compact, step / 100f)
                    val center = bounds.start + bounds.width / 2
                    assertTrue(center >= previousCenter)
                    assertTrue(bounds.start >= 0.dp)
                    assertTrue(bounds.start + bounds.width <= availableWidth + 0.001.dp)
                    // placeRelative mirrors this single surface with the actions in RTL.
                    val mirroredStart = availableWidth - bounds.start - bounds.width
                    assertTrue(mirroredStart >= (-0.001).dp)
                    previousCenter = center
                }
            }
        }
    }

    @Test
    fun `Lore motion keeps click bounds clear of primary tabs and minimum touch size`() {
        listOf(320, 360, 420, 600).forEach { screenWidth ->
            val availableWidth = screenWidth.dp - AppNavigationBarHorizontalInset * 2
            val restingPillWidth = floatingNavigationLayout(availableWidth, 0f).pillWidth
            var previousLoreStart = Float.POSITIVE_INFINITY
            for (step in 0..100) {
                val layout = floatingNavigationLayout(availableWidth, step / 100f)
                val primaryTabsEnd = 6.dp + layout.tabWidth * 3

                assertEquals(restingPillWidth, layout.pillWidth)
                assertTrue(layout.tabWidth >= 48.dp, "Primary targets at width=$screenWidth step=$step")
                assertTrue(layout.loreSize >= 48.dp, "Lore target at width=$screenWidth step=$step")
                assertTrue(layout.loreStart >= primaryTabsEnd, "Lore overlaps primary tabs at width=$screenWidth step=$step")
                assertTrue(layout.loreStart.value <= previousLoreStart, "Lore should move toward its fourth slot")
                previousLoreStart = layout.loreStart.value
            }
        }
    }

    @Test
    fun `Lore clears the external player slot before player descends beside navigation`() {
        listOf(320, 360, 420, 600).forEach { screenWidth ->
            val availableWidth = screenWidth.dp - AppNavigationBarHorizontalInset * 2
            val externalSlotStart = floatingNavigationLayout(availableWidth, 0f).loreStart
            for (step in 70..100) {
                val layout = floatingNavigationLayout(availableWidth, step / 100f)

                // Logical start coordinates mirror together in RTL. Checking the
                // complete Lore touch rect also covers its changing diameter.
                assertTrue(
                    layout.loreStart + layout.loreSize <= externalSlotStart,
                    "Lore overlaps the player's external slot at width=$screenWidth step=$step",
                )
            }
        }
    }

    @Test
    fun `compact progress clamps spring overshoot and invalid values safely`() {
        assertEquals(0f, boundedNavigationCompactProgress(-0.2f))
        assertEquals(1f, boundedNavigationCompactProgress(1.2f))
        assertEquals(0.5f, boundedNavigationCompactProgress(0.5f))
        assertEquals(0f, boundedNavigationCompactProgress(Float.NaN))
        assertEquals(0f, boundedNavigationCompactProgress(Float.POSITIVE_INFINITY))
        assertEquals(0f, boundedNavigationCompactProgress(Float.NEGATIVE_INFINITY))
        assertEquals(
            floatingNavigationLayout(328.dp, 0f),
            floatingNavigationLayout(328.dp, -0.2f),
        )
        assertEquals(
            floatingNavigationLayout(328.dp, 1f),
            floatingNavigationLayout(328.dp, 1.2f),
        )
    }
}
