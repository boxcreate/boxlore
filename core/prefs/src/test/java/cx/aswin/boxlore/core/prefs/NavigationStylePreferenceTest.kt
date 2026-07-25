package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigationStylePreferenceTest {
    @Test
    fun sanitizeNavigationStyle_keepsClassicAndDefaultsToFloating() {
        assertEquals("floating", sanitizeNavigationStyle(null))
        assertEquals("floating", sanitizeNavigationStyle("unsupported"))
        assertEquals("floating", sanitizeNavigationStyle("Floating"))
        assertEquals("classic", sanitizeNavigationStyle(" Classic "))
    }
}
