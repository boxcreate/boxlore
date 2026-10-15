package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

/**
 * Colours for words and buttons laid over a cover. The art fades into [deep] behind the words;
 * [content] and [softContent] are read on it; [accent] fills the main button with [onAccent] on
 * it; [container] backs secondary buttons and progress tracks, with [content] on it.
 *
 * Only hue and colourfulness come from the source colour. Every tone is fixed, so each pair stays
 * readable whatever cover or theme colour the shade starts from: [content] and [softContent] clear
 * 7:1 and 4.5:1 on [deep], [onAccent] clears 4.5:1 on [accent], and [content] clears 4.5:1 on
 * [container].
 */
@Immutable
data class ArtworkShade(
    val deep: Color,
    val container: Color,
    val content: Color,
    val softContent: Color,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        /** A plain near-black shade with white words and a light grey button. */
        val Neutral: ArtworkShade = Hct.from(0.0, 0.0, 50.0).let { shade(ground = it, accent = it, deepTone = NeutralDeepTone, neutral = true) }

        /** The cover's seed, the same colour its pages and the player are built from. */
        fun fromPalette(palette: ArtworkPalette): ArtworkShade = fromColor(Color(palette.seed))

        /** One colour, such as a cover's seed or the app's primary, for both the shade and the button. */
        fun fromColor(color: Color): ArtworkShade = Hct.fromInt(color.toArgb()).let { shade(ground = it, accent = it, deepTone = DeepTone) }
    }
}

private const val DeepTone = 16.0
private const val NeutralDeepTone = 6.0
private const val ContainerTone = 30.0
private const val ContentTone = 96.0
private const val SoftContentTone = 84.0
private const val AccentTone = 80.0
private const val NeutralAccentTone = 90.0
private const val OnAccentTone = 12.0
private const val MaxGroundChroma = 36.0
private const val MaxNeutralGroundChroma = 6.0
private const val MinAccentChroma = 16.0
private const val MaxAccentChroma = 48.0

/** [neutral] drops all colour: an HCT grey still reads back with a little chroma at some hue. */
private fun shade(ground: Hct, accent: Hct, deepTone: Double, neutral: Boolean = false): ArtworkShade {
    // A near-neutral colour keeps a near-neutral shade with at most a faint cast, never an invented hue.
    val groundChroma = when {
        neutral -> 0.0
        ground.chroma < ArtworkNeutralChroma -> ground.chroma.coerceAtMost(MaxNeutralGroundChroma)
        else -> ground.chroma.coerceAtMost(MaxGroundChroma)
    }
    val darkHue = artworkHueForDarkTone(ground.hue)
    val neutralAccent = neutral || accent.chroma < ArtworkNeutralChroma
    val accentChroma = if (neutralAccent) 0.0 else accent.chroma.coerceIn(MinAccentChroma, MaxAccentChroma)
    fun at(hue: Double, chroma: Double, tone: Double) = Color(Hct.from(hue, chroma, tone).toInt())
    return ArtworkShade(
        deep = at(darkHue, groundChroma, deepTone),
        container = at(darkHue, groundChroma, ContainerTone),
        content = at(ground.hue, groundChroma.coerceAtMost(8.0), ContentTone),
        softContent = at(ground.hue, groundChroma.coerceAtMost(12.0), SoftContentTone),
        accent = at(accent.hue, accentChroma, if (neutralAccent) NeutralAccentTone else AccentTone),
        onAccent = at(artworkHueForDarkTone(accent.hue), accentChroma.coerceAtMost(24.0), OnAccentTone),
    )
}
