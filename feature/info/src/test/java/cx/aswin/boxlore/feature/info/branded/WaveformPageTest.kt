package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.waveform.LogoGroup
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformBand
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformButtonMotion
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformColors
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformLogoIntro
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformLogoShape
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformMotion
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WaveformPageTest {
    @Test
    fun `Waveform gets its Signal page`() {
        assertSame(WaveformPage, brandedShowPageFor(TestFixtures.podcast(id = "947138")))
        assertSame(WaveformPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.megaphone.fm/STU4418364045")))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `the page stays dark and readable in both modes`(dark: Boolean) {
        assertTrue(WaveformPage.darkOnly)
        val style = WaveformPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        assertEquals(WaveformColors.Page, c.surface)
        // Light mode's own scheme never leaks into the rest of the dark page.
        assertTrue(c.inverseSurface.luminance() > 0.5f && c.error.luminance() > 0.2f)
        assertReadable(c.onSurface, c.surface)
        assertReadable(c.onSurfaceVariant, c.surface)
        assertReadable(c.onSurface, c.surfaceContainerLow)
        assertReadable(c.onPrimary, c.primary)
        assertReadable(style.chips.content, c.surface)
        assertReadable(style.chips.genreContent, style.chips.genreContainer)
        assertReadable(style.toolbar.subscribeContent, style.toolbar.subscribeContainer)
        assertReadable(style.toolbar.subscribedContent, style.toolbar.subscribedContainer)
        assertReadable(style.toolbar.toggleCheckedContent, style.toolbar.toggleCheckedContainer)
        assertReadable(style.toolbar.actionContent, style.toolbar.actionContainer)
        assertReadable(style.episodeTimeColor, c.surfaceContainerLow)
        assertReadable(style.navigationColors.onSurfaceVariant, style.navigationColors.surfaceContainerHigh)
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
    }

    @Test
    fun `the header's small text is readable`() {
        assertReadable(WaveformColors.Subtitle, WaveformColors.Page)
        assertReadable(WaveformColors.Description, WaveformColors.Page)
        assertReadable(WaveformColors.Publisher, WaveformColors.Page)
        assertReadable(WaveformColors.RedText, WaveformColors.Card)
        assertReadable(WaveformColors.Muted, WaveformColors.Card)
        assertReadable(WaveformColors.Description, WaveformColors.Card)
        assertReadable(WaveformColors.Copyright.body, WaveformColors.Copyright.popup)
        assertReadable(WaveformColors.Copyright.note, WaveformColors.Copyright.popup)
    }

    @Test
    fun `the logo keeps its traced centre and reaches both edges of any window`() {
        assertEquals(52, WaveformLogoShape.Bars.size)
        assertEquals(6, WaveformLogoShape.Bars.count { it.group == LogoGroup.OverBlock })
        assertEquals(6, WaveformLogoShape.Bars.count { it.group == LogoGroup.UnderBlock })
        assertSame(WaveformLogoShape.Bars, WaveformLogoShape.barsFor(369f))
        val tablet = WaveformLogoShape.barsFor(900f)
        val left = (900f - WaveformLogoShape.WIDTH) / 2f
        assertTrue(tablet.minOf { it.x } + left < 7f, "Bars reach the left edge")
        assertTrue(tablet.maxOf { it.x } + left + WaveformLogoShape.BAR_WIDTH > 900f - 7f, "Bars reach the right edge")
        // Every bar, however far out, has finished its burst before the intro ends.
        assertTrue(tablet.all { it.introDelay + 430 <= WaveformMotion.INTRO_MILLIS })
    }

    @Test
    fun `the intro opens on a flat line and ends on the logo at rest`() {
        val bars = WaveformLogoShape.barsFor(1_280f)
        assertTrue(bars.all { WaveformMotion.introBarScale(0f, it.introDelay) == 0.04f })
        assertEquals(0f, WaveformMotion.introBlockScale(0f))
        val end = WaveformMotion.INTRO_MILLIS.toFloat()
        assertTrue(bars.all { WaveformMotion.introBarScale(end, it.introDelay) == 1f })
        assertEquals(1f, WaveformMotion.introBlockScale(end))
        assertEquals(0f, WaveformMotion.introFlash(end))
        assertEquals(0f to 0f, WaveformMotion.introJitter(end))
        assertEquals(1f, WaveformMotion.introBeat(end))
        assertEquals(-1, WaveformMotion.glitchFrame(end))
    }

    @Test
    fun `bars burst from the centre outwards and overshoot before settling`() {
        val centre = WaveformLogoShape.Bars.minBy { it.introDelay }
        val edge = WaveformLogoShape.Bars.maxBy { it.introDelay }
        assertTrue(centre.group != LogoGroup.Plain && edge.x < 10f)
        assertEquals(1.32f, WaveformMotion.riseScale(0.68f), 0.001f)
        val early = centre.introDelay + 100f
        assertTrue(WaveformMotion.introBarScale(early, centre.introDelay) > 0.5f)
        assertEquals(0.04f, WaveformMotion.introBarScale(early, edge.introDelay), 0.001f)
    }

    @Test
    fun `the glitch runs four frames between 560 and 800ms`() {
        assertEquals(-1, WaveformMotion.glitchFrame(559f))
        assertEquals(0, WaveformMotion.glitchFrame(560f))
        assertEquals(3, WaveformMotion.glitchFrame(799f))
        assertEquals(-1, WaveformMotion.glitchFrame(800f))
    }

    @Test
    fun `playing bars breathe within their range, each group on its own beat`() {
        val scales = (0L..3_000L step 40L).flatMap { t -> (0..3).map { WaveformMotion.liveScale(t, it) } }
        assertTrue(scales.all { it in 0.45f..1.12f })
        assertTrue((0..3).map { WaveformMotion.liveScale(400L, it) }.distinct().size == 4)
    }

    @Test
    fun `the spotlight band lights its bars up to where you are, with no marker`() {
        val width = 307f
        val barWidth = WaveformBand.BAR_WIDTH
        val centres = WaveformBand.Heights.indices.map { WaveformBand.barLeft(it, width, barWidth) + barWidth / 2f }
        fun played(progress: Float) = centres.map { WaveformBand.isPlayed(it, width, progress) }
        assertTrue(played(0f).none { it }, "A new episode shows no played bars")
        assertTrue(played(1f).all { it }, "A played episode shows every bar lit")
        val partway = played(0.34f)
        val lit = partway.count { it }
        assertEquals(14, lit)
        // Lit bars run from the left edge with no gap, so only colour marks the listening point.
        assertTrue(partway.take(lit).all { it } && partway.drop(lit).none { it })
        assertEquals(0f, WaveformBand.barLeft(0, width, barWidth))
        assertEquals(width - barWidth, WaveformBand.barLeft(WaveformBand.Heights.lastIndex, width, barWidth), 0.001f)
        assertFalse(WaveformBand.isPlayed(1f, 0f, 1f), "A band with no width draws nothing as played")
    }

    @Test
    fun `the band's tallest breathing bar stays inside it`() {
        val tallest = WaveformBand.Heights.max() * WaveformBand.HEIGHT_SCALE * 1.12f
        assertTrue(tallest < WaveformBand.HEIGHT - 1, "$tallest dp must fit above the band's 1dp hairline")
        assertReadable(WaveformColors.Text, WaveformColors.Band)
        assertReadable(WaveformColors.BandTrack, WaveformColors.Band, minimum = 1.5f)
    }

    @Test
    fun `the button lands on Subscribed with nothing left over`() {
        assertTrue(WaveformLogoIntro.revealDelayMillis < WaveformLogoIntro.durationMillis)
        assertTrue(WaveformButtonMotion.BUTTON_SWAP_MILLIS <= WaveformLogoIntro.revealDelayMillis)
        assertEquals(1f, WaveformButtonMotion.subscribeLabelOut(120f))
        assertEquals(0f, WaveformButtonMotion.buttonWaveAlpha(99f))
        assertEquals(1f, WaveformButtonMotion.buttonWaveAlpha(400f))
        assertEquals(0f, WaveformButtonMotion.buttonWaveAlpha(900f))
        val hidden = WaveformButtonMotion.subscribedLabel(799f)
        assertEquals(0f, hidden.alpha)
        val landed = WaveformButtonMotion.subscribedLabel(WaveformLogoIntro.durationMillis.toFloat())
        assertEquals(1f, landed.alpha)
        assertEquals(0f, landed.shift)
        assertEquals(0f, landed.splitAlpha)
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
