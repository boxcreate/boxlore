package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.designsystem.briefing.briefingEditorialColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BriefingEditorialColorsTest {
    @Test fun `editorial surfaces preserve every user accent and readable ink in both modes`() {
        listOf(false, true).forEach { dark ->
            val base = if (dark) ClassicBrandColors.dark else ClassicBrandColors.light
            val paper = briefingEditorialColors(base, dark)
            assertEquals(base.primary, paper.primary)
            assertEquals(base.onPrimary, paper.onPrimary)
            assertEquals(base.primaryContainer, paper.primaryContainer)
            assertEquals(base.secondary, paper.secondary)
            assertEquals(base.tertiary, paper.tertiary)
            assertEquals(base.error, paper.error)
            listOf(paper.background, paper.surface, paper.surfaceContainer, paper.surfaceContainerHigh).forEach { surface ->
                listOf(paper.onSurface, paper.onSurfaceVariant).forEach { ink ->
                    val high = maxOf(surface.luminance(), ink.luminance())
                    val low = minOf(surface.luminance(), ink.luminance())
                    assertTrue((high + 0.05f) / (low + 0.05f) >= 4.5f)
                }
            }
        }
    }
}
