package cx.aswin.boxlore.core.designsystem.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Hermetic guard for Flex roundness presets.
 * Compose FontFamily loading is not exercised on the JVM unit classpath.
 */
class TypographyFlexFamilyTest {
    @Test
    fun `google sans flex soft default is moderate`() {
        assertEquals(50f, GoogleSansFlexRoundness)
        assertEquals(50f, FontRoundness.AXIS_SOFT)
        assertEquals(FontRoundness.SOFT, FontRoundness.DEFAULT_KEY)
    }

    @Test
    fun `font roundness presets map to ROND axis`() {
        assertEquals(0f, FontRoundness.axisValue("crisp"))
        assertEquals(50f, FontRoundness.axisValue("soft"))
        assertEquals(100f, FontRoundness.axisValue("round"))
        assertEquals(50f, FontRoundness.axisValue("unknown"))
        assertEquals("crisp", FontRoundness.sanitizeKey("CRISP"))
    }
}
