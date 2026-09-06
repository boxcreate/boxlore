package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.catalog.content.CuratedMoods
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Pure Explore browse helpers extracted from [cx.aswin.boxlore.feature.explore.ExploreViewModel].
 */
object ExploreBrowseLogic {
    /** Mood chips for For You — same catalog as Home daypart rails, time-sorted. */
    fun vibesForHour(hourOfDay: Int): List<Pair<String, String>> = CuratedMoods.forHourOfDay(hourOfDay).map { it.id to it.title }

    fun filterPodcastsBySubstring(query: String, podcasts: Collection<Podcast>): List<Podcast> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return podcasts.filter { podcast ->
            podcast.title.contains(trimmed, ignoreCase = true) ||
                podcast.artist.contains(trimmed, ignoreCase = true)
        }.sortedBy { it.title }
    }

    fun <T> mergeUniqueById(existing: List<T>, incoming: List<T>, idOf: (T) -> String): List<T> {
        val existingIds = existing.map(idOf).toSet()
        return existing + incoming.filter { idOf(it) !in existingIds }
    }

    fun projectDisplayList(podcasts: List<Podcast>): List<Podcast> =
        cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy.deduplicateById(podcasts) { it.id }

    fun projectGridItems(
        displayList: List<Podcast>,
        isSearching: Boolean,
        hasCurrentVibe: Boolean,
    ): List<Podcast> =
        if (!isSearching && displayList.isNotEmpty() && !hasCurrentVibe) {
            displayList.drop(1)
        } else {
            displayList
        }

    fun filterAlsoFound(
        alsoFound: List<Podcast>,
        catalog: List<Podcast>,
    ): List<Podcast> {
        val catalogIds = catalog.mapNotNull { it.id.trim().takeIf(String::isNotEmpty) }.toSet()
        val filtered = alsoFound.filter { it.id.trim().takeIf(String::isNotEmpty) !in catalogIds }
        return cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy.deduplicateById(filtered) { it.id }
    }

    fun episodeToSearchPodcast(episode: Episode): Podcast = Podcast(
        id = episode.podcastId.orEmpty(),
        title = episode.podcastTitle.orEmpty(),
        artist = episode.podcastArtist.orEmpty(),
        imageUrl = episode.podcastImageUrl ?: episode.imageUrl.orEmpty(),
        genre = episode.podcastGenre.orEmpty(),
        latestEpisode = episode,
    )
}
