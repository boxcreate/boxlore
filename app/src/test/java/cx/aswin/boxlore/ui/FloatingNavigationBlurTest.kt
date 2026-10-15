package cx.aswin.boxlore.ui

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.prefs.NavigationBlurLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloatingNavigationBlurTest {
    @Test
    fun `the blur shows only behind a visible floating bar, at any level but off`() {
        assertTrue(FloatingNavigationBlur.shows(NavigationStyle.Floating, navigationVisible = true, level = NavigationBlurLevel.SOFT))
        assertFalse(FloatingNavigationBlur.shows(NavigationStyle.Classic, navigationVisible = true, level = NavigationBlurLevel.STRONG))
        assertFalse(FloatingNavigationBlur.shows(NavigationStyle.Floating, navigationVisible = false, level = NavigationBlurLevel.SOFT))
        assertFalse(FloatingNavigationBlur.shows(NavigationStyle.Floating, navigationVisible = true, level = NavigationBlurLevel.OFF))
    }

    @Test
    fun `each level is stronger and reaches higher than the one before, and soft is the gentle default`() {
        val levels = listOf(NavigationBlurLevel.SOFT, NavigationBlurLevel.MEDIUM, NavigationBlurLevel.STRONG).map(FloatingNavigationBlur::spec)
        levels.zipWithNext { weaker, stronger ->
            assertTrue(stronger.radius > weaker.radius)
            assertTrue(stronger.tintAlpha > weaker.tintAlpha)
            assertTrue(stronger.reach > weaker.reach)
        }
        assertEquals(FloatingNavigationBlur.spec(NavigationBlurLevel.SOFT), FloatingNavigationBlur.spec("unknown"))
    }

    @Test
    fun `the fade starts at nothing, eases in slowly and ends fully visible`() {
        val stops = FloatingNavigationBlur.fadeStops()
        assertEquals(0f to 0f, stops.first())
        assertEquals(1f to 1f, stops.last())
        stops.zipWithNext { a, b -> assertTrue(b.second > a.second) }
        // Ease-in: at the halfway point it is still well under half visible.
        assertTrue(stops.first { it.first >= 0.45f }.second < 0.15f)
    }

    @Test
    fun `the strip stays behind the bar and does not grow when the mini player shows`() {
        val inset = 24.dp
        val bar = FloatingNavigationBlur.barChrome(NavigationStyle.Floating, inset)
        assertEquals(appBottomChromeContentPadding(NavigationStyle.Floating, isMiniPlayerVisible = false, systemNavigationInset = inset), bar)
        assertTrue(bar < appBottomChromeContentPadding(NavigationStyle.Floating, isMiniPlayerVisible = true, systemNavigationInset = inset))
    }

    @Test
    fun `the strip covers the bottom chrome plus the reach, never less than the reach`() {
        assertEquals(148.dp, FloatingNavigationBlur.stripHeight(120.dp, 28.dp))
        assertEquals(28.dp, FloatingNavigationBlur.stripHeight((-4).dp, 28.dp))
    }
}
