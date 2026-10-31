package cx.aswin.boxlore.feature.info.branded

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyColors
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkieColors
import cx.aswin.boxlore.feature.info.branded.daily.DailyColors
import cx.aswin.boxlore.feature.info.branded.jre.JreColors
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoColors
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastColors
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformColors
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class BrandedEpisodeActionsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("shows")
    fun `each show's episode buttons are readable`(show: String, colors: BrandedActionColors) {
        // Icons need 3:1 against their button; Play's label is text, so 4.5:1.
        assertContrast(colors.onToggle, colors.toggle, 3f, "$show toggle")
        assertContrast(colors.onChecked, colors.checked, 3f, "$show switched-on toggle")
        assertContrast(colors.play, colors.toggle, 3f, "$show download spinner")
        assertContrast(colors.onPlay, colors.play, 4.5f, "$show Play")
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float, what: String) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (dark + 0.05f)
        assertTrue(ratio >= minimum, "$what: contrast $ratio < $minimum")
    }

    companion object {
        @JvmStatic
        fun shows(): List<Array<Any>> = listOf(
            arrayOf("The Vergecast", VergecastColors.Actions),
            arrayOf("Conspiracy Theories", ConspiracyColors.Actions),
            arrayOf("Waveform", WaveformColors.Actions),
            arrayOf("Crime Junkie light", CrimeJunkieColors.Light.actions),
            arrayOf("Crime Junkie dark", CrimeJunkieColors.Dark.actions),
            arrayOf("The Joe Rogan Experience", JreColors.Actions),
            arrayOf("Rotten Mango", RottenMangoColors.Actions),
            arrayOf("The Daily light", DailyColors.Light.actions),
            arrayOf("The Daily dark", DailyColors.Dark.actions),
        )
    }
}
