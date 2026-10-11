package cx.aswin.boxlore.feature.player.v2

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import cx.aswin.boxlore.core.designsystem.theme.generateBrandColorScheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MinimalControlAccentTest {
    @Test
    fun `auxiliary accents stay related to the artwork and separate by tone in both modes`() {
        val seeds = listOf(Color(0xFFB8F26B), Color(0xFFE77986), Color(0xFF3058E8), Color(0xFF846235))
        for (dark in listOf(false, true)) {
            for (seed in seeds) {
                val colors = generateBrandColorScheme(seed, dark)
                val accent = minimalControlAccent(colors)
                val primaryHsl = FloatArray(3)
                val auxiliaryHsl = FloatArray(3)
                ColorUtils.colorToHSL(colors.primary.toArgb(), primaryHsl)
                ColorUtils.colorToHSL(accent.container.toArgb(), auxiliaryHsl)
                val distance = abs(primaryHsl[0] - auxiliaryHsl[0]).let { minOf(it, 360f - it) }
                assertTrue("Secondary controls must stay in the artwork's related hue family", distance <= 45f)
                assertTrue("Tone must separate the secondary controls from Play Pause", abs(primaryHsl[2] - auxiliaryHsl[2]) >= 0.3f)
                assertTrue("Icons and speed text need readable contrast", ColorUtils.calculateContrast(accent.content.toArgb(), accent.container.toArgb()) >= 4.5)
            }
        }
    }
}
