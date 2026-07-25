package cx.aswin.boxlore.core.designsystem.theme

import android.graphics.Typeface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GoogleSansFlexTypefaceTest {
    @Test
    fun styleForWeight_mapsBoldAndItalicStyles() {
        assertEquals(Typeface.BOLD, GoogleSansFlexTypeface.styleForWeight(700, italic = false))
        assertEquals(Typeface.BOLD_ITALIC, GoogleSansFlexTypeface.styleForWeight(700, italic = true))
        assertEquals(Typeface.ITALIC, GoogleSansFlexTypeface.styleForWeight(400, italic = true))
        assertEquals(Typeface.NORMAL, GoogleSansFlexTypeface.styleForWeight(400, italic = false))
    }

    @Test
    fun variationSettings_preservesAllRequestedAxes() {
        assertEquals(
            "'wght' 700, 'ROND' 50, 'opsz' 24.0, 'wdth' 75.0",
            GoogleSansFlexTypeface.variationSettings(
                weight = 700,
                roundness = 50,
                opticalSize = 24f,
                width = 75f,
            ),
        )
    }
}
