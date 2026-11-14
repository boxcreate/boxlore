package cx.aswin.boxlore.core.designsystem.share

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The artwork a card uses when no cover loads must be square, so the card never cuts the boxlore mark at its sides. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareFallbackArtworkTest {
    @Test
    fun `the fallback is a square indigo tile with the whole mark inside it`() {
        val art = shareFallbackArtwork(ApplicationProvider.getApplicationContext())
        assertNotNull(art)
        assertEquals(art!!.width, art.height)

        // Every edge is plain indigo: the mark sits inside with room around it.
        val edges = (0 until art.width step 20).flatMap { i ->
            listOf(art.getPixel(i, 0), art.getPixel(i, art.height - 1), art.getPixel(0, i), art.getPixel(art.width - 1, i))
        }
        assertTrue(edges.all { it == ShareCardFallbackSeed })
        // And the white mark is there in the middle band.
        assertTrue((0 until art.width).any { art.getPixel(it, art.height / 2) == android.graphics.Color.WHITE })
    }
}
