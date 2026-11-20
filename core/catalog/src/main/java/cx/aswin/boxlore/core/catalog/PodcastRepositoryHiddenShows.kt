package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.logic.SemanticSearchGroupedResult
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.HiddenShows
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.withoutHiddenShowEpisodes
import cx.aswin.boxlore.core.model.withoutHiddenShows
import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * Discovery answers without the shows the listener hid ([PodcastRepository.hiddenShowIds]). Each
 * discovery read filters on return, after its caches and last good copies, so a show hidden since an
 * answer was kept is left out too. Show search (Find a show) is not filtered: it is how a listener
 * finds a hidden show to unhide it. Explore's paged chart ([PodcastRepository.getTrendingPodcastsOrNull])
 * is filtered on screen instead, so its pages keep their size and the chart's paging stays right.
 */

/** [PodcastRepository.hiddenShows], or none when it can't be read, so a failed read never empties discovery. */
internal suspend fun PodcastRepository.hiddenShowIds(): Set<String> = try {
    hiddenShows()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.w("PodcastRepository", "Couldn't read the hidden shows", e)
    emptySet()
}

/** A stream of chart pages without hidden shows ([PodcastRepository.getTrendingPodcastsStream]). */
internal fun PodcastRepository.withoutHiddenPodcasts(podcasts: Flow<List<Podcast>>): Flow<List<Podcast>> = podcasts.map { withoutHiddenPodcasts(it) }

internal suspend fun PodcastRepository.withoutHiddenPodcasts(podcasts: List<Podcast>): List<Podcast> =
    if (podcasts.isEmpty()) podcasts else podcasts.withoutHiddenShows(hiddenShowIds())

internal suspend fun PodcastRepository.withoutHiddenEpisodes(episodes: List<Episode>): List<Episode> =
    if (episodes.isEmpty()) episodes else episodes.withoutHiddenShowEpisodes(hiddenShowIds())

internal suspend fun PodcastRepository.withoutHiddenShows(recommendations: HomeRecommendations): HomeRecommendations =
    if (recommendations.items.isEmpty()) recommendations else recommendations.copy(items = withoutHiddenEpisodes(recommendations.items))

internal suspend fun PodcastRepository.withoutHiddenShows(data: BecauseYouLikeData): BecauseYouLikeData {
    val hidden = hiddenShowIds()
    if (hidden.isEmpty()) return data
    return BecauseYouLikeData(data.podcasts.withoutHiddenShows(hidden), data.episodes.withoutHiddenShowEpisodes(hidden))
}

internal suspend fun PodcastRepository.withoutHiddenShows(data: HomeBootstrapData): HomeBootstrapData {
    val hidden = hiddenShowIds()
    if (hidden.isEmpty()) return data
    return data.copy(
        trending = data.trending.withoutHiddenShows(hidden),
        curatedVibes = data.curatedVibes.mapValues { (_, podcasts) -> podcasts.withoutHiddenShows(hidden) },
        recommendations = data.recommendations.withoutHiddenShowEpisodes(hidden),
    )
}

internal suspend fun PodcastRepository.withoutHiddenShows(vibes: Map<String, List<Podcast>>): Map<String, List<Podcast>> {
    val hidden = hiddenShowIds()
    if (hidden.isEmpty()) return vibes
    return vibes.mapValues { (_, podcasts) -> podcasts.withoutHiddenShows(hidden) }
}

internal suspend fun PodcastRepository.withoutHiddenShows(result: SemanticSearchGroupedResult): SemanticSearchGroupedResult {
    val hidden = hiddenShowIds()
    if (hidden.isEmpty()) return result
    return SemanticSearchGroupedResult(result.podcasts.withoutHiddenShows(hidden), result.episodes.withoutHiddenShowEpisodes(hidden))
}

/**
 * This curiosity page without the question of the day and category shows of hidden shows. The card
 * stack keeps every card: an empty stack means the deck has ended, so Lore leaves hidden shows' cards
 * out itself after reading the page.
 */
internal fun CuratedCuriosityResponseDto.withoutHiddenShows(hidden: Set<String>): CuratedCuriosityResponseDto {
    if (hidden.isEmpty()) return this
    return copy(
        questionOfTheDay = questionOfTheDay?.takeUnless { HiddenShows.hidesShow(hidden, it.episode.feedId?.toString()) },
        categories = categories.map { category -> category.copy(shows = category.shows.filterNot { HiddenShows.hidesShow(hidden, it.id.toString()) }) },
    )
}

/**
 * The hidden Podcast Index shows a `/v2` recommendation request names as `excludedPodcastIds`: the
 * positive numeric IDs, at most [MAX_RECOMMENDATION_IDS]. `rss:` shows can't be sent; the phone
 * filters them, and any past the cap, from the answer.
 */
internal fun hiddenPodcastIndexIds(hidden: Set<String>): List<Long> = hidden
    .mapNotNull(String::toLongOrNull)
    .filter { it > 0L }
    .sorted()
    .take(MAX_RECOMMENDATION_IDS)
