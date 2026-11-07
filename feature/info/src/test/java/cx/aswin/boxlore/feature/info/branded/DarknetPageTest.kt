package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.darknet.DarknetColors
import cx.aswin.boxlore.feature.info.branded.darknet.DarknetFlagWave
import cx.aswin.boxlore.feature.info.branded.darknet.DarknetMaskedHost
import cx.aswin.boxlore.feature.info.branded.darknet.DarknetMotion
import cx.aswin.boxlore.feature.info.branded.darknet.DarknetPage
import cx.aswin.boxlore.feature.info.branded.darknet.DarknetTitle
import cx.aswin.boxlore.feature.info.branded.darknet.InkFleck
import cx.aswin.boxlore.feature.info.branded.darknet.darknetTitle
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DarknetPageTest {
    @Test
    fun `Darknet Diaries gets its inked flag page, dark in both themes`() {
        assertSame(DarknetPage, brandedShowPageFor(TestFixtures.podcast(id = "577105")))
        assertSame(DarknetPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://podcast.darknetdiaries.com")))
        assertTrue(DarknetPage.darkOnly)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable`(dark: Boolean) {
        val style = DarknetPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        assertReadable(c.onSurface, c.surface)
        assertReadable(c.onSurfaceVariant, c.surface)
        assertReadable(c.onSurface, c.surfaceContainerLow)
        assertReadable(c.onPrimary, c.primary)
        assertReadable(style.chips.genreContent, style.chips.genreContainer)
        assertReadable(style.toolbar.subscribeContent, style.toolbar.subscribeContainer)
        assertReadable(style.toolbar.subscribedContent, style.toolbar.subscribedContainer)
        assertReadable(style.episodeTimeColor, c.surfaceContainerLow)
        assertReadable(style.navigationColors.onSurfaceVariant, style.navigationColors.surfaceContainerHigh)
        assertReadable(style.popup.body, style.popup.popup)
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
        assertReadable(DarknetColors.Slash, DarknetColors.Card, minimum = 3f)
    }

    @Test
    fun `the card names the episode in capitals with its number above`() {
        assertEquals(DarknetTitle("Blackshades", 182), darknetTitle("182: Blackshades"))
        assertEquals(DarknetTitle("Pegasus", 181), darknetTitle("Pegasus", 181))
        assertEquals(DarknetTitle("Hacker Jim", 183), darknetTitle("Ep 183: Hacker Jim"))
        assertEquals(DarknetTitle("Mini-Stories: Vol 3", 160), darknetTitle("160: Mini-Stories: Vol 3"))
        assertEquals(DarknetTitle("A bonus", null), darknetTitle("A bonus"))
    }

    @Test
    fun `only the cloth moves, and the wave travels out from the pole`() {
        assertEquals(0f, DarknetFlagWave.push(300f, 0f, 1f).getDistance(), 0.0001f)
        val strongest = (0..259).maxOf { DarknetFlagWave.push(it.toFloat(), 1f, 0f).getDistance() }
        // At most 23 units up or down and 8 along, in the 1200-unit art.
        assertTrue(strongest <= 23.8f)
        // The wave repeats every wavelength and every period, so it never jumps.
        assertEquals(DarknetFlagWave.push(100f, 1f, 0.3f).y, DarknetFlagWave.push(100f + DarknetFlagWave.WAVELENGTH, 1f, 0.3f).y, 0.001f)
        assertEquals(DarknetFlagWave.push(100f, 1f, 0.3f).y, DarknetFlagWave.push(100f, 1f, 0.3f + DarknetFlagWave.PERIOD_SECONDS).y, 0.001f)
        // A crest a little later sits a little further from the pole.
        val crestAt = { s: Float -> (0..259).minBy { DarknetFlagWave.push(it.toFloat(), 1f, s).y } }
        assertTrue(crestAt(0.2f) > crestAt(0f))
    }

    @Test
    fun `the motion eases in from rest and then keeps going`() {
        assertEquals(0f, DarknetMotion.eased(0f), 0.0001f)
        // It starts slowly, without a jump...
        assertTrue(DarknetMotion.eased(0.05f) < 0.01f)
        // ...reaches full speed, and joins up smoothly with it.
        val ease = DarknetMotion.EASE_IN_SECONDS
        assertEquals(DarknetMotion.eased(ease - 0.001f), DarknetMotion.eased(ease), 0.002f)
        assertEquals(1f, DarknetMotion.eased(10f) - DarknetMotion.eased(9f), 0.0001f)
        // It never stops while the header is on screen.
        assertTrue(DarknetMotion.eased(600f) > DarknetMotion.eased(300f))
        assertTrue(DarknetMotion.breath(0f) == 0f && abs(DarknetMotion.breath(2.5f) - 1f) < 0.001f)
    }

    @Test
    fun `a page settles once its frames run smoothly, or after a while regardless`() {
        assertFalse(pageSettled(waited = 100, smooth = 20))
        assertFalse(pageSettled(waited = 400, smooth = 2))
        assertTrue(pageSettled(waited = 400, smooth = 6))
        assertTrue(pageSettled(waited = 1_500, smooth = 0))
    }

    @Test
    fun `the ink flecks are the same on every visit and fade at both ends of a drift`() {
        assertEquals(InkFleck.scatter(), InkFleck.scatter())
        assertEquals(14, InkFleck.scatter().size)
        InkFleck.scatter().forEach { fleck ->
            assertEquals(0f, fleck.alpha(0f), 0.001f)
            assertEquals(0.9f, fleck.alpha(0.5f), 0.001f)
            assertTrue(fleck.x in 10f..355f && fleck.y in 96f..300f)
        }
    }

    @Test
    fun `the masked host flashes before the button settles`() {
        val moment = DarknetMaskedHost
        assertTrue(moment.FLASH_AT < moment.revealDelayMillis && moment.revealDelayMillis < moment.durationMillis)
        assertEquals(0f, moment.nudge(500f).offsetY.value, 0.001f)
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
