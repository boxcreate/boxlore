package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoColors
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoNoise
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoPage
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoTape
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RottenMangoPageTest {
    @Test
    fun `Rotten Mango gets Her room, dark only`() {
        assertSame(RottenMangoPage, brandedShowPageFor(TestFixtures.podcast(id = "490022")))
        assertSame(RottenMangoPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.simplecast.com/6Qp23t6h")))
        assertSame(RottenMangoPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(podcastGuid = "DA0493CA-8CE4-5BAB-BE6E-37B7B0B945F9")))
        assertTrue(RottenMangoPage.darkOnly)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable, and stays dark when the app is light`(dark: Boolean) {
        val style = RottenMangoPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        // The colours it does not set come from a dark scheme even when the app is light.
        assertEquals(darkColorScheme().error, c.error)
        assertEquals(RottenMangoColors.Page, c.surface)
        assertReadable(c.onSurface, c.surface)
        assertReadable(c.onSurfaceVariant, c.surface)
        assertReadable(c.onSurface, c.surfaceContainerLow)
        assertReadable(c.onSurfaceVariant, c.surfaceContainerLow)
        assertReadable(c.onPrimary, c.primary)
        assertReadable(style.chips.content, style.chips.container)
        assertReadable(style.chips.genreContent, style.chips.genreContainer)
        assertReadable(style.toolbar.subscribeContent, style.toolbar.subscribeContainer)
        assertReadable(style.toolbar.subscribedContent, style.toolbar.subscribedContainer)
        assertReadable(style.toolbar.toggleCheckedContent, style.toolbar.toggleCheckedContainer)
        assertReadable(style.toolbar.actionContent, style.toolbar.actionContainer)
        assertReadable(style.episodeTimeColor, c.surfaceContainerLow)
        assertReadable(style.navigationColors.onSurfaceVariant, style.navigationColors.surfaceContainerHigh)
        // Cards and chips keep full-strength text by default.
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerHigh))
    }

    @Test
    fun `the frame, its plate, the tag and the popup are readable`() {
        // The frame's inside is darkest at its edges and lightest at its glow; text must hold on the glow.
        val glow = Color(0xFF221010)
        assertReadable(RottenMangoColors.FrameTitle, glow)
        assertReadable(RottenMangoColors.FrameLabel, glow)
        assertReadable(RottenMangoColors.FrameRed, glow)
        assertReadable(RottenMangoColors.PlateText, RottenMangoColors.PlateBrass)
        assertReadable(RottenMangoColors.Ink, RottenMangoColors.Page)
        assertReadable(RottenMangoColors.Copyright.title, RottenMangoColors.Copyright.popup)
        assertReadable(RottenMangoColors.Copyright.body, RottenMangoColors.Copyright.popup)
        assertReadable(RottenMangoColors.Copyright.note, RottenMangoColors.Copyright.popup)
    }

    @Test
    fun `the set switches on from a bright line and off to one`() {
        assertNull(RottenMangoTape.screen(39f), "Nothing before the button goes black")
        val on = RottenMangoTape.screen(40f)!!
        assertEquals(0.03f, on.scaleY, 0.001f)
        assertEquals(4f, on.brightness, 0.001f)
        val full = RottenMangoTape.screen(190f)!!
        assertEquals(1f, full.scaleY, 0.001f)
        assertEquals(1f, full.brightness, 0.001f)
        val line = RottenMangoTape.screen(900f + 160f * 0.45f)!!
        assertEquals(0.03f, line.scaleY, 0.001f)
        assertEquals(1f, line.scaleX, 0.001f)
        assertEquals(0f, RottenMangoTape.screen(1_059.9f)!!.alpha, 0.01f)
        assertNull(RottenMangoTape.screen(1_060f))
        assertTrue(RottenMangoTape.baseBlack(40f))
    }

    @Test
    fun `bada bing plays, static cuts in, then bada boom hits once`() {
        assertNull(RottenMangoTape.bing(189f))
        assertNotNull(RottenMangoTape.bing(190f))
        assertNull(RottenMangoTape.bing(540f), "BADA BING is gone as the static bursts")
        assertTrue(RottenMangoTape.isStatic(540f) && RottenMangoTape.isStatic(609f) && !RottenMangoTape.isStatic(610f))
        assertEquals(0.92f, RottenMangoTape.grainAlpha(560f), 0.001f)
        assertEquals(0.26f, RottenMangoTape.grainAlpha(700f), 0.001f)
        assertNull(RottenMangoTape.boom(609f))
        val slam = RottenMangoTape.boom(RottenMangoTape.BOOM_MILLIS.toFloat())!!
        assertEquals(1.4f, slam.scale, 0.001f)
        assertEquals(8f, slam.split, 0.001f)
        val held = RottenMangoTape.boom(750f)!!
        assertEquals(1f, held.scale, 0.001f)
        assertEquals(2f, held.split, 0.001f)
        assertEquals(-7f, RottenMangoTape.hold(RottenMangoTape.BOOM_MILLIS + 40f), 0.001f)
        assertEquals(0f, RottenMangoTape.hold(RottenMangoTape.BOOM_MILLIS + 160f), 0.001f)
        assertEquals(1, RottenMangoTape.trackingLines(300f).size)
        assertEquals(0, RottenMangoTape.trackingLines(600f).size)
    }

    @Test
    fun `it ends on Subscribed with nothing left over`() {
        val end = RottenMangoTape.DURATION_MILLIS.toFloat()
        assertEquals(0f, RottenMangoTape.subscribeLabel(60f), 0.001f)
        assertNull(RottenMangoTape.screen(end))
        assertEquals(1f, RottenMangoTape.settle(end), 0.001f)
        assertEquals(1f, RottenMangoTape.subscribedLabel(end).first, 0.001f)
        assertEquals(1f, RottenMangoTape.subscribedLabel(end).second, 0.001f)
        assertTrue(RottenMangoTape.REVEAL_MILLIS < RottenMangoTape.DURATION_MILLIS)
    }

    @Test
    fun `the grain is the same every run`() {
        val grain = RottenMangoNoise.pixels()
        assertArrayEquals(grain, RottenMangoNoise.pixels())
        assertEquals(RottenMangoNoise.SIZE * RottenMangoNoise.SIZE, grain.size)
        val alphas = grain.map { it ushr 24 }
        assertTrue(alphas.all { it in 89..166 }, "Each speck is partly see-through")
        assertTrue(grain.any { it and 0xFF == 0 } && grain.any { it and 0xFF > 200 }, "The boost reaches black and near white")
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
