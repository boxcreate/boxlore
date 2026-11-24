package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCodeInputTest {
    @Test
    fun `the field keeps only the code's own characters, in upper case, at most eight`() {
        assertEquals("K7QX4M2P", TvCodeInput.clean("k7qx-4m2p"))
        assertEquals("K7QX4M2P", TvCodeInput.clean(" K7QX 4M2P 99"))
        // Look-alikes never appear in a code, so they are dropped rather than guessed.
        assertEquals("K7QX", TvCodeInput.clean("K7QX0OI1L"))
        assertTrue(TvCodeInput.isComplete("K7QX4M2P"))
        assertFalse(TvCodeInput.isComplete("K7QX4M2"))
    }

    @Test
    fun `the code shows in two groups of four, as on the TV`() {
        val shown = TvCodeInput.grouped.filter(AnnotatedString("K7QX4M2P"))
        assertEquals("K7QX-4M2P", shown.text.text)
        assertEquals(9, shown.offsetMapping.originalToTransformed(8))
        assertEquals(4, shown.offsetMapping.originalToTransformed(4))
        assertEquals("K7Q", TvCodeInput.grouped.filter(AnnotatedString("K7Q")).text.text)
    }
}
