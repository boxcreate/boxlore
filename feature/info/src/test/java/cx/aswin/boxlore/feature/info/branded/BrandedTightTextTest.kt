package cx.aswin.boxlore.feature.info.branded

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrandedTightTextTest {
    @Test
    fun `trims the font's extra height from text set tighter than its face`() {
        // The Vergecast's 31/34 Anton: about 13 extra on 34-high lines.
        assertEquals(13, tightTextExcess(height = 47, lines = 1, lineHeight = 34f))
        assertEquals(13, tightTextExcess(height = 115, lines = 3, lineHeight = 34f))
    }

    @Test
    fun `trims more than half a line when the face is that much taller`() {
        // JRE's 55/51 Anton number carries 32 extra on a 51 line; guessing the line count from
        // the height made that two lines and trimmed nothing, pushing the number down the card.
        assertEquals(32, tightTextExcess(height = 83, lines = 1, lineHeight = 51f))
        assertEquals(22, tightTextExcess(height = 122, lines = 2, lineHeight = 50f))
    }

    @Test
    fun `never grows text that already fits its lines`() {
        assertEquals(0, tightTextExcess(height = 40, lines = 2, lineHeight = 20f))
        assertEquals(0, tightTextExcess(height = 38, lines = 2, lineHeight = 20f))
    }
}
