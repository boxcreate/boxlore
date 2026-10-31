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

    @Test
    fun `relative dates step from minutes to years`() {
        val now = 100_000_000L
        assertEquals("", formatEpisodeRelativeDate(0, now))
        assertEquals("55m ago", formatEpisodeRelativeDate(now - 55 * 60, now))
        assertEquals("20h ago", formatEpisodeRelativeDate(now - 20 * 3600, now))
        assertEquals("3d ago", formatEpisodeRelativeDate(now - 3 * 86400, now))
        assertEquals("2w ago", formatEpisodeRelativeDate(now - 14 * 86400, now))
        assertEquals("2mo ago", formatEpisodeRelativeDate(now - 60 * 86400, now))
        assertEquals("1y ago", formatEpisodeRelativeDate(now - 365 * 86400, now))
    }
}
