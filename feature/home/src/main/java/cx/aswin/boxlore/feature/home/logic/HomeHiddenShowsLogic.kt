package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.HiddenShows
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.withoutHiddenShowEpisodes
import cx.aswin.boxlore.core.model.withoutHiddenShows
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import cx.aswin.boxlore.feature.home.HomeUiState

/**
 * Home without the shows the listener hid. Applied to what Home already ranked and built, so hiding
 * or unhiding a show never starts a ranking pass or a Daily Mix rebuild (HomeRankingBudgetTest).
 */
internal object HomeHiddenShowsLogic {
    fun editorialRows(rows: List<HomeEditorialRow>, hidden: Set<String>): List<HomeEditorialRow> =
        if (hidden.isEmpty()) rows else rows.map { row -> row.copy(podcasts = row.podcasts.withoutHiddenShows(hidden)) }

    /** The kept Daily Mix without hidden shows' episodes; null stays null so the mix is rebuilt as before. */
    fun mixtape(cache: HomeMixtapeCache?, hidden: Set<String>): HomeMixtapeCache? {
        if (cache == null || hidden.isEmpty()) return cache
        val podcasts = cache.podcasts.withoutHiddenShows(hidden)
        val episodes = cache.episodes.withoutHiddenShowEpisodes(hidden)
        if (podcasts.size == cache.podcasts.size && episodes.size == cache.episodes.size) return cache
        return cache.copy(
            podcasts = podcasts,
            episodes = episodes,
            unplayedCount = podcasts.count { it.episodeStatus != EpisodeStatus.COMPLETED },
        )
    }

    /** True when Because you like would start from a hidden show; its picks are then left out too. */
    fun hidesSeed(seed: Podcast?, hidden: Set<String>): Boolean = seed != null && HiddenShows.hides(hidden, seed)

    /**
     * Because you like and the category list without hidden shows. A hidden seed hides the whole
     * row until Home picks another show for it.
     */
    fun discovery(state: HomeUiState, hidden: Set<String>): HomeUiState {
        if (hidden.isEmpty()) return state
        val seedHidden = hidesSeed(state.seemsToLikePodcast, hidden)
        return state.copy(
            discoverPodcasts = state.discoverPodcasts.withoutHiddenShows(hidden),
            seemsToLikePodcast = state.seemsToLikePodcast.takeUnless { seedHidden },
            becauseYouLikePodcasts = if (seedHidden) emptyList() else state.becauseYouLikePodcasts.withoutHiddenShows(hidden),
            becauseYouLikeRecommendations = if (seedHidden) emptyList() else state.becauseYouLikeRecommendations.withoutHiddenShowEpisodes(hidden),
        )
    }
}
