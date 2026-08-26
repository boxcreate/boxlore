package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetAppearanceTest {
    @Test
    fun sanitizeDefaultsToAppTheme() {
        assertEquals(WidgetAppearance.APP, WidgetAppearance.sanitize(null))
        assertEquals(WidgetAppearance.APP, WidgetAppearance.sanitize("unknown"))
        assertEquals(WidgetAppearance.APP, WidgetAppearance.sanitize(" APP "))
        assertEquals(WidgetAppearance.SYSTEM, WidgetAppearance.sanitize("System"))
    }

    @Test
    fun followsAppThemeIsTheDefault() {
        assertTrue(WidgetAppearance.followsAppTheme(null))
        assertTrue(WidgetAppearance.followsAppTheme(WidgetAppearance.APP))
        assertFalse(WidgetAppearance.followsAppTheme(WidgetAppearance.SYSTEM))
    }
}
