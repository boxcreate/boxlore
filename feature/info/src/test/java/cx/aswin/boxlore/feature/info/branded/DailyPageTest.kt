package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.daily.DailyColors
import cx.aswin.boxlore.feature.info.branded.daily.DailyDawn
import cx.aswin.boxlore.feature.info.branded.daily.DailyDaybreak
import cx.aswin.boxlore.feature.info.branded.daily.DailyHorizon
import cx.aswin.boxlore.feature.info.branded.daily.DailyPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DailyPageTest {
    @Test
    fun `The Daily gets its first light page in both themes`() {
        assertSame(DailyPage, brandedShowPageFor(TestFixtures.podcast(id = "743229")))
        assertSame(DailyPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.simplecast.com/54nAGcIl")))
        assertSame(DailyPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(podcastGuid = "304B84F0-07B0-5265-B6B7-DA5CF5AEB56E")))
        assertFalse(DailyPage.darkOnly)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable in both editions`(dark: Boolean) {
        val style = DailyPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
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
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerHigh))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `the accent, the notes and the popup are readable`(dark: Boolean) {
        val p = if (dark) DailyColors.Dark else DailyColors.Light
        assertReadable(p.accent, p.page)
        assertReadable(p.onAccent, p.accent)
        assertReadable(p.sub, p.page)
        assertReadable(p.copyright.body, p.copyright.popup)
        assertReadable(p.copyright.note, p.copyright.popup)
        // The cadence chip's blue icon is a graphic on its tonal chip.
        assertReadable(p.accent, p.control, minimum = 3f)
    }

    @Test
    fun `the sky keeps white icons over it by day and the page's own by night`() {
        assertEquals(Color.White, DailyPage.style(lightColorScheme(), dark = false).headerContent)
        assertNull(DailyPage.style(darkColorScheme(), dark = true).headerContent)
    }

    @Test
    fun `the white masthead, wordmark and copyright button are readable on the sky`() {
        val apex = DailyHorizon.Apex.value
        // From the masthead row's top to the wordmark's foot, every 2dp: logos and the © icon need 3:1.
        var y = 0f
        while (y <= DailyHorizon.LockupHeight.value) {
            assertReadable(Color.White, DailyHorizon.skyAt(apex - y), minimum = 3f)
            y += 2f
        }
        // The sun peeks over the horizon well clear of the wordmark.
        assertTrue(apex - DailyHorizon.SUN_PEEK - DailyHorizon.LockupHeight.value >= 16f)
    }

    @Test
    fun `the horizon curves as on the board on phones and stays gentle on wide windows`() {
        assertEquals(26f, DailyHorizon.drop(369f), 0.01f)
        assertEquals(26f, DailyHorizon.depth(369f, 369f / 2f), 0.01f)
        assertEquals(20f, DailyHorizon.drop(240f), 0.01f)
        assertEquals(40f, DailyHorizon.drop(840f), 0.01f)
        assertEquals(40f, DailyHorizon.drop(1280f), 0.01f)
        // A wider window has a flatter curve: the drop grows far slower than the width.
        assertTrue(DailyHorizon.radius(840f) > DailyHorizon.radius(369f) * 2)
    }

    @ParameterizedTest
    @ValueSource(floats = [280f, 320f, 360f, 369f, 412f, 480f, 600f, 673f, 840f, 1024f, 1280f, 1600f])
    fun `the description and chips start below the curve at every width`(width: Float) {
        // The description's text starts 28dp in from each edge; the curve must have dropped away above it.
        val underText = DailyHorizon.Apex.value + DailyHorizon.depth(width, width / 2f - 28f)
        assertTrue(underText + 4f <= DailyHorizon.PageTop.value, "curve at $underText for width $width")
    }

    @Test
    fun `daybreak rises, taps as the sun clears the horizon, and lands on Subscribed`() {
        assertEquals(1f, DailyDawn.subscribeLabel(0f).first, 0.001f)
        assertEquals(0f, DailyDawn.subscribeLabel(140f).first, 0.001f)
        assertEquals(0f, DailyDawn.sky(60f), 0.001f)
        assertEquals(1f, DailyDawn.sky(700f), 0.001f)
        // At rest the sun and its glow sit below the button, so the first frame is the plain button.
        val glowTop = DailyDawn.sunTop(0f) + DailyDawn.SUN_DIAMETER / 2 - DailyDawn.SUN_GLOW_RADIUS
        assertTrue(glowTop >= DailyDawn.BUTTON_HEIGHT, "glow top $glowTop")
        // The one tap comes just as the sun's disc clears the bottom edge.
        val clear = DailyDawn.SUN_CLEAR_MILLIS.toFloat()
        assertTrue(DailyDawn.sunTop(clear) + DailyDawn.SUN_DIAMETER <= DailyDawn.BUTTON_HEIGHT)
        assertTrue(DailyDawn.sunTop(clear - 20f) + DailyDawn.SUN_DIAMETER > DailyDawn.BUTTON_HEIGHT)
        // Daylight starts after the sun is up and is full, with Subscribed in, by the end.
        val end = DailyDawn.DURATION_MILLIS.toFloat()
        assertEquals(0f, DailyDawn.daylight(800f), 0.001f)
        assertEquals(1f, DailyDawn.daylight(end), 0.001f)
        assertEquals(1f, DailyDawn.subscribedLabel(end), 0.001f)
        assertEquals(0f, DailyDawn.sunAlpha(end), 0.001f)
        assertTrue(DailyDawn.REVEAL_MILLIS < DailyDawn.DURATION_MILLIS)
        assertSame(DailyDaybreak, DailyPage.subscribeMoment)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
