package cx.aswin.boxlore.core.playback.service.auto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A show tile's second line: where the name carries on when the first line cuts it. */
class AutoShowTitlesTest {
    @Test
    fun `names that fit on one line leave the second line free`() {
        // Measured on a 1280 by 720 car screen: both of these are shown whole.
        assertNull(AutoShowTitles.secondLine("The Vergecast"))
        assertNull(AutoShowTitles.secondLine("Darknet Diaries"))
        assertNull(AutoShowTitles.secondLine("Serial"))
    }

    @Test
    fun `a longer name carries on from the word the first line is cut in`() {
        // The same screen cut these to "The Rest Is Hi…", "Conspiracy T…" and "The Desi Crim…".
        assertEquals("History", AutoShowTitles.secondLine("The Rest Is History"))
        assertEquals("Theories", AutoShowTitles.secondLine("Conspiracy Theories"))
        assertEquals("Crime Podcast", AutoShowTitles.secondLine("The Desi Crime Podcast"))
    }

    @Test
    fun `one very long word has nothing shorter to carry over`() {
        assertNull(AutoShowTitles.secondLine("Supercalifragilisticexpialidocious"))
    }

    @Test
    fun `extra spaces do not change the result`() {
        assertEquals("History", AutoShowTitles.secondLine("  The   Rest Is   History "))
    }
}
