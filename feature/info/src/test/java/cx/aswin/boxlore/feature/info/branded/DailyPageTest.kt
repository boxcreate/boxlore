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
import cx.aswin.boxlore.feature.info.branded.daily.DailyPage
import cx.aswin.boxlore.feature.info.branded.daily.millisUntilNextDay
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DailyPageTest {
    @Test
    fun `The Daily gets its front page in both themes`() {
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
    fun `the kicker, the popup and the rules are readable`(dark: Boolean) {
        val p = if (dark) DailyColors.Dark else DailyColors.Light
        assertReadable(p.kicker, p.page)
        assertReadable(p.sub, p.page)
        assertReadable(p.copyright.body, p.copyright.popup)
        assertReadable(p.copyright.note, p.copyright.popup)
        // The rules are graphics: the double rule at 3:1, the fine ones around the buttons only have to show.
        assertReadable(p.ink, p.page, minimum = 3f)
        assertTrue(contrast(p.soft, p.page) > 1.1f)
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

    @Test
    fun `the dateline turns over at the next midnight, through clock changes`() {
        val london = ZoneId.of("Europe/London")
        assertEquals(30_000L, millisUntilNextDay(ZonedDateTime.of(2026, 10, 31, 23, 59, 30, 0, london)))
        assertEquals(12 * 3_600_000L, millisUntilNextDay(ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, london)))
        // New York's clocks go back an hour early on 1 November 2026: that day runs 25 hours.
        val newYork = ZoneId.of("America/New_York")
        assertEquals(24 * 3_600_000L + 1_800_000L, millisUntilNextDay(ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, newYork)))
        assertEquals(1L, millisUntilNextDay(ZonedDateTime.of(2026, 11, 1, 23, 59, 59, 999_999_999, newYork)))
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
