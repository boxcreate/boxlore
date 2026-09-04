package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.catalog.content.CuratedMood
import cx.aswin.boxlore.core.catalog.content.CuratedMoods
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeEditorialIcon
import cx.aswin.boxlore.feature.home.HomeEditorialRow

internal data class HomeEditorialRowDefinition(
    val providerId: String,
    val title: String,
    val subtitle: String,
    val icon: HomeEditorialIcon,
)

internal fun editorialRowDefinitionsFor(daypart: ContentDaypart): List<HomeEditorialRowDefinition> = CuratedMoods.forDaypart(daypart).map { it.toHomeDefinition() }

internal fun buildHomeEditorialRows(
    daypart: ContentDaypart,
    podcastsByProvider: Map<String, List<Podcast>>,
    maximumItemsPerRow: Int = 8,
): List<HomeEditorialRow> {
    if (maximumItemsPerRow <= 0) return emptyList()
    val seenPodcastIds = mutableSetOf<String>()
    val seenEpisodeIds = mutableSetOf<String>()
    return editorialRowDefinitionsFor(daypart).mapNotNull { definition ->
        val podcasts =
            podcastsByProvider[definition.providerId]
                .orEmpty()
                .asSequence()
                .filter { podcast ->
                    val episode = podcast.latestEpisode
                    episode != null &&
                        episode.id.isNotBlank() &&
                        episode.audioUrl.isNotBlank() &&
                        podcast.id.isNotBlank() &&
                        podcast.id !in seenPodcastIds &&
                        episode.id !in seenEpisodeIds
                }.onEach { podcast ->
                    seenPodcastIds += podcast.id
                    podcast.latestEpisode?.id?.let(seenEpisodeIds::add)
                }.take(maximumItemsPerRow)
                .toList()
        if (podcasts.isEmpty()) {
            null
        } else {
            HomeEditorialRow(
                providerId = definition.providerId,
                title = definition.title,
                subtitle = definition.subtitle,
                icon = definition.icon,
                podcasts = podcasts,
            )
        }
    }
}

private fun CuratedMood.toHomeDefinition(): HomeEditorialRowDefinition = HomeEditorialRowDefinition(
    providerId = id,
    title = title,
    subtitle = subtitle,
    icon = homeEditorialIconForMood(id),
)

private fun homeEditorialIconForMood(moodId: String): HomeEditorialIcon = when (moodId) {
    "morning_news" -> HomeEditorialIcon.HEADLINES
    "morning_motivation" -> HomeEditorialIcon.UPLIFTING
    "business_insider" -> HomeEditorialIcon.BUSINESS
    "science_explainer" -> HomeEditorialIcon.SCIENCE
    "tech_culture" -> HomeEditorialIcon.TECHNOLOGY
    "creative_focus" -> HomeEditorialIcon.CREATIVITY
    "comedy_gold" -> HomeEditorialIcon.COMEDY
    "tv_film_buff" -> HomeEditorialIcon.SCREEN
    "sports_fan" -> HomeEditorialIcon.SPORTS
    "true_crime_sleep" -> HomeEditorialIcon.TRUE_CRIME
    "history_buff" -> HomeEditorialIcon.HISTORY
    "mystery_thriller" -> HomeEditorialIcon.MYSTERY
    else -> HomeEditorialIcon.HEADLINES
}
