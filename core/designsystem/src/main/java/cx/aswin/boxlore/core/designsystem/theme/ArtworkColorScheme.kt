package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance as relativeLuminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.materialkolor.hct.Hct

/**
 * A podcast or episode page's colours: a Material scheme built from the cover's seed, the same way
 * as a custom theme. Pure black and white styles keep their own grounds.
 */
fun artworkColorScheme(palette: ArtworkPalette, isDark: Boolean, surfaceStyle: String = SurfaceStyles.STANDARD): ColorScheme =
    generatePersonalColorScheme(CustomThemeSeeds(Color(palette.seed)), isDark, surfaceStyle)

/**
 * The full player's colours: the brand scheme generator seeded from the cover's seed. In light mode
 * a bright cover's primary is deepened (same hue) until its light Play icon reads, rather than
 * turning the icon black, and near-black text becomes a deep shade of the cover's hue. If the Play
 * icon still cannot read, that one colour switches to black or white, whichever reads.
 */
fun artworkPlayerColorScheme(palette: ArtworkPalette, isDark: Boolean, surfaceStyle: String = SurfaceStyles.STANDARD): ColorScheme {
    val generated = generateBrandColorScheme(Color(palette.seed), isDark, surfaceStyle)
    val scheme = if (isDark) generated else lightPlayerTones(generated)
    if (artworkContrast(scheme.onPrimary, scheme.primary) >= ReadableContrast) return scheme
    val onPrimary = if (artworkContrast(Color.Black, scheme.primary) >= artworkContrast(Color.White, scheme.primary)) Color.Black else Color.White
    return scheme.copy(onPrimary = onPrimary)
}

/** WCAG AA for text; either black or white always reaches it against any colour. */
private const val ReadableContrast = 4.5f

/** Body text is softened only where it still reads this clearly against the page. */
private const val SoftInkContrast = 7f

private const val DeepenStep = 0.02f
private const val DeepestPrimary = 0.18f
private const val NearBlack = 0.15f
private const val SoftInkLightness = 0.18f
private const val SoftInkSaturation = 0.35f

/** Light mode: a primary deep enough for its own light foreground, and warm instead of black text. */
private fun lightPlayerTones(scheme: ColorScheme): ColorScheme {
    val primary = deepenUntilReadable(scheme.primary, scheme.onPrimary)
    val ink = softInk(scheme.onSurface, primary, scheme.surface)
    return scheme.copy(primary = primary, onSurface = ink, onBackground = ink)
}

private fun deepenUntilReadable(primary: Color, onPrimary: Color): Color {
    if (onPrimary.relativeLuminance() < 0.5f) return primary
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(primary.toArgb(), hsl)
    var candidate = primary
    while (artworkContrast(onPrimary, candidate) < ReadableContrast && hsl[2] > DeepestPrimary) {
        hsl[2] = (hsl[2] - DeepenStep).coerceAtLeast(DeepestPrimary)
        candidate = Color(ColorUtils.HSLToColor(hsl))
    }
    return candidate
}

private fun softInk(onSurface: Color, primary: Color, surface: Color): Color {
    val text = FloatArray(3)
    ColorUtils.colorToHSL(onSurface.toArgb(), text)
    if (text[2] >= NearBlack) return onSurface
    val hue = FloatArray(3)
    ColorUtils.colorToHSL(primary.toArgb(), hue)
    val ink = Color(ColorUtils.HSLToColor(floatArrayOf(hue[0], minOf(hue[1], SoftInkSaturation), SoftInkLightness)))
    return if (artworkContrast(ink, surface) >= SoftInkContrast) ink else onSurface
}

/** The mini player's colours: [bar] behind the title, [play] for its Play end, [progress] for the sweep. */
data class ArtworkMiniPlayerColors(
    val bar: Color,
    val onBar: Color,
    val onBarVariant: Color,
    val progress: Color,
    val play: Color,
    val onPlay: Color,
) {
    companion object {
        /** The player's scheme supplies every role: the bar is its primary container, Play its primary. */
        fun fromScheme(scheme: ColorScheme) = ArtworkMiniPlayerColors(
            bar = scheme.primaryContainer,
            onBar = scheme.onPrimaryContainer,
            onBarVariant = scheme.onPrimaryContainer.copy(alpha = 0.72f),
            progress = scheme.onPrimaryContainer.copy(alpha = 0.12f).compositeOver(scheme.primaryContainer),
            play = scheme.primary,
            onPlay = scheme.onPrimary,
        )
    }
}

/**
 * A deep but clearly coloured shade of [color]: its hue at Material tone [tone] with chroma up to
 * [maxChroma], so a dark surface still reads as that colour instead of black. Yellows turn gold
 * rather than olive, and near-neutral colours stay near-neutral. White text stays above 12:1 at
 * tone 18.
 */
fun artworkDeepShade(color: Color, tone: Double = 18.0, maxChroma: Double = 36.0): Color {
    val hct = Hct.fromInt(color.toArgb())
    val chroma = if (hct.chroma < ArtworkNeutralChroma) hct.chroma.coerceAtMost(6.0) else hct.chroma.coerceAtMost(maxChroma)
    return Color(Hct.from(artworkHueForDarkTone(hct.hue), chroma, tone).toInt())
}

/** WCAG contrast ratio between two opaque colours. */
fun artworkContrast(a: Color, b: Color): Float {
    val lighter = maxOf(a.relativeLuminance(), b.relativeLuminance())
    val darker = minOf(a.relativeLuminance(), b.relativeLuminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Yellows go olive at low tones, so a yellow that has to be dark turns toward gold instead.
 * Light yellows (pale grounds, dark-theme accents) keep their hue.
 */
internal fun artworkHueForDarkTone(hue: Double): Double = if (hue in 80.0..120.0) 55.0 + (hue - 80.0) * 0.25 else hue
