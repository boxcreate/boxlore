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
}
