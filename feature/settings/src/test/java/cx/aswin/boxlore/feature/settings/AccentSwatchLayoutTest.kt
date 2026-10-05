package cx.aswin.boxlore.feature.settings

import cx.aswin.boxlore.feature.settings.components.accentSwatchColumnCount
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentSwatchLayoutTest {
    @Test fun gridFitsFullTouchTargetsOnNarrowAndWideScreens() {
        assertEquals(4, accentSwatchColumnCount(256f))
        assertEquals(5, accentSwatchColumnCount(296f))
        assertEquals(5, accentSwatchColumnCount(700f))
        assertEquals(2, accentSwatchColumnCount(120f))
        assertEquals(3, accentSwatchColumnCount(700f, 3))
    }

    @Test fun invalidConstraintsRetainAtLeastOneUsableColumn() {
        assertEquals(1, accentSwatchColumnCount(Float.NaN))
        assertEquals(1, accentSwatchColumnCount(-1f))
        assertEquals(1, accentSwatchColumnCount(700f, 0))
    }
}
