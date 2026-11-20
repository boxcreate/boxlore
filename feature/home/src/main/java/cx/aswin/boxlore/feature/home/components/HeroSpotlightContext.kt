package cx.aswin.boxlore.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.feature.home.R
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

/** Hero state keys from `HomeHeroLogic` (and the time-left suffix it may carry) mapped to the listener's words. */
private val heroStateLabels = mapOf(
    "TRENDING" to R.string.home_hero_trending,
    "JUMP BACK IN" to R.string.home_hero_resume_collection,
    "NEW EPISODES" to R.string.home_hero_new_collection,
    "RESUME" to R.string.home_hero_resume,
    "FRESH DROP" to R.string.home_hero_fresh_drop,
    "NEW EPISODE" to R.string.home_hero_new_episode,
    "NEXT" to R.string.home_hero_next_episode,
)

/** The words shown for a hero [label] key; an unknown one is shown as written, in sentence case. */
@Composable
internal fun heroStateLabel(label: String): String {
    val key = label.trim().uppercase(Locale.ROOT).substringBefore(" •")
    val id = heroStateLabels[key] ?: return label.trim().lowercase(currentLocale()).replaceFirstChar { it.titlecase(currentLocale()) }
    return stringResource(id)
}

/** Chart markets named in full, by country code; "US" and "UK" stay as their short names. */
private val heroMarketCountries = mapOf(
    "India" to "IN",
    "France" to "FR",
    "Germany" to "DE",
    "Netherlands" to "NL",
    "Singapore" to "SG",
    "Spain" to "ES",
    "Brazil" to "BR",
    "Russia" to "RU",
    "Indonesia" to "ID",
)

/** A Rank context's [market] in the app language ("India" is "Indien" in German). */
@Composable
internal fun heroMarketName(market: String): String {
    val region = heroMarketCountries[market] ?: return market
    return Locale.Builder().setRegion(region).build().getDisplayCountry(currentLocale())
}
