package cx.aswin.boxlore.feature.home.components

import java.util.Locale

internal sealed interface HeroSpotlightContext {
    data class Rank(val number: String, val market: String) : HeroSpotlightContext
    data class Genre(val name: String) : HeroSpotlightContext
    data class General(val label: String) : HeroSpotlightContext
}

private val rankLabel = Regex("^#([1-9][0-9]*) IN (.+)$", RegexOption.IGNORE_CASE)
private const val TrendingPrefix = "TRENDING IN "
private val genreWordSeparator = Regex("\\s+")

/** Only a two-word genre can wrap; all other names fit on one line. */
internal fun heroGenreMaxLines(genre: String): Int = if (genre.trim().split(genreWordSeparator).size == 2) 2 else 1

/** Presentation of the supplied context only; never derives a new chart rank or genre. */
internal fun heroSpotlightContext(label: String, showGenre: String): HeroSpotlightContext {
    val trimmed = label.trim()
    rankLabel.matchEntire(trimmed)?.let { match ->
        val market = match.groupValues[2].trim()
        if (market.isNotEmpty()) return HeroSpotlightContext.Rank(match.groupValues[1], displayHeroWords(market))
    }
    if (trimmed.startsWith(TrendingPrefix, ignoreCase = true)) {
        val genre = trimmed.substring(TrendingPrefix.length).trim()
        if (genre.isNotEmpty()) {
            val name = if (genre.equals(showGenre.trim(), ignoreCase = true)) showGenre.trim() else displayHeroWords(genre)
            return HeroSpotlightContext.Genre(name)
        }
    }
    return HeroSpotlightContext.General(trimmed.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) })
}

private fun displayHeroWords(value: String): String = value.split(' ').joinToString(" ") { word ->
    if (word in setOf("US", "UK", "TV", "AI")) word else word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
}
