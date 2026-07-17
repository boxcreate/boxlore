package cx.aswin.boxlore.core.model

/**
 * Canonical genre vocabulary shared by preference capture, ranking, and network summaries.
 */
object PodcastGenres {
    const val NEWS = "News"
    const val TECHNOLOGY = "Technology"
    const val BUSINESS = "Business"
    const val COMEDY = "Comedy"
    const val TRUE_CRIME = "True Crime"
    const val SPORTS = "Sports"
    const val HEALTH = "Health"
    const val HISTORY = "History"
    const val ARTS = "Arts"
    const val SOCIETY_AND_CULTURE = "Society & Culture"
    const val EDUCATION = "Education"
    const val SCIENCE = "Science"
    const val TV_AND_FILM = "TV & Film"
    const val FICTION = "Fiction"
    const val MUSIC = "Music"
    const val RELIGION_AND_SPIRITUALITY = "Religion & Spirituality"
    const val KIDS_AND_FAMILY = "Kids & Family"
    const val LEISURE = "Leisure"
    const val GOVERNMENT = "Government"

    val all: List<String> = listOf(
        NEWS,
        TECHNOLOGY,
        BUSINESS,
        COMEDY,
        TRUE_CRIME,
        SPORTS,
        HEALTH,
        HISTORY,
        ARTS,
        SOCIETY_AND_CULTURE,
        EDUCATION,
        SCIENCE,
        TV_AND_FILM,
        FICTION,
        MUSIC,
        RELIGION_AND_SPIRITUALITY,
        KIDS_AND_FAMILY,
        LEISURE,
        GOVERNMENT,
    )

    private val normalized = buildMap {
        all.forEach { genre -> put(genre.normalizedGenreKey(), genre) }
        put("health & fitness", HEALTH)
        put("fitness", HEALTH)
        put("society", SOCIETY_AND_CULTURE)
        put("culture", SOCIETY_AND_CULTURE)
        put("religion", RELIGION_AND_SPIRITUALITY)
        put("spirituality", RELIGION_AND_SPIRITUALITY)
        put("family", KIDS_AND_FAMILY)
        put("kids", KIDS_AND_FAMILY)
        put("tv", TV_AND_FILM)
        put("film", TV_AND_FILM)
        put("technology & science", TECHNOLOGY)
        put("tech", TECHNOLOGY)
    }

    fun canonicalize(value: String?): String? {
        val key = value?.normalizedGenreKey()?.takeIf(String::isNotEmpty) ?: return null
        return normalized[key]
    }
}

private fun String.normalizedGenreKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}
