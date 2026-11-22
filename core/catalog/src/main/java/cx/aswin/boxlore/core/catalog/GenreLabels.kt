package cx.aswin.boxlore.core.catalog

import androidx.annotation.StringRes
import cx.aswin.boxlore.core.model.PodcastGenres

/**
 * The translated name to show for a show's genre tag, or null when no tag should show its own
 * name: blank, the "Podcast" placeholder a show with no category gets, or a genre without a
 * translated name (callers then show the feed's own text or no tag). Synonyms share a name, so
 * "Society & Culture" and "Culture" both read as the society genre.
 */
@StringRes
fun genreLabelRes(raw: String?): Int? {
    if (raw.isNullOrBlank() || PodcastGenres.isPlaceholder(raw)) return null
    return PodcastGenres.canonicalize(raw)?.let(GenreLabelResources::get)
}

/** Each canonical genre in [PodcastGenres.all] and its translated name. */
internal val GenreLabelResources: Map<String, Int> = mapOf(
    PodcastGenres.NEWS to R.string.auto_genre_news,
    PodcastGenres.TECHNOLOGY to R.string.auto_genre_technology,
    PodcastGenres.BUSINESS to R.string.auto_genre_business,
    PodcastGenres.COMEDY to R.string.auto_genre_comedy,
    PodcastGenres.TRUE_CRIME to R.string.auto_genre_true_crime,
    PodcastGenres.SPORTS to R.string.auto_genre_sports,
    PodcastGenres.HEALTH to R.string.auto_genre_health,
    PodcastGenres.HISTORY to R.string.auto_genre_history,
    PodcastGenres.ARTS to R.string.auto_genre_arts,
    PodcastGenres.SOCIETY_AND_CULTURE to R.string.auto_genre_society,
    PodcastGenres.EDUCATION to R.string.auto_genre_education,
    PodcastGenres.SCIENCE to R.string.auto_genre_science,
    PodcastGenres.TV_AND_FILM to R.string.auto_genre_tv_film,
    PodcastGenres.FICTION to R.string.auto_genre_fiction,
    PodcastGenres.MUSIC to R.string.auto_genre_music,
    PodcastGenres.RELIGION_AND_SPIRITUALITY to R.string.auto_genre_religion,
    PodcastGenres.KIDS_AND_FAMILY to R.string.auto_genre_family,
    PodcastGenres.LEISURE to R.string.auto_genre_leisure,
    PodcastGenres.GOVERNMENT to R.string.auto_genre_government,
)
