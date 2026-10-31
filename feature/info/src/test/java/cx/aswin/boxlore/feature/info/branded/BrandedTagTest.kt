package cx.aswin.boxlore.feature.info.branded

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyColors
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkieColors
import cx.aswin.boxlore.feature.info.branded.daily.DailyColors
import cx.aswin.boxlore.feature.info.branded.jre.JreColors
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoColors
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastColors
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformColors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class BrandedTagTest {
    /** Each show's tag: its background, boxlore's mark in the show's colour, and the "Made for:" text. */
    data class Tag(val show: String, val background: Color, val mark: Color, val text: Color) {
        override fun toString() = show
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tags")
    fun `the mark stands out and the words are readable on every tag`(tag: Tag) {
        // The mark is a graphic, 3:1; "Made for:" is text, 4.5:1.
        assertContrast(tag.mark, tag.background, 3f, "${tag.show} mark")
        assertContrast(tag.text, tag.background, 4.5f, "${tag.show} words")
    }

    @Test
    fun `the hole sits at the point's base on the start side, clear of the mark`() {
        val size = Size(120f, 28f)
        assertEquals(Offset(9f, 14f), brandedTagHoleCentre(size, LayoutDirection.Ltr, point = 9f))
        assertEquals(Offset(111f, 14f), brandedTagHoleCentre(size, LayoutDirection.Rtl, point = 9f))
        // The mark starts at least 3dp past the hole's far edge.
        assertTrue(BrandedTagStartPadding >= BrandedTagPoint + BrandedTagHoleRadius + 3.dp)
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float, what: String) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (dark + 0.05f)
        assertTrue(ratio >= minimum, "$what: contrast $ratio < $minimum")
    }

    companion object {
        @JvmStatic
        fun tags(): List<Tag> = listOf(
            Tag("The Vergecast", VergecastColors.Lime, VergecastColors.Ultraviolet, VergecastColors.Ink),
            Tag("Conspiracy Theories", ConspiracyColors.Mustard, ConspiracyColors.Stamp, ConspiracyColors.Ink),
            Tag("Waveform", WaveformColors.Page, WaveformColors.Red, WaveformColors.Text),
            Tag("Crime Junkie light", CrimeJunkieColors.Light.tag, CrimeJunkieColors.SplatMagenta, CrimeJunkieColors.Light.onTag),
            Tag("Crime Junkie dark", CrimeJunkieColors.Dark.tag, CrimeJunkieColors.SplatMagenta, CrimeJunkieColors.Dark.onTag),
            Tag("The Joe Rogan Experience", JreColors.Page, JreColors.Orange, JreColors.Ink),
            Tag("Rotten Mango", RottenMangoColors.Page, RottenMangoColors.Gold, RottenMangoColors.Ink),
            Tag("The Daily light", DailyColors.Light.ink, DailyColors.Light.tagMark, DailyColors.Light.page),
            Tag("The Daily dark", DailyColors.Dark.ink, DailyColors.Dark.tagMark, DailyColors.Dark.page),
        )
    }
}
