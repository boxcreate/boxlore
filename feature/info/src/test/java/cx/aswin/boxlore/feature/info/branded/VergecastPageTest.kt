package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastColors
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class VergecastPageTest {
    @Test
    fun `The Vergecast gets its branded page`() {
        assertSame(VergecastPage, brandedShowPageFor(TestFixtures.podcast(id = "415434")))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable in both themes`(dark: Boolean) {
        val style = style(dark)
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
    }

    @Test
    fun `the latest episode card is readable`() {
        assertReadable(Color.White, VergecastColors.Ultraviolet)
        assertReadable(VergecastColors.CardText, VergecastColors.Ultraviolet)
        assertReadable(VergecastColors.Lime, VergecastColors.Ultraviolet)
        assertReadable(VergecastColors.Ink, VergecastColors.Lime)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `cards and chips keep full-strength text by default`(dark: Boolean) {
        // Regression: a card colour shared with surfaceVariant made Material pick the muted text colour.
        val c = style(dark).colors
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerHigh))
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerHighest))
    }

    @Test
    fun `lime switches to ultraviolet on the light page`() {
        assertEquals(VergecastColors.Lime, style(dark = true).episodeTimeColor)
        assertEquals(VergecastColors.Ultraviolet, style(dark = false).episodeTimeColor)
    }

    private fun style(dark: Boolean) = VergecastPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
