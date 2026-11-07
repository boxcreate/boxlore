package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.conspiracy.BoardClues
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyBoardLayout
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyColors
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyItAllConnects
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyPage
import cx.aswin.boxlore.feature.info.branded.conspiracy.boardClues
import cx.aswin.boxlore.feature.info.branded.conspiracy.boardHeadline
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
    fun `Conspiracy Theories gets its evidence board page`() {
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
        assertReadable(ConspiracyColors.Ink, ConspiracyColors.Manila)
        assertReadable(ConspiracyColors.ManilaBody, ConspiracyColors.Manila)
        assertReadable(ConspiracyColors.Ink, ConspiracyColors.ManilaToggle)
        assertReadable(ConspiracyColors.PaperYellow, ConspiracyColors.Ink)
        assertReadable(ConspiracyColors.Stamp, ConspiracyColors.Manila, minimum = 3f)
        assertReadable(ConspiracyColors.ClippingInk, ConspiracyColors.Clipping)
        assertReadable(ConspiracyColors.Ink, ConspiracyColors.IndexCard)
        assertReadable(ConspiracyColors.Ink, ConspiracyColors.Sticky)
        assertReadable(ConspiracyColors.OnYellow, ConspiracyColors.Yellow)
        for (p in listOf(ConspiracyColors.Light, ConspiracyColors.Dark)) {
            assertReadable(p.textMuted, p.page)
            assertReadable(p.copyright.icon, p.page, minimum = 3f)
            assertReadable(p.copyright.body, p.copyright.popup)
            assertReadable(p.copyright.note, p.copyright.popup)
        }
    }

    @Test
    fun `each scrap shows the headline before the colon`() {
        assertEquals("Priests vs. Presidents", boardHeadline("Priests vs. Presidents: Who Really Orchestrated the Plot?"))
        assertEquals("The Donner Party Villain", boardHeadline("The Donner Party Villain: How Did Lewis Keseberg Become a Monster?"))
        // A label before the colon gives way to what follows it.
        assertEquals("The Year in Cover-Ups", boardHeadline("BEST OF 2025: The Year in Cover-Ups"))
        assertEquals("Roswell", boardHeadline("Pt: Roswell"))
        assertEquals("Was Jack the Ripper a Freemason?", boardHeadline("  Was Jack the Ripper a Freemason?  "))
        assertEquals("Panic", boardHeadline("Panic:"))
    }

    @Test
    fun `the board pins the newest episodes and the shortest of the next four`() {
        val titles = listOf(
            "The Donner Party Villain: How Did Lewis Keseberg Become a Monster?",
            "Was Jack the Ripper a Freemason?",
            "The Possessed Nuns of Loudun",
            "Panic in the Streets: The 1938 War of the Worlds",
            "Did a CIA Experiment Turn Ted Kaczynski Into the Unabomber?",
            "The Dyatlov Pass Incident",
            "Roswell",
            "The Mothman of Point Pleasant",
        )
        val clues = boardClues(titles)
        assertEquals("The Donner Party Villain", clues.clipping)
        assertEquals("Was Jack the Ripper a Freemason?", clues.indexCard)
        assertEquals("Panic in the Streets", clues.sticky)
        assertEquals(listOf("The Possessed Nuns of Loudun", "Did a CIA Experiment Turn Ted Kaczynski Into the Unabomber?", "The Dyatlov Pass Incident"), clues.peeks)
        // A new show fills what it can.
        assertEquals(BoardClues("One", null, null, emptyList()), boardClues(listOf("One")))
        assertEquals(BoardClues(null, null, null, emptyList()), boardClues(emptyList()))
    }

    @Test
    fun `the board's intro settles and its yarn rests`() {
        assertTrue(ConspiracyBoardLayout.MARKER_AT + ConspiracyBoardLayout.MARKER_MILLIS < ConspiracyBoardLayout.SWAY_AT + 400f)
        assertTrue(ConspiracyBoardLayout.END <= 9_000f)
        // Every length of yarn needs a piece that is pinned at both of its ends on the board.
        ConspiracyBoardLayout.Yarns.forEach { yarn -> assertTrue(ConspiracyBoardLayout.Pins.any { it.holds == yarn.needs }) }
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
    fun `it all connects before the button settles`() {
        val moment = ConspiracyItAllConnects
        assertEquals(0f, moment.nudge(500f).offsetY.value, 0.01f)
        assertTrue(moment.EYE_AT < moment.revealDelayMillis)
        assertTrue(moment.revealDelayMillis < moment.durationMillis)
        // Subscribed lands from 1,060ms over 180ms, by the time the button takes over.
        assertTrue(moment.durationMillis >= 1_240)
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
