package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.jre.CurtainShade
import cx.aswin.boxlore.feature.info.branded.jre.JreColors
import cx.aswin.boxlore.feature.info.branded.jre.JreEye
import cx.aswin.boxlore.feature.info.branded.jre.JrePage
import cx.aswin.boxlore.feature.info.branded.jre.JreThumbnailTitle
import cx.aswin.boxlore.feature.info.branded.jre.jreGuestLines
import cx.aswin.boxlore.feature.info.branded.jre.jreThumbnailTitle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JrePageTest {
    @Test
    fun `The Joe Rogan Experience gets its studio page, dark only`() {
        assertSame(JrePage, brandedShowPageFor(TestFixtures.podcast(id = "550168")))
        assertSame(JrePage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.megaphone.fm/GLT1412515089")))
        assertSame(JrePage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(podcastGuid = "9D783600-A77F-5C77-9042-0D9F3280465E")))
        assertTrue(JrePage.darkOnly)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable, and stays dark when the app is light`(dark: Boolean) {
        val style = JrePage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        // The colours it does not set come from a dark scheme even when the app is light.
        assertEquals(darkColorScheme().error, c.error)
        assertEquals(JreColors.Page, c.surface)
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
    fun `the card, its label and the copyright popup are readable`() {
        assertReadable(JreColors.Orange, JreColors.Label)
        assertReadable(JreColors.LabelMuted, JreColors.Label)
        assertReadable(JreColors.OnOrange, JreColors.Orange)
        assertReadable(JreColors.Ink, JreColors.Control)
        assertReadable(JreColors.Ink, JreColors.Panel)
        assertReadable(JreColors.Copyright.title, JreColors.Copyright.popup)
        assertReadable(JreColors.Copyright.body, JreColors.Copyright.popup)
        assertReadable(JreColors.Copyright.note, JreColors.Copyright.popup)
    }

    @Test
    fun `a title splits into the number and guest the pictures show`() {
        assertEquals(JreThumbnailTitle("#2573", "Legion of Skanks"), jreThumbnailTitle("#2573 - Legion of Skanks"))
        assertEquals(JreThumbnailTitle("#187", "Robert Whittaker"), jreThumbnailTitle("JRE MMA Show #187 with Robert Whittaker"))
        assertEquals(JreThumbnailTitle("#160", "Tony Ferguson"), jreThumbnailTitle("JRE MMA Show #160 - Tony Ferguson"))
        // A dash inside the guest stays with the guest.
        assertEquals(JreThumbnailTitle("#2500", "Mel Gibson - Live"), jreThumbnailTitle(" #2500 - Mel Gibson - Live "))
    }

    @Test
    fun `any other title shows whole, with no number`() {
        assertEquals(JreThumbnailTitle(null, "Fight Companion - October 26, 2026"), jreThumbnailTitle("Fight Companion - October 26, 2026"))
        assertNull(jreThumbnailTitle("#2573 Legion of Skanks").number)
    }

    @Test
    fun `a long guest name breaks where the two lines balance`() {
        assertEquals("Legion\nof Skanks", jreGuestLines("Legion of Skanks"))
        assertEquals("Dr. Andrew\nHuberman", jreGuestLines("Dr. Andrew Huberman"))
        assertEquals("Colum\nTyrrell", jreGuestLines("Colum Tyrrell"))
        assertEquals("Theo Von", jreGuestLines("Theo Von"))
        assertEquals("Supercalifragilistic", jreGuestLines("Supercalifragilistic"))
    }

    @Test
    fun `the eye opens, pulses once, closes and leaves Subscribed`() {
        assertNull(JreEye.lid(100f), "No eye before the button goes dark")
        assertTrue(JreEye.lid(110f + 260f * 0.7f)!! > 1.05f, "The eye overshoots as it opens")
        assertEquals(1f, JreEye.lid(700f)!!, 0.001f)
        assertEquals(0.06f, JreEye.lid(930f)!!, 0.001f)
        assertEquals(1f, JreEye.pulse(JreEye.PULSE_MILLIS - 1f), 0.001f)
        assertEquals(1.16f, JreEye.pulse(JreEye.PULSE_MILLIS + 170f * 0.4f), 0.001f)
        assertEquals(1f, JreEye.pulse(730f), 0.001f)
        assertEquals(0f, JreEye.glow(JreEye.PULSE_MILLIS + 300f), 0.001f)
        assertTrue(JreEye.REVEAL_MILLIS < JreEye.DURATION_MILLIS)
        // Everything has settled before the button takes over again.
        val end = JreEye.DURATION_MILLIS.toFloat()
        assertTrue(JreEye.baseDark(end))
        assertEquals(1f, JreEye.settle(end), 0.001f)
        assertEquals(1f, JreEye.subscribedLabel(end).first, 0.001f)
        assertEquals(1f, JreEye.subscribedLabel(end).second, 0.001f)
        assertEquals(0f, JreEye.subscribeLabel(end).first, 0.001f)
        assertTrue(1_160 <= JreEye.DURATION_MILLIS)
    }

    @Test
    fun `every dot spreads to its place, then falls back into the eye`() {
        for ((dots, first) in listOf(JreEye.ButtonDots to JreEye.BUTTON_FIRST_DOT, JreEye.BadgeDots to JreEye.BADGE_FIRST_DOT)) {
            for (dot in dots) {
                assertEquals(0f, JreEye.dot(first - 1f, dot.index, first).third, 0.001f)
                val (out, scale, alpha) = JreEye.dot(819f, dot.index, first)
                assertEquals(1f, out, 0.001f, "Dot ${dot.index} reaches its place before falling back")
                assertEquals(1f, scale, 0.001f)
                assertEquals(1f, alpha, 0.001f)
                assertEquals(0f, JreEye.dot(1_040f, dot.index, first).third, 0.001f, "Dot ${dot.index} is back in the eye")
            }
        }
        // The side rays run out across the button; the badge's stay near its own eye.
        assertEquals(97f, JreEye.ButtonDots.maxOf { it.target.x }, 0.01f)
        assertTrue(JreEye.BadgeDots.all { it.target.getDistance() <= 28.01f })
    }

    @Test
    fun `the guest's name stands out on the curtain`() {
        // The worst spot: the brightest fold (#8B161B) with the stage light at full strength. A title
        // with no number puts the name's top at 39% of the picture; below the number it starts near 58%.
        val lit = Color(0xFF8B161B).compositeWith(JreColors.Glow.copy(alpha = 0.2f))
        assertReadable(JreColors.Orange, lit.shadedAt(0.39f), minimum = 3f)
        assertReadable(JreColors.Orange, lit.shadedAt(0.58f))
        assertReadable(Color.White, lit.shadedAt(0.39f))
    }

    private fun Color.shadedAt(at: Float): Color {
        val (from, to) = CurtainShade.zipWithNext().first { (a, b) -> at in a.first..b.first }
        val alpha = from.second + (to.second - from.second) * (at - from.first) / (to.first - from.first)
        return compositeWith(Color.Black.copy(alpha = alpha))
    }

    private fun Color.compositeWith(top: Color) = top.compositeOver(this)

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
