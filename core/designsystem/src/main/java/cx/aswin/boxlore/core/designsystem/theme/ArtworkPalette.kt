package cx.aswin.boxlore.core.designsystem.theme

import com.materialkolor.hct.Hct

/**
 * What a cover gives the screens that wear its colours (podcast and episode pages, the full and
 * mini player, Home's hero cards): [seed], the one colour each screen builds its Material scheme
 * from, and [average], the cover's mean colour (roughly what its heavy blur shows).
 */
data class ArtworkPalette(
    val seed: Int,
    val average: Int,
)

data class ArtworkColorCandidate(val argb: Int, val population: Int)

/** HCT chroma under which a colour reads as grey, cream or black rather than as a hue. */
internal const val ArtworkNeutralChroma = 12.0

/** Avoid white margins, black lettering and tiny bright details; monochrome art keeps the app palette. */
fun selectArtworkSeed(candidates: List<ArtworkColorCandidate>): Int? {
    val total = candidates.sumOf { it.population.coerceAtLeast(0) }.coerceAtLeast(1)
    return candidates.filter { it.population.toDouble() / total >= 0.01 }.map { it to Hct.fromInt(it.argb) }
        .filter { (_, hct) -> hct.chroma >= 8.0 && hct.tone in 12.0..92.0 }
        .maxByOrNull { (candidate, hct) -> candidate.population.toDouble() / total * 70.0 + hct.chroma.coerceAtMost(64.0) * 0.3 }?.first?.argb
}

/**
 * The shared picker: a cover's seed ([selectArtworkSeed]) and mean colour. A cover with no
 * meaningful colour has no palette, so its screens keep the listener's theme.
 */
fun extractArtworkPalette(candidates: List<ArtworkColorCandidate>, average: Int): ArtworkPalette? =
    selectArtworkSeed(candidates)?.let { ArtworkPalette(seed = it, average = average) }
