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
        put("health and fitness", HEALTH)
        put("fitness", HEALTH)
        put("wellness", HEALTH)
        put("mental health", HEALTH)
        put("medicine", HEALTH)
        put("medical", HEALTH)
        put("society", SOCIETY_AND_CULTURE)
        put("culture", SOCIETY_AND_CULTURE)
        put("society and culture", SOCIETY_AND_CULTURE)
        put("religion", RELIGION_AND_SPIRITUALITY)
        put("spirituality", RELIGION_AND_SPIRITUALITY)
        put("religion and spirituality", RELIGION_AND_SPIRITUALITY)
        put("family", KIDS_AND_FAMILY)
        put("kids", KIDS_AND_FAMILY)
        put("children", KIDS_AND_FAMILY)
        put("kids and family", KIDS_AND_FAMILY)
        put("tv", TV_AND_FILM)
        put("film", TV_AND_FILM)
        put("films", TV_AND_FILM)
        put("movie", TV_AND_FILM)
        put("movies", TV_AND_FILM)
        put("cinema", TV_AND_FILM)
        put("television", TV_AND_FILM)
        put("tv and film", TV_AND_FILM)
        put("technology & science", TECHNOLOGY)
        put("technology and science", TECHNOLOGY)
        put("tech", TECHNOLOGY)
        put("computers", TECHNOLOGY)
        put("software", TECHNOLOGY)
        put("coding", TECHNOLOGY)
        put("programming", TECHNOLOGY)
        put("developer", TECHNOLOGY)
        put("ai", TECHNOLOGY)
        put("hardware", TECHNOLOGY)
        put("comedy", COMEDY)
        put("funny", COMEDY)
        put("humor", COMEDY)
        put("humour", COMEDY)
        put("standup", COMEDY)
        put("true crime", TRUE_CRIME)
        put("truecrime", TRUE_CRIME)
        put("true-crime", TRUE_CRIME)
        put("crime", TRUE_CRIME)
        put("sports", SPORTS)
        put("sport", SPORTS)
        put("athletics", SPORTS)
        put("football", SPORTS)
        put("soccer", SPORTS)
        put("basketball", SPORTS)
        put("baseball", SPORTS)
        put("business", BUSINESS)
        put("finance", BUSINESS)
        put("investing", BUSINESS)
        put("money", BUSINESS)
        put("economy", BUSINESS)
        put("arts", ARTS)
        put("art", ARTS)
        put("design", ARTS)
        put("visual arts", ARTS)
        put("painting", ARTS)
        put("education", EDUCATION)
        put("learning", EDUCATION)
        put("science", SCIENCE)
        put("fiction", FICTION)
        put("stories", FICTION)
        put("story", FICTION)
        put("books", FICTION)
        put("sci-fi", FICTION)
        put("scifi", FICTION)
        put("sci fi", FICTION)
        put("science fiction", FICTION)
        put("drama", FICTION)
        put("audio drama", FICTION)
        put("novels", FICTION)
        put("music", MUSIC)
        put("leisure", LEISURE)
        put("hobbies", LEISURE)
        put("gaming", LEISURE)
        put("games", LEISURE)
        put("video games", LEISURE)
        put("crafts", LEISURE)
        put("relaxation", LEISURE)
        put("automotive", LEISURE)
        put("aviation", LEISURE)
        put("government", GOVERNMENT)
        put("govt", GOVERNMENT)
        put("politics", GOVERNMENT)
        put("law", GOVERNMENT)
        put("policy", GOVERNMENT)
        put("elections", GOVERNMENT)
        put("news", NEWS)
        put("daily news", NEWS)
        put("journalism", NEWS)
        put("current events", NEWS)
        put("history", HISTORY)
    }

    fun canonicalize(value: String?): String? {
        val key = value?.normalizedGenreKey()?.takeIf(String::isNotEmpty) ?: return null
        return normalized[key]
    }
}

private fun String.normalizedGenreKey(): String = trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
