package cx.aswin.boxlore.feature.settings.components

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsHeaderTitleTest {
    private val typography = Typography(
        displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 48.sp),
        displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 40.sp),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 26.sp),
    )

    @Test
    fun `expanded headers reduce the oversized title and allow a second line`() {
        val presentation = settingsHeaderTitlePresentation(typography, 0f)

        assertEquals(36.sp, presentation.style.fontSize)
        assertEquals(40.sp, presentation.style.lineHeight)
        assertEquals(2, presentation.maxLines)
        assertTrue(presentation.style.fontSize.value < typography.displayMedium.fontSize.value)
    }

    @Test
    fun `narrow collapsed headers retain wrapping through the entire scroll transition`() {
        val fractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val presentations = fractions.map { settingsHeaderTitlePresentation(typography, it) }

        presentations.forEach { assertEquals(2, it.maxLines) }
        presentations.zipWithNext().forEach { (before, after) ->
            assertTrue(after.style.fontSize.value < before.style.fontSize.value)
            assertTrue(after.style.lineHeight.value < before.style.lineHeight.value)
        }
        assertEquals(22.sp, presentations.last().style.fontSize)
        assertEquals(26.sp, presentations.last().style.lineHeight)
    }

    @Test
    fun `compact toolbar never inherits the hidden expanded title size`() {
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val presentation = settingsHeaderTitlePresentation(typography, fraction, expanded = false)

            assertEquals(22.sp, presentation.style.fontSize)
            assertEquals(26.sp, presentation.style.lineHeight)
            assertEquals(2, presentation.maxLines)
        }
    }

    @Test
    fun `out of range collapse values keep safe readable typography`() {
        assertEquals(36.sp, settingsHeaderTitlePresentation(typography, -1f).style.fontSize)
        assertEquals(22.sp, settingsHeaderTitlePresentation(typography, 2f).style.fontSize)
        assertEquals(36.sp, settingsHeaderTitlePresentation(typography, Float.NaN).style.fontSize)
    }
}
