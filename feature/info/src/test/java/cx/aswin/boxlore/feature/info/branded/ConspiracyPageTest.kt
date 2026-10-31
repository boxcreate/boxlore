package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyColors
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyPage
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyTypedAndFiled
import cx.aswin.boxlore.feature.info.branded.conspiracy.filedDate
import cx.aswin.boxlore.feature.info.branded.conspiracy.typedDetails
import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConspiracyPageTest {
    @Test
    fun `Conspiracy Theories gets its case file page`() {
        assertSame(ConspiracyPage, brandedShowPageFor(TestFixtures.podcast(id = "9926")))
        assertSame(ConspiracyPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.megaphone.fm/END5064265457")))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable in both themes`(dark: Boolean) {
        val style = ConspiracyPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        assertReadable(c.onSurface, c.surface)
        assertReadable(c.onSurfaceVariant, c.surface)
        assertReadable(c.onSurface, c.surfaceContainerLow)
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
    fun `the paper's type is readable`() {
        assertReadable(ConspiracyColors.Ink, ConspiracyColors.Mustard)
        assertReadable(ConspiracyColors.StripText, ConspiracyColors.Strip)
        assertReadable(ConspiracyColors.CardText, ConspiracyColors.Mustard)
        assertReadable(ConspiracyColors.PaperYellow, ConspiracyColors.Ink)
        assertReadable(ConspiracyColors.Stamp, ConspiracyColors.Mustard, minimum = 3f)
    }

    @Test
    fun `details are typed in capitals with the length in minutes`() {
        assertEquals("2D AGO · 55 MIN", typedDetails("2d ago", 55 * 60))
        assertEquals("3H AGO · 1 HR 5 MIN", typedDetails("3h ago", 65 * 60))
        assertEquals("1W AGO", typedDetails("1w ago", 30))
        assertEquals("", typedDetails("", 0))
    }

    @Test
    fun `the folder tab shows the day it was filed`() {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("28 OCT 2026", filedDate(1_793_188_800L, Locale.UK))
            assertEquals("", filedDate(0L, Locale.UK))
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun `each strike jolts the button, then it is still`() {
        assertEquals(0f, ConspiracyTypedAndFiled.nudge(100f).offsetY.value, 0.01f)
        assertEquals(1.5f, ConspiracyTypedAndFiled.nudge(150f + 35f).offsetY.value, 0.05f)
        assertEquals(1.5f, ConspiracyTypedAndFiled.nudge(525f + 35f).offsetY.value, 0.05f)
        assertEquals(0f, ConspiracyTypedAndFiled.nudge(700f).offsetY.value, 0.01f)
        assertTrue(ConspiracyTypedAndFiled.revealDelayMillis < ConspiracyTypedAndFiled.durationMillis)
        // "Subscribed" finishes sliding in at 1,160ms, just before the button takes over.
        assertTrue(ConspiracyTypedAndFiled.durationMillis >= 1_160)
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
