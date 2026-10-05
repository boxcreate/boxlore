package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigationChromePlacementTest {
    @Test
    fun `classic player clears the 80dp app navbar and 48dp system buttons`() {
        assertEquals(208.dp, appBottomChromeContentPadding(NavigationStyle.Classic, true, 48.dp))
        assertEquals(636.dp, appMiniPlayerTopOffset(NavigationStyle.Classic, 844.dp, 48.dp))
        assertEquals(660.dp, appMiniPlayerTopOffset(NavigationStyle.Classic, 844.dp, 24.dp))
        assertEquals(684.dp, appMiniPlayerTopOffset(NavigationStyle.Classic, 844.dp, 0.dp))
    }

    @Test
    fun `floating placement retains its existing system inset and capsule clearance`() {
        assertEquals(656.dp, appMiniPlayerTopOffset(NavigationStyle.Floating, 844.dp, 48.dp))
        assertEquals(680.dp, appMiniPlayerTopOffset(NavigationStyle.Floating, 844.dp, 24.dp))
        assertEquals(704.dp, appMiniPlayerTopOffset(NavigationStyle.Floating, 844.dp, 0.dp))
    }

    @Test
    fun `both styles keep player gap and edge-to-edge selector clearance across navigation insets and densities`() {
        NavigationStyle.entries.forEach { style ->
            listOf(0, 16, 24, 48, 96).forEach { inset ->
                val playerTop = appMiniPlayerTopOffset(style, 844.dp, inset.dp)
                val contentClearance = appBottomChromeContentPadding(style, true, inset.dp)
                assertEquals(844.dp - playerTop, contentClearance)
                listOf(1f, 2.75f, 3.5f).forEach { scale -> assertPixelClearance(style, inset, scale) }
            }
        }
    }

    private fun assertPixelClearance(style: NavigationStyle, inset: Int, scale: Float) {
        val metrics = navigationChromeMetrics(style)
        val playerTop = appMiniPlayerTopOffset(style, 844.dp, inset.dp)
        val navigationTop = 844.dp - inset.dp - metrics.bottomNavigationClearance
        val contentClearance = appBottomChromeContentPadding(style, true, inset.dp)
        with(Density(scale)) {
            val playerBottom = playerTop.toPx() + metrics.miniPlayerHeight.toPx()
            assertEquals(metrics.miniPlayerNavigationGap.toPx(), navigationTop.toPx() - playerBottom, 0.001f)
            val selectorBottom = (844.dp - contentClearance - 16.dp).toPx()
            assertEquals(16.dp.toPx(), playerTop.toPx() - selectorBottom, 0.001f)
        }
    }

    @Test
    fun `parents that already reserve system navigation keep the original content padding`() {
        assertEquals(80.dp, appBottomChromeContentPadding(NavigationStyle.Classic, false))
        assertEquals(160.dp, appBottomChromeContentPadding(NavigationStyle.Classic, true))
        assertEquals(68.dp, appBottomChromeContentPadding(NavigationStyle.Floating, false))
        assertEquals(140.dp, appBottomChromeContentPadding(NavigationStyle.Floating, true))
    }

    @Test
    fun `tiny windows and negative insets never put the player above the window`() {
        NavigationStyle.entries.forEach { style ->
            assertEquals(0.dp, appMiniPlayerTopOffset(style, 100.dp, 48.dp))
            assertEquals(appMiniPlayerTopOffset(style, 844.dp, 0.dp), appMiniPlayerTopOffset(style, 844.dp, (-24).dp))
        }
    }
}
