package cx.aswin.boxlore.feature.info.branded.serial

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.brandedEpisodePicture
import cx.aswin.boxlore.feature.info.branded.brandedShowPicture
import java.util.Locale

/**
 * One card in the deck: a season's number, its name when known, and its cover: [cover] for a season
 * whose cover ships with the app, otherwise [picture] from the feed.
 */
@Immutable
internal data class SerialSeason(val number: Int, val name: String?, @DrawableRes val cover: Int?, val picture: String?) {
    /** The card's label: "18 · METRO", or [unnamed] ("Season 19") while a new season's name is unknown; in capitals. */
    fun label(unnamed: String, locale: Locale = Locale.getDefault()): String =
        (name?.let { String.format(Locale.ROOT, "%02d · %s", number, it) } ?: unnamed).uppercase(locale)
}

internal object SerialSeasons {
    /**
     * The seasons whose covers ship with the app, as Serial names them. Covers of seasons that have
     * ended never change; a season newer than these comes from the feed.
     */
    val Bundled: List<SerialSeason> = listOf(
        SerialSeason(12, "The Kids of Rutherford County", R.drawable.serial_season_12, null),
        SerialSeason(13, "The Good Whale", R.drawable.serial_season_13, null),
        SerialSeason(14, "The Retrievals S02", R.drawable.serial_season_14, null),
        SerialSeason(15, "The Preventionist", R.drawable.serial_season_15, null),
        SerialSeason(16, "The Idiot", R.drawable.serial_season_16, null),
        SerialSeason(17, "The Last 12 Weeks", R.drawable.serial_season_17, null),
        SerialSeason(18, "METRO", R.drawable.serial_season_18, null),
    )

    /** Cards in the deck, as in the logo's stack: the newest on top and six behind it. */
    const val SHOWN = 7
}

/**
 * The deck, newest season first. A season newer than the bundled ones comes from [feed] (trailers
 * included), showing its cover as [serialSeasonPicture] finds it and the name its episodes' titles share.
 */
internal fun serialDeck(
    feed: List<Episode>,
    podcast: Podcast,
    bundled: List<SerialSeason> = SerialSeasons.Bundled,
    shown: Int = SerialSeasons.SHOWN,
): List<SerialSeason> {
    val newest = bundled.maxOfOrNull { it.number } ?: 0
    val fresh = feed.filter { (it.seasonNumber ?: 0) > newest }
        .groupBy { requireNotNull(it.seasonNumber) }
        .map { (number, episodes) -> SerialSeason(number, serialSeasonName(episodes), null, serialSeasonPicture(episodes, podcast)) }
    return (fresh + bundled).sortedByDescending { it.number }.take(shown)
}

/**
 * A season's cover. Seasons that give every episode its own picture put the season's art on the
 * trailer and the first episode, so it is the trailer's own picture, else episode 1's; while
 * neither is loaded, the picture every loaded episode shares, or the show's cover.
 */
internal fun serialSeasonPicture(episodes: List<Episode>, podcast: Podcast): String? {
    val trailer = episodes.filter { it.episodeType.equals("trailer", ignoreCase = true) }.minByOrNull { it.publishedDate }
    val first = episodes.filter { it.episodeNumber == 1 && !it.episodeType.equals("trailer", ignoreCase = true) }.minByOrNull { it.publishedDate }
    listOfNotNull(trailer, first).firstNotNullOfOrNull { brandedEpisodePicture(it, podcast) }?.let { return it }
    val shared = episodes.map { brandedEpisodePicture(it, podcast) }.distinct().singleOrNull()
    return shared ?: brandedShowPicture(podcast)
}

/** The name a season's episode titles share before " - ", when at least two of them use it. */
internal fun serialSeasonName(episodes: List<Episode>): String? = episodes
    .mapNotNull { serialTitlePrefix(it.title) }
    .groupingBy { it }
    .eachCount()
    .filterValues { it >= 2 }
    .maxByOrNull { it.value }
    ?.key

/** "METRO" from "METRO - Karat and Schtick"; null for a title without one. */
internal fun serialTitlePrefix(title: String): String? =
    TitleParts.find(title)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }

/** The latest episode's line above its title: its season's name and its number. */
@Immutable
internal data class SerialEpisodeLine(val season: String?, val episode: Int?, val headline: String)

/**
 * The season's name and the episode's number for the latest-episode card, and its title without
 * the season's name in front: "METRO - Karat and Schtick" reads "Karat and Schtick" under
 * "METRO · Episode 2". A title that is only a number after the name ("The Idiot - Chapter 5")
 * stays whole.
 */
internal fun serialEpisodeLine(episode: Episode, bundled: List<SerialSeason> = SerialSeasons.Bundled): SerialEpisodeLine {
    val prefix = serialTitlePrefix(episode.title)
    val season = bundled.firstOrNull { it.number == episode.seasonNumber }?.name ?: prefix
    val rest = TitleParts.find(episode.title)?.let { episode.title.substring(it.range.last + 1).trim() }
    val headline = rest
        ?.takeUnless { OnlyNumber.matches(it) }
        ?.takeIf { prefix?.equals(season, ignoreCase = true) == true }
        ?: episode.title
    return SerialEpisodeLine(season, episode.episodeNumber, headline)
}

private val TitleParts = Regex("""^(.+?)\s+[-–—]\s+""")
private val OnlyNumber = Regex("""^(?:ep\.?|episode|chapter|part)?\s*\d+$""", RegexOption.IGNORE_CASE)
private const val MAX_NAME = 40
