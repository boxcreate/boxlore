package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance as relativeLuminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

private const val TextContrast = 4.5f
private const val ToneStep = 2.0

/**
 * A cover's colours as a complete Material scheme, shared by detail pages and the player.
 *
 * The ground takes the cover's ground hue at a fixed tone (light 92, dark 11), so the page is
 * clearly that show's colour and still always readable; cards, chips and outlines step away from
 * it on the same hue. The accent keeps the cover's most colourful hue and moves only in tone until
 * it reads as text on the ground, and every foreground is placed at a tone that clears 4.5:1 on
 * its container. Pure black and white styles keep their own grounds.
 */
fun artworkColorScheme(palette: ArtworkPalette, isDark: Boolean, surfaceStyle: String = SurfaceStyles.STANDARD): ColorScheme {
    val base = generatePersonalColorScheme(CustomThemeSeeds(Color(palette.accent)), isDark)
    val scheme = base.withArtworkGround(Hct.fromInt(palette.ground), isDark).withArtworkAccent(Hct.fromInt(palette.accent), isDark)
    return applySurfaceStyle(scheme, isDark, surfaceStyle)
}

/**
 * The colour the player is built on: the cover's main (ground) colour when it is clearly coloured,
 * else its accent. The full player's surfaces and the mini player's bar share it, so the two read
 * as one object; the accent is kept for Play, progress and active controls.
 */
private fun playerGround(palette: ArtworkPalette): Hct {
    val ground = Hct.fromInt(palette.ground)
    return if (ground.chroma >= PlayerGroundChroma) ground else Hct.fromInt(palette.accent)
}

/** Under this chroma the cover's main colour reads as grey, so the player follows the accent instead. */
private const val PlayerGroundChroma = 18.0

/**
 * The full player's scheme: a Material 3 tonal scheme seeded from the cover's accent (buttons,
 * progress, text roles), on a softly tinted surface ladder in the player ground's hue, light 92
 * down to 82 and dark 20 up to 32, so it is neither near-white nor near-black. Control tiles step
 * clearly off their tray in both modes. Pure black and white styles keep their grounds.
 */
fun artworkPlayerColorScheme(palette: ArtworkPalette, isDark: Boolean, surfaceStyle: String = SurfaceStyles.STANDARD): ColorScheme {
    val base = generatePersonalColorScheme(CustomThemeSeeds(Color(palette.accent)), isDark)
    val ground = playerGround(palette)
    val accent = Hct.fromInt(palette.accent)
    val tones = ArtworkTones(if (isDark) artworkHueForDarkTone(ground.hue) else ground.hue, ground.chroma.coerceAtMost(if (isDark) 20.0 else 16.0))
    val tiles = ArtworkTones(if (isDark) accent.hue else artworkHueForDarkTone(accent.hue), accent.chroma.coerceAtMost(20.0))
    val scheme = if (isDark) {
        base.copy(
            background = tones.at(20.0), surface = tones.at(20.0), surfaceDim = tones.at(15.0), surfaceBright = tones.at(34.0),
            surfaceContainerLowest = tones.at(16.0), surfaceContainerLow = tones.at(22.0), surfaceContainer = tones.at(24.0),
            surfaceContainerHigh = tones.at(28.0), surfaceContainerHighest = tones.at(32.0), surfaceVariant = tones.at(32.0),
            outlineVariant = tones.at(40.0), inverseOnSurface = tones.at(20.0),
            secondaryContainer = tiles.at(36.0), onSecondaryContainer = tiles.at(94.0, 8.0),
        )
    } else {
        base.copy(
            background = tones.at(92.0), surface = tones.at(92.0), surfaceDim = tones.at(86.0), surfaceBright = tones.at(96.0),
            surfaceContainerLowest = tones.at(96.0), surfaceContainerLow = tones.at(90.0), surfaceContainer = tones.at(88.0),
            surfaceContainerHigh = tones.at(85.0), surfaceContainerHighest = tones.at(82.0), surfaceVariant = tones.at(82.0),
            outlineVariant = tones.at(76.0), inverseOnSurface = tones.at(95.0),
            secondaryContainer = tiles.at(80.0), onSecondaryContainer = tiles.at(10.0, 8.0),
        )
    }
    return applySurfaceStyle(scheme, isDark, surfaceStyle)
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
        /** Without a cover palette, the player's own scheme supplies the same roles. */
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
 * The mini player in the same family as the full player: its bar is the player ground one step
 * deeper than the full player's background in light mode (84) and lighter in dark mode (28), with
 * the same soft chroma, so it never looks sharp next to the cover. Play uses the full player's accent
 * tones (light 40, dark 80); the progress sweep is the bar one step darker (light) or lighter (dark).
 * Every text pair clears 4.5:1.
 */
fun artworkMiniPlayerColors(palette: ArtworkPalette, isDark: Boolean): ArtworkMiniPlayerColors {
    val ground = playerGround(palette)
    val accent = Hct.fromInt(palette.accent)
    val barHue = if (isDark) artworkHueForDarkTone(ground.hue) else ground.hue
    val bar = ArtworkTones(barHue, ground.chroma.coerceAtMost(if (isDark) 26.0 else 22.0))
    val text = ArtworkTones(barHue, ground.chroma.coerceAtMost(8.0))
    val play = ArtworkTones(if (isDark) accent.hue else artworkHueForDarkTone(accent.hue), accent.chroma.coerceIn(24.0, 64.0))
    return if (isDark) {
        ArtworkMiniPlayerColors(
            bar = bar.at(28.0),
            onBar = text.at(95.0),
            onBarVariant = text.at(80.0),
            progress = bar.at(35.0),
            play = play.at(80.0),
            onPlay = play.at(15.0, 12.0),
        )
    } else {
        ArtworkMiniPlayerColors(
            bar = bar.at(84.0),
            onBar = text.at(12.0),
            onBarVariant = text.at(30.0),
            progress = bar.at(78.0),
            play = play.at(40.0),
            onPlay = play.at(99.0, 4.0),
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

private class ArtworkTones(private val hue: Double, private val chroma: Double) {
    fun at(tone: Double, chroma: Double = this.chroma): Color = Color(Hct.from(hue, chroma, tone).toInt())
}

private fun ColorScheme.withArtworkGround(ground: Hct, isDark: Boolean): ColorScheme {
    // A neutral cover keeps a neutral page with at most a faint cast, never an invented hue.
    val chroma = if (ground.chroma < ArtworkNeutralChroma) {
        ground.chroma.coerceAtMost(4.0)
    } else {
        (ground.chroma * 0.5).coerceIn(6.0, if (isDark) 20.0 else 16.0)
    }
    val tones = ArtworkTones(if (isDark) artworkHueForDarkTone(ground.hue) else ground.hue, chroma)
    val text = chroma.coerceAtMost(6.0)
    return if (isDark) {
        copy(
            background = tones.at(11.0), surface = tones.at(11.0), surfaceDim = tones.at(8.0), surfaceBright = tones.at(26.0),
            surfaceContainerLowest = tones.at(7.0), surfaceContainerLow = tones.at(15.0), surfaceContainer = tones.at(18.0),
            surfaceContainerHigh = tones.at(22.0), surfaceContainerHighest = tones.at(26.0), surfaceVariant = tones.at(26.0),
            onBackground = tones.at(94.0, text), onSurface = tones.at(94.0, text), onSurfaceVariant = tones.at(80.0, text),
            outline = tones.at(62.0, text), outlineVariant = tones.at(32.0), inverseSurface = tones.at(90.0, text), inverseOnSurface = tones.at(20.0),
        )
    } else {
        copy(
            background = tones.at(92.0), surface = tones.at(92.0), surfaceDim = tones.at(85.0), surfaceBright = tones.at(96.0),
            surfaceContainerLowest = tones.at(97.0), surfaceContainerLow = tones.at(88.0), surfaceContainer = tones.at(86.0),
            surfaceContainerHigh = tones.at(84.0), surfaceContainerHighest = tones.at(81.0), surfaceVariant = tones.at(81.0),
            onBackground = tones.at(10.0, text), onSurface = tones.at(10.0, text), onSurfaceVariant = tones.at(30.0, text),
            outline = tones.at(48.0, text), outlineVariant = tones.at(74.0), inverseSurface = tones.at(20.0, text), inverseOnSurface = tones.at(95.0),
        )
    }
}

private fun ColorScheme.withArtworkAccent(accent: Hct, isDark: Boolean): ColorScheme {
    val tones = ArtworkTones(if (isDark) accent.hue else artworkHueForDarkTone(accent.hue), accent.chroma.coerceIn(36.0, 84.0))
    // Start at Material's usual accent tone and move away from the ground until it reads as text.
    var tone = if (isDark) 80.0 else 40.0
    while (tone in 0.0..100.0 && artworkContrast(tones.at(tone), background) < TextContrast) tone += if (isDark) ToneStep else -ToneStep
    val primary = tones.at(tone.coerceIn(0.0, 100.0))
    return copy(
        primary = primary,
        onPrimary = tones.at(if (isDark) 10.0 else 100.0, 8.0),
        primaryContainer = tones.at(if (isDark) 30.0 else 86.0),
        onPrimaryContainer = tones.at(if (isDark) 90.0 else 10.0, 24.0),
        inversePrimary = tones.at(if (isDark) 40.0 else 80.0),
        secondaryContainer = tones.at(if (isDark) 28.0 else 85.0, 16.0),
        onSecondaryContainer = tones.at(if (isDark) 90.0 else 10.0, 16.0),
        surfaceTint = primary,
    )
}
