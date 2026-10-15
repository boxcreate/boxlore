package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

/**
 * Two tones a cover is redrawn in: its darkest parts become [shadow] and its brightest [highlight],
 * by brightness alone ([colorFilter]). [highlight] stays at tone 38 or below, so [ArtworkShade]'s
 * light words read on any part of a duotone cover (content 6:1, soft content 4.5:1 at worst).
 */
@Immutable
data class ArtworkDuotone(val shadow: Color, val highlight: Color) {
    /**
     * A color matrix that maps each pixel's brightness onto the line from [shadow] to [highlight],
     * with a slight contrast lift so flat covers keep their shapes. Cheap, works on every Android
     * version and needs no extra bitmap.
     */
    fun colorFilter(): ColorFilter {
        // Brightness after the contrast lift: c * (0.2126 R + 0.7152 G + 0.0722 B) - (c - 1) * 127.5.
        val lift = (DuotoneContrast - 1f) * 127.5f
        fun row(from: Float, to: Float): FloatArray {
            val span = (to - from) * DuotoneContrast
            return floatArrayOf(span * 0.2126f, span * 0.7152f, span * 0.0722f, 0f, from * 255f - (to - from) * lift)
        }
        return ColorFilter.colorMatrix(
            ColorMatrix(
                row(shadow.red, highlight.red) + row(shadow.green, highlight.green) + row(shadow.blue, highlight.blue) +
                    floatArrayOf(0f, 0f, 0f, 1f, 0f),
            ),
        )
    }

    companion object {
        /** Black and white: a near-black shadow and a mid grey highlight. */
        val Neutral: ArtworkDuotone = duotone(chroma = 0.0, darkHue = 0.0)

        /** The cover's seed, the same colour its pages, the player and its other hero cards use. */
        fun fromPalette(palette: ArtworkPalette): ArtworkDuotone = fromColor(Color(palette.seed))

        /** One colour, such as a cover's seed or the app's primary. */
        fun fromColor(color: Color): ArtworkDuotone {
            val hct = Hct.fromInt(color.toArgb())
            val chroma = if (hct.chroma < ArtworkNeutralChroma) 0.0 else hct.chroma
            return duotone(chroma = chroma, darkHue = artworkHueForDarkTone(hct.hue))
        }
    }
}

private const val DuotoneContrast = 1.12f
private const val ShadowTone = 8.0
private const val HighlightTone = 38.0
private const val MaxShadowChroma = 40.0
private const val MaxHighlightChroma = 56.0

private fun duotone(chroma: Double, darkHue: Double) = ArtworkDuotone(
    shadow = Color(Hct.from(darkHue, chroma.coerceAtMost(MaxShadowChroma), ShadowTone).toInt()),
    highlight = Color(Hct.from(darkHue, chroma.coerceAtMost(MaxHighlightChroma), HighlightTone).toInt()),
)
