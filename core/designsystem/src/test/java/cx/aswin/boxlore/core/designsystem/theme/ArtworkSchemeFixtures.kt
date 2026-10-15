package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import kotlin.math.abs
import kotlin.math.min

/** Covers, styles and checks shared by the page and player artwork scheme tests. */
internal object ArtworkSchemeFixtures {
    fun hct(hue: Double, chroma: Double, tone: Double) = Hct.from(hue, chroma, tone).toInt()

    /** Seeds the picker can return: any hue, chroma at least 8, tone 12 to 92. */
    val palettes: List<ArtworkPalette> = buildList {
        for (hue in 0 until 360 step 30) {
            for ((chroma, tone) in listOf(12.0 to 20.0, 70.0 to 55.0, 40.0 to 88.0)) {
                add(ArtworkPalette(seed = hct(hue.toDouble(), chroma, tone), average = hct(hue.toDouble(), chroma, tone)))
            }
        }
    }

    val styles = listOf(
        false to SurfaceStyles.STANDARD,
        true to SurfaceStyles.STANDARD,
        true to SurfaceStyles.AMOLED,
        false to SurfaceStyles.PURE_WHITE,
        true to SurfaceStyles.DYNAMIC_OLED_WHITE,
        false to SurfaceStyles.DYNAMIC_OLED_WHITE,
    )

    /** Text and button pairs that must read at 4.5:1. */
    fun textPairs(scheme: ColorScheme): List<Pair<String, Pair<Color, Color>>> = listOf(
        "onSurface/background" to (scheme.onSurface to scheme.background),
        "onBackground/background" to (scheme.onBackground to scheme.background),
        "onSurface/surface" to (scheme.onSurface to scheme.surface),
        "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
        "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
        "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
    )

    /** Every pair in [scheme] below [minimum], described for a failure message. */
    fun unreadable(scheme: ColorScheme, minimum: Float, where: String): List<String> = textPairs(scheme).mapNotNull { (name, pair) ->
        val contrast = artworkContrast(pair.first, pair.second)
        "$name $where: contrast=$contrast".takeIf { contrast < minimum }
    }

    fun hueGap(a: Double, b: Double) = abs(a - b).let { min(it, 360.0 - it) }
}
