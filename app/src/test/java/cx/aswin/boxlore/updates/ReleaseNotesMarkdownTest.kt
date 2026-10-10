package cx.aswin.boxlore.updates

import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesMarkdownTest {
    @Test
    fun `PR references disappear while useful links remain`() {
        val source = "- Better playback ([#123](https://github.com/boxcreate/boxlore/pull/123))\n- Clearer settings (#456)\n[Help](https://example.com/help)"
        assertEquals("- Better playback\n- Clearer settings\n[Help](https://example.com/help)", stripReleasePrReferences(source))
    }

    @Test
    fun `headings lists and legacy paragraphs retain their content`() {
        val blocks = releaseNotesBlocks("## Improvements\r\n\r\n- Faster playback\n2. Clearer settings\n\nLegacy paragraph\ncontinued")
        assertEquals(2, blocks[0].heading)
        assertEquals("•", blocks[1].marker)
        assertEquals("2.", blocks[2].marker)
        assertEquals("Legacy paragraph\ncontinued", blocks[3].text)
    }

    @Test
    fun `inline emphasis and web links hide markdown markers`() {
        val text = releaseNotesInline("**Bold** and *italic*, `code`, [Notes](https://example.com)", SpanStyle())
        assertEquals("Bold and italic, code, Notes", text.text)
        assertEquals(1, text.getLinkAnnotations(0, text.length).size)
    }

    @Test
    fun `asterisk and underscore delimiters preserve distinct bold italic and code styles`() {
        val text = releaseNotesInline("**bold** __strong__ *italic* _emphasis_ `code`", SpanStyle())
        assertEquals("bold strong italic emphasis code", text.text)
        assertEquals(listOf(androidx.compose.ui.text.font.FontWeight.Bold, androidx.compose.ui.text.font.FontWeight.Bold), text.spanStyles.mapNotNull { it.item.fontWeight })
        assertEquals(2, text.spanStyles.count { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
        assertEquals(1, text.spanStyles.count { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace })
        assertEquals("*mixed_", releaseNotesInline("*mixed_", SpanStyle()).text)
    }

    @Test
    fun `adjacent emphasis preserves delimiter precedence`() {
        val text = releaseNotesInline("**first**__second__*third*_fourth_ tail", SpanStyle())
        assertEquals("firstsecondthirdfourth tail", text.text)
        assertEquals(4, text.spanStyles.size)
        assertEquals(listOf(0, 5, 11, 16), text.spanStyles.map { it.start })
    }

    @Test
    fun `delimiters inside code and links do not consume later formatting`() {
        val text = releaseNotesInline("`**hidden` **visible** [**Label**](https://example.com) _after_", SpanStyle())
        assertEquals("**hidden visible **Label** after", text.text)
        assertEquals(1, text.getLinkAnnotations(0, text.length).size)
        assertEquals(1, text.spanStyles.count { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertEquals(1, text.spanStyles.count { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
        assertEquals(1, text.spanStyles.count { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace })
    }

    @Test
    fun `unsafe link schemes remain inert plain text`() {
        val text = releaseNotesInline("[Unsafe](javascript:alert) and <script>text</script>", SpanStyle())
        assertEquals(0, text.getLinkAnnotations(0, text.length).size)
        assertEquals("[Unsafe](javascript:alert) and <script>text</script>", text.text)
    }
}
