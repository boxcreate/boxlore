package cx.aswin.boxlore.core.designsystem.theme

import com.materialkolor.hct.Hct
import kotlin.math.sqrt

/**
 * What a cover gives the pages and the player that wear its colours. Only hue and colourfulness
 * come from the art; [artworkColorScheme] decides every tone, so any cover gives readable text.
 */
data class ArtworkPalette(
    /** The cover's largest colour family, or its neutral when colour covers little of it. */
    val ground: Int,
    /** The family that is most colourful for the area it covers: buttons, links, progress. */
    val accent: Int,
    /** The cover's mean colour, black and white included: roughly what its heavy blur shows. */
    val average: Int,
)

data class ArtworkColorCandidate(val argb: Int, val population: Int)

/** HCT chroma under which a colour reads as grey, cream or black rather than as a hue. */
internal const val ArtworkNeutralChroma = 12.0

private const val HueFamilyDegrees = 30.0
private const val HueFamilies = 12
private const val NeutralFamily = -1

/** The ground stays coloured while colour covers at least this share of the cover. */
private const val ColouredGroundShare = 0.2

/** An accent family must cover this share of the cover, or the smaller one when none does. */
private const val AccentShare = 0.04
private const val AccentFallbackShare = 0.015

private class ColourFamily(members: List<ArtworkColorCandidate>, total: Double) {
    val share = members.sumOf { it.population } / total
    val largest = members.maxBy { it.population }
    val mostColourful = members.maxBy { Hct.fromInt(it.argb).chroma }

    /** Colour counts most, but area still matters: a vivid speck does not outrank a vivid field. */
    val accentScore = Hct.fromInt(mostColourful.argb).chroma.coerceAtMost(80.0) * sqrt(share)
}

/**
 * Groups a cover's quantised colours into 30° hue families, pooling each family's whole area so a
 * textured field split over many swatches still counts as one. Neutrals pool separately and never
 * become the accent. A cover with no meaningful colour has no palette, so its pages keep the
 * listener's theme.
 */
fun extractArtworkPalette(candidates: List<ArtworkColorCandidate>, average: Int): ArtworkPalette? {
    val usable = candidates.filter { it.population > 0 }
    if (usable.isEmpty()) return null
    val total = usable.sumOf { it.population }.toDouble()
    val families = usable.groupBy(::hueFamily).mapValues { (_, members) -> ColourFamily(members, total) }
    val coloured = families.filterKeys { it != NeutralFamily }.values
    val accent = coloured.filter { it.share >= AccentShare }.ifEmpty { coloured.filter { it.share >= AccentFallbackShare } }
        .maxByOrNull { it.accentScore } ?: return null
    val largestColour = coloured.maxBy { it.share }
    val ground = if (coloured.sumOf { it.share } >= ColouredGroundShare) largestColour else families[NeutralFamily] ?: largestColour
    return ArtworkPalette(ground = ground.largest.argb, accent = accent.mostColourful.argb, average = average)
}

private fun hueFamily(candidate: ArtworkColorCandidate): Int {
    val hct = Hct.fromInt(candidate.argb)
    return if (hct.chroma < ArtworkNeutralChroma) NeutralFamily else (hct.hue / HueFamilyDegrees).toInt().coerceIn(0, HueFamilies - 1)
}
