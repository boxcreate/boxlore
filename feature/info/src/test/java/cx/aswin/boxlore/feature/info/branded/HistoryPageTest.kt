package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.history.HistoryColors
import cx.aswin.boxlore.feature.info.branded.history.HistoryPage
import cx.aswin.boxlore.feature.info.branded.history.HistoryThroughTheAges
import cx.aswin.boxlore.feature.info.branded.history.HistoryTitle
import cx.aswin.boxlore.feature.info.branded.history.HistoryWall
import cx.aswin.boxlore.feature.info.branded.history.historyTitle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HistoryPageTest {
    @Test
    fun `The Rest Is History gets its gallery page`() {
        assertSame(HistoryPage, brandedShowPageFor(TestFixtures.podcast(id = "1365765")))
        assertSame(HistoryPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.megaphone.fm/GLT4787413333/")))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable in both themes`(dark: Boolean) {
        val style = HistoryPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        assertReadable(c.onSurface, c.surface)
        assertReadable(c.onSurfaceVariant, c.surface)
        assertReadable(c.onSurface, c.surfaceContainerLow)
        assertReadable(c.onPrimary, c.primary)
        assertReadable(style.chips.content, style.chips.container)
        assertReadable(style.chips.genreContent, style.chips.genreContainer)
        assertReadable(style.toolbar.subscribeContent, style.toolbar.subscribeContainer)
        assertReadable(style.toolbar.subscribedContent, style.toolbar.subscribedContainer)
        assertReadable(style.toolbar.actionContent, style.toolbar.actionContainer)
        assertReadable(style.episodeTimeColor, c.surfaceContainerLow)
        assertReadable(style.navigationColors.onSurfaceVariant, style.navigationColors.surfaceContainerHigh)
        assertReadable(style.popup.body, style.popup.popup)
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
        // The velvet wall stays dark by day, so only the light theme lifts the top bar's icons over it.
        assertEquals(if (dark) null else HistoryColors.OnWall, style.headerContent)
    }

    @Test
    fun `the card's ivory text reads on the dimmed velvet`() {
        // The velvet at 78% brightness is about this claret at its lightest.
        val velvet = Color(0xFF8E1F45)
        assertReadable(HistoryColors.CardTitle, velvet)
        assertReadable(HistoryColors.CardText, velvet)
        assertReadable(HistoryColors.CardKicker, velvet, minimum = 3f)
        assertReadable(HistoryColors.OnCardToggle, HistoryColors.CardToggle)
        assertReadable(HistoryColors.PlateInk, Color(0xFFBE9B62))
    }

    @Test
    fun `titles split into the episode's name, its series and part, and its number`() {
        assertEquals(
            HistoryTitle("Revenge of the Red Army", "Revolution in the Soviet Empire, Part 2", 720),
            historyTitle("720. Revolution in the Soviet Empire: Revenge of the Red Army (Part 2) "),
        )
        assertEquals(HistoryTitle("The Hungarian Uprising", "Revolution in the Soviet Empire, Part 1", 719), historyTitle("Revolution in the Soviet Empire: The Hungarian Uprising (Part 1)", 719))
        assertEquals(HistoryTitle("Napoleon's Last Stand", null, 650), historyTitle("650. Napoleon's Last Stand"))
        assertEquals(HistoryTitle("Bonus episode", "part 3", null), historyTitle("Bonus episode (part  3)"))
        assertEquals(HistoryTitle("The Year in History", null, null), historyTitle("The Year in History"))
        assertNull(historyTitle("Q&A").series)
    }

    @Test
    fun `the lights come on one by one, centre first, and settle lit`() {
        // In the dark before the lights.
        assertEquals(HistoryWall.WALL_DARK, HistoryWall.wallBrightness(0f), 0.001f)
        HistoryWall.Frames.indices.forEach { assertEquals(HistoryWall.FRAME_DARK, HistoryWall.frameBrightness(it, 0f), 0.001f) }
        // The centre frame's lamp catches with a flicker: past full, a dip, then on.
        val centre = HistoryWall.Frames[0].lightsAt
        val flicker = (0..280).map { HistoryWall.frameBrightness(0, centre + it) }
        assertTrue(flicker.max() > 1f)
        assertTrue(flicker.drop(80).any { it < 0.7f })
        // Each light comes on after the one before it, so the order reads as switching on, not loading.
        val starts = HistoryWall.Frames.map { it.lightsAt }
        assertEquals(starts.sorted(), starts)
        assertTrue(starts.zipWithNext().all { (a, b) -> b == a || b - a >= 100f })
        assertTrue(HistoryWall.BUSTS_AT > starts.last() && HistoryWall.LOGO_AT > HistoryWall.BUSTS_AT && HistoryWall.GLINT_AT > HistoryWall.LOGO_AT)
        // A spotlight flares as it comes on, then settles lower but stays on.
        assertTrue(HistoryWall.spotlight(0, centre + 126f) > HistoryWall.spotlight(0, HistoryWall.LIT))
        assertTrue(HistoryWall.spotlight(0, HistoryWall.LIT) > 0f)
        // Fully lit at the end, with the glint gone.
        assertEquals(0.95f, HistoryWall.wallBrightness(HistoryWall.LIT), 0.001f)
        HistoryWall.Frames.indices.forEach { assertEquals(1f, HistoryWall.frameBrightness(it, HistoryWall.LIT), 0.001f) }
        assertEquals(1f, HistoryWall.bustBrightness(HistoryWall.LIT), 0.001f)
        assertEquals(1f, HistoryWall.logoAlpha(HistoryWall.LIT), 0.001f)
        assertTrue(HistoryWall.glint(HistoryWall.LIT) < 0f && HistoryWall.glint(HistoryWall.GLINT_AT + 100f) in 0f..1f)
        assertTrue(HistoryWall.LIT <= 2_000f)
    }

    @Test
    fun `the strip starts off the button and lands the newest frame in the middle`() {
        val moment = HistoryThroughTheAges
        for (width in listOf(235f, 300f, 520f)) {
            for (frames in 1..9) {
                assertTrue(moment.stripLeft(0f, frames, width) >= width, "starts off the right edge")
                val left = moment.stripLeft(moment.RUN_AT + moment.RUN_MILLIS, frames, width)
                val newestCentre = left + (frames - 1) * (moment.FRAME + moment.GAP) + moment.FRAME / 2f
                assertEquals(width / 2f, newestCentre, 0.01f)
            }
        }
        assertTrue(moment.LIGHT_AT < moment.revealDelayMillis && moment.revealDelayMillis < moment.durationMillis)
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
