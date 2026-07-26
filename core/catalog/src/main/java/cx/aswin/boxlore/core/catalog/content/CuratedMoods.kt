package cx.aswin.boxlore.core.catalog.content

/**
 * Canonical curated-mood catalog shared by Home daypart rails and Explore For You chips.
 * [id] is the backend `getCuratedVibe` provider key — do not rename without a server change.
 */
data class CuratedMood(
    val id: String,
    val title: String,
    val subtitle: String,
    val daypart: ContentDaypart,
)

object CuratedMoods {
    val all: List<CuratedMood> =
        listOf(
            CuratedMood(
                id = "morning_news",
                title = "What's happening",
                subtitle = "The stories shaping today",
                daypart = ContentDaypart.MORNING,
            ),
            CuratedMood(
                id = "morning_motivation",
                title = "A brighter start",
                subtitle = "Ideas and stories with a little lift",
                daypart = ContentDaypart.MORNING,
            ),
            CuratedMood(
                id = "business_insider",
                title = "Business in focus",
                subtitle = "Markets, technology, and the people moving them",
                daypart = ContentDaypart.MORNING,
            ),
            CuratedMood(
                id = "science_explainer",
                title = "Worth knowing",
                subtitle = "Clear answers to curious questions",
                daypart = ContentDaypart.AFTERNOON,
            ),
            CuratedMood(
                id = "tech_culture",
                title = "Tech right now",
                subtitle = "The ideas changing how we live and work",
                daypart = ContentDaypart.AFTERNOON,
            ),
            CuratedMood(
                id = "creative_focus",
                title = "Creative spark",
                subtitle = "Fresh perspectives from art and design",
                daypart = ContentDaypart.AFTERNOON,
            ),
            CuratedMood(
                id = "comedy_gold",
                title = "A good laugh",
                subtitle = "Comedy and conversation for winding down",
                daypart = ContentDaypart.EVENING,
            ),
            CuratedMood(
                id = "tv_film_buff",
                title = "On screen",
                subtitle = "Film, television, and culture worth talking about",
                daypart = ContentDaypart.EVENING,
            ),
            CuratedMood(
                id = "sports_fan",
                title = "Game time",
                subtitle = "Stories and analysis from across sport",
                daypart = ContentDaypart.EVENING,
            ),
            CuratedMood(
                id = "true_crime_sleep",
                title = "True crime after dark",
                subtitle = "Investigations that keep you listening",
                daypart = ContentDaypart.LATE_NIGHT,
            ),
            CuratedMood(
                id = "history_buff",
                title = "Stories from history",
                subtitle = "The past, told like it happened yesterday",
                daypart = ContentDaypart.LATE_NIGHT,
            ),
            CuratedMood(
                id = "mystery_thriller",
                title = "Mystery & suspense",
                subtitle = "Twists, tension, and stories for the night",
                daypart = ContentDaypart.LATE_NIGHT,
            ),
        )

    private val byId: Map<String, CuratedMood> = all.associateBy { it.id }

    fun forDaypart(daypart: ContentDaypart): List<CuratedMood> =
        all.filter { it.daypart == daypart }

    /** Explore chip order: current daypart first, then the rest in day cycle. */
    fun forHourOfDay(hourOfDay: Int): List<CuratedMood> {
        val current = daypartForHour(hourOfDay)
        val order =
            when (current) {
                ContentDaypart.MORNING ->
                    listOf(
                        ContentDaypart.MORNING,
                        ContentDaypart.AFTERNOON,
                        ContentDaypart.EVENING,
                        ContentDaypart.LATE_NIGHT,
                    )
                ContentDaypart.AFTERNOON ->
                    listOf(
                        ContentDaypart.AFTERNOON,
                        ContentDaypart.EVENING,
                        ContentDaypart.LATE_NIGHT,
                        ContentDaypart.MORNING,
                    )
                ContentDaypart.EVENING ->
                    listOf(
                        ContentDaypart.EVENING,
                        ContentDaypart.LATE_NIGHT,
                        ContentDaypart.MORNING,
                        ContentDaypart.AFTERNOON,
                    )
                ContentDaypart.LATE_NIGHT ->
                    listOf(
                        ContentDaypart.LATE_NIGHT,
                        ContentDaypart.MORNING,
                        ContentDaypart.AFTERNOON,
                        ContentDaypart.EVENING,
                    )
            }
        return order.flatMap(::forDaypart)
    }

    fun titleForId(id: String): String? = byId[id]?.title

    fun daypartForHour(hourOfDay: Int): ContentDaypart =
        when (hourOfDay) {
            in 5..11 -> ContentDaypart.MORNING
            in 12..16 -> ContentDaypart.AFTERNOON
            in 17..22 -> ContentDaypart.EVENING
            else -> ContentDaypart.LATE_NIGHT
        }
}
