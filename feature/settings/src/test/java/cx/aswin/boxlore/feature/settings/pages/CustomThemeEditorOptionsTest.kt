package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomThemeEditorOptionsTest {
    @Test fun `pasted hex accepts hash whitespace and lowercase`() {
        assertEquals("22AACC", normalizeEditorHex(" #22aaCC "))
        assertEquals(Color(0xFF22AACC), parseEditorHex(" #22aaCC "))
    }

    @Test fun `incomplete invalid and alpha hex never become saved colors`() {
        listOf("", "123", "ZZ0000", "FF123456", "#1234567", "123456garbage").forEach { assertNull(parseEditorHex(it), it) }
    }

    @Test fun `legacy backgrounds retain their equivalent without a locked display mode`() {
        assertEquals(SurfaceStyles.CLASSIC_DYNAMIC, editorSurfaceStyle("classic_dark"))
        assertEquals(SurfaceStyles.CLASSIC_DYNAMIC, editorSurfaceStyle("classic_light"))
        assertEquals(SurfaceStyles.DYNAMIC_OLED_WHITE, editorSurfaceStyle("amoled"))
        assertEquals(SurfaceStyles.DYNAMIC_OLED_WHITE, editorSurfaceStyle("purewhite"))
        assertEquals("preset:aurora", editorSurfaceStyle("preset:aurora"))
        assertEquals(SurfaceStyles.STANDARD, editorSurfaceStyle("unknown"))
    }

    @Test fun `suggested supporting color preserves explicit primary and other supporting seed`() {
        val seeds = CustomThemeSeeds(Color.Blue, Color.Green, Color.Red)
        assertEquals(CustomThemeSeeds(Color.Blue, null, Color.Red), seeds.withRoleColor(1, null))
        assertEquals(CustomThemeSeeds(Color.Blue, Color.Green, null), seeds.withRoleColor(2, null))
        assertEquals(seeds, seeds.withRoleColor(0, null))
    }
}
