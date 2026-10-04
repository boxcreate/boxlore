package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShimmerColorLogicTest {
    @Test
    fun `transparent sweep edges leave base opacity unchanged`() {
        val base = Color.White.copy(alpha = 0.05f)
        assertEquals(base, Color.Transparent.compositeOver(base))
    }

    @Test
    fun `overlay reaches specified highlight without doubling translucent base`() {
        for (color in listOf(Color.White, Color.Black)) {
            val base = color.copy(alpha = 0.05f)
            val highlight = color.copy(alpha = 0.12f)
            val combined = shimmerOverlayColor(base, highlight).compositeOver(base)
            assertEquals(highlight.alpha, combined.alpha, 0.001f)
            assertEquals(highlight.red, combined.red, 0.001f)
        }
    }

    @Test
    fun `opaque base and non brighter highlight do not add opacity`() {
        assertEquals(Color.Transparent, shimmerOverlayColor(Color.White, Color.White))
        assertEquals(Color.Transparent, shimmerOverlayColor(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.2f)))
    }
}
