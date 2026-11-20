package cx.aswin.boxlore.feature.info.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeDetailsLineTest {
    @Test
    fun `joins the parts that have text`() {
        assertEquals("S2 E4 · 3h ago · 40m", episodeDetailsLine(listOf("S2 E4", "3h ago", "40m")).text)
        assertEquals("3h ago · 40m", episodeDetailsLine(listOf("", "3h ago", "40m")).text)
        assertEquals("", episodeDetailsLine(listOf("", " ")).text)
    }

    @Test
    fun `styles only the highlighted part`() {
        val style = SpanStyle(color = Color.Red)
        val line = episodeDetailsLine(listOf("", "3h ago", "40m"), highlightIndex = 1, highlight = style)
        val span = line.spanStyles.single()
        assertEquals(style, span.item)
        assertEquals("3h ago", line.text.substring(span.start, span.end))
    }

    @Test
    fun `no highlight style leaves the line plain`() {
        assertTrue(episodeDetailsLine(listOf("3h ago", "40m"), highlightIndex = 0).spanStyles.isEmpty())
    }
}
