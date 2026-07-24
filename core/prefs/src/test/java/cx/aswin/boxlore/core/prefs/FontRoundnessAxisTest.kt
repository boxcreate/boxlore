package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FontRoundnessAxisTest {
    @Test
    fun sanitizeKey_normalizesAndDefaultsToSoft() {
        assertEquals(FontRoundnessAxis.SOFT, FontRoundnessAxis.sanitizeKey(null))
        assertEquals(FontRoundnessAxis.SOFT, FontRoundnessAxis.sanitizeKey("unknown"))
        assertEquals(FontRoundnessAxis.CRISP, FontRoundnessAxis.sanitizeKey(" CRISP "))
        assertEquals(FontRoundnessAxis.ROUND, FontRoundnessAxis.sanitizeKey("round"))
    }

    @Test
    fun axisValue_mapsPresetsToRondAxis() {
        assertEquals(FontRoundnessAxis.AXIS_CRISP, FontRoundnessAxis.axisValue("crisp"))
        assertEquals(FontRoundnessAxis.AXIS_SOFT, FontRoundnessAxis.axisValue("soft"))
        assertEquals(FontRoundnessAxis.AXIS_ROUND, FontRoundnessAxis.axisValue("round"))
        assertEquals(FontRoundnessAxis.AXIS_SOFT, FontRoundnessAxis.axisValue("invalid"))
    }
}
