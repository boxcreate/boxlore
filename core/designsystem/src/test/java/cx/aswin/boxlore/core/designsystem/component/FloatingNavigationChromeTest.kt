package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FloatingNavigationChromeTest {
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
    }
}
