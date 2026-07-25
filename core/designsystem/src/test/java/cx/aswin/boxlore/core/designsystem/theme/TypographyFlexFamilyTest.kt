package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Hermetic guard for Flex roundness presets.
 * Compose FontFamily loading is not exercised on the JVM unit classpath.
 */
class TypographyFlexFamilyTest {
    @Test
    fun `google sans flex round default is full ROND`() {
        assertEquals(100f, GoogleSansFlexRoundness)
        assertEquals(100f, FontRoundness.AXIS_ROUND)
        assertEquals(FontRoundness.ROUND, FontRoundness.DEFAULT_KEY)
    }

    @Test
    fun `font roundness presets map to ROND axis`() {
        assertEquals(0f, FontRoundness.axisValue("crisp"))
        assertEquals(50f, FontRoundness.axisValue("soft"))
        assertEquals(100f, FontRoundness.axisValue("round"))
        assertEquals(100f, FontRoundness.axisValue("unknown"))
        assertEquals("crisp", FontRoundness.sanitizeKey("CRISP"))
    }

    @Test
    fun `google sans weight scale stays intentionally light`() {
        assertEquals(FontWeight.Normal, GoogleSansWeight.regular)
        assertEquals(FontWeight.Normal, GoogleSansWeight.medium)
        assertEquals(FontWeight.Medium, GoogleSansWeight.semiBold)
        assertEquals(FontWeight.SemiBold, GoogleSansWeight.bold)
        assertEquals(FontWeight.SemiBold, GoogleSansWeight.extraBold)
    }
}
