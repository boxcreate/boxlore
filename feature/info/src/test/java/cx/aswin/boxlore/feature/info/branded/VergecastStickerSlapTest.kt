package cx.aswin.boxlore.feature.info.branded

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastStickerSlap
import cx.aswin.boxlore.feature.info.branded.vergecast.stickerAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VergecastStickerSlapTest {
    @Test
    fun `the sticker flies in from the top right, large and tilted`() {
        val start = stickerAt(0f)
        assertEquals(80f, start.x, 0.01f)
        assertEquals(-96f, start.y, 0.01f)
        assertEquals(28f, start.rotation, 0.01f)
        assertEquals(1.9f, start.scale, 0.01f)
        assertEquals(0f, start.alpha, 0.01f)
    }

    @Test
    fun `it lands at 330ms, squashed, then settles and lifts away by 1s`() {
        val impact = stickerAt(330f)
        assertEquals(0f, impact.x, 0.01f)
        assertEquals(0f, impact.y, 0.01f)
        assertEquals(-9f, impact.rotation, 0.01f)
        assertEquals(0.88f, impact.scale, 0.01f)
        assertEquals(1.07f, stickerAt(410f).scale, 0.01f)
        assertEquals(1f, stickerAt(600f).scale, 0.01f)
        assertEquals(1f, stickerAt(600f).alpha, 0.01f)
        val gone = stickerAt(1_000f)
        assertEquals(0f, gone.alpha, 0.01f)
        assertEquals(0.55f, gone.scale, 0.01f)
        assertEquals(gone, stickerAt(5_000f))
    }

    @Test
    fun `the button thuds once, at impact`() {
        val still = BrandedButtonNudge(0.dp, 1f)
        assertEquals(still, VergecastStickerSlap.nudge(0f))
        assertEquals(still, VergecastStickerSlap.nudge(300f))
        val deepest = VergecastStickerSlap.nudge(300f + 170f * 0.4f)
        assertEquals(2f, deepest.offsetY.value, 0.01f)
        assertEquals(0.985f, deepest.scale, 0.001f)
        assertEquals(still, VergecastStickerSlap.nudge(470f))
    }

    @Test
    fun `alerts and auto-download slide in before the moment ends`() {
        assertTrue(VergecastStickerSlap.revealDelayMillis < VergecastStickerSlap.durationMillis)
        // The Subscribed label finishes fading in at 1,210ms, just before the button takes over.
        assertTrue(VergecastStickerSlap.durationMillis >= 1_210)
    }

    @Test
    fun `a description shows when there is text, a lock notice or recommendations`() {
        fun description(text: String = "", locked: Boolean = false) =
            BrandedDescription(text, locked, emptyList(), expanded = false, onToggle = {}, onPodcastClick = {})
        assertTrue(description(text = "About the show").isVisible)
        assertTrue(description(locked = true).isVisible)
        assertEquals(false, description().isVisible)
    }
}
