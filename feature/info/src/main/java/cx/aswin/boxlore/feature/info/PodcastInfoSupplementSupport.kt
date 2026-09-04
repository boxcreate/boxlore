package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.logic.EpisodeSupplementEligibility
import cx.aswin.boxlore.feature.info.logic.EpisodeSupplementMergeLogic

/**
 * PI episode-list supplement helpers for [PodcastInfoViewModel].
 * List merge lives in [PodcastRepository]; this type owns the chip, opt-in, and
 * post-feed reload so newly cached extras appear without a second merge pass.
 */
internal class PodcastInfoSupplementSupport(
    private val episodeSupplementPort: EpisodeSupplementPort,
    private val loadPage: suspend (
        podcastId: String,
        limit: Int,
        offset: Int,
        sort: String,
    ) -> PodcastRepository.EpisodePage,
    /** PI rows only — must not include cached feed extras or a refresh wipes them. */
    private val loadPiBaseline: suspend (podcastId: String) -> List<Episode>,
    private val isCatalogReady: suspend (podcastId: String) -> Boolean = { false },
) {
    constructor(
        episodeSupplementPort: EpisodeSupplementPort,
        loadPage: suspend (
            podcastId: String,
            limit: Int,
            offset: Int,
            sort: String,
        ) -> PodcastRepository.EpisodePage,
    ) : this(
        episodeSupplementPort,
        loadPage,
        loadPiBaseline = { id ->
            loadPage(id, SUPPLEMENT_BASELINE_LIMIT, 0, "oldest").episodes
        },
        isCatalogReady = { false },
    )

    constructor(
        repository: PodcastRepository,
        episodeSupplementPort: EpisodeSupplementPort,
    ) : this(
        episodeSupplementPort,
        { id, limit, offset, sort ->
            repository.getEpisodesPaginated(id, limit, offset, sort)
        },
        { id ->
            repository
                .getEpisodesPaginated(
                    feedId = id,
                    limit = SUPPLEMENT_BASELINE_LIMIT,
                    offset = 0,
                    sort = "oldest",
                    mergeSupplements = false,
                ).episodes
        },
        { id -> repository.localEpisodeCatalog?.isReady(id) == true },
    )

    data class MissingEpisodesRefresh(
        val state: PodcastInfoUiState.Success,
        /** Promote into Room `latestEpisode` for Home filter chips when subscribed. */
        val libraryTip: Episode?,
        /** Reloaded page `sourceCount` so [PodcastInfoViewModel] can reset pagination. */
        val pageSourceCount: Int? = null,
    )

    suspend fun remountWithSupplements(
        state: PodcastInfoUiState.Success,
        piEpisodes: List<Episode>,
        hasMoreEpisodes: Boolean = state.hasMoreEpisodes,
        isLoadingMore: Boolean = state.isLoadingMore,
        userMessage: String? = state.userMessage,
        isFetchingFromFeed: Boolean = false,
    ): PodcastInfoUiState.Success {
        val podcast = state.podcast
        val eligible = EpisodeSupplementEligibility.canLoadMissingEpisodes(podcast)
        val catalogReady = !podcast.isRss && isCatalogReady(podcast.id)
        val optedIn =
            !podcast.isRss && episodeSupplementPort.hasDirectFeedOptIn(podcast.id)
        val chip =
            when {
                catalogReady -> DirectFeedChipState.Hidden
                isFetchingFromFeed -> DirectFeedChipState.Fetching
                optedIn -> DirectFeedChipState.Updated
                state.isSubscribed -> DirectFeedChipState.Hidden
                !eligible -> DirectFeedChipState.Hidden
                else -> DirectFeedChipState.Offer
            }
        val display =
            if (podcast.isRss) {
                piEpisodes
            } else {
                EpisodeSupplementMergeLogic.sorted(piEpisodes, state.currentSort)
            }
        return state.copy(
            episodes = display,
            piEpisodes = piEpisodes,
            hasMoreEpisodes = hasMoreEpisodes,
            isLoadingMore = isLoadingMore,
            userMessage = userMessage,
            directFeedChip = chip,
        )
    }

    /**
     * @param announceResult when true (user tapped opt-in), surface a snackbar message;
     *   silent refresh on page open should pass false.
     */
    suspend fun refreshMissingEpisodes(
        state: PodcastInfoUiState.Success,
        announceResult: Boolean,
    ): MissingEpisodesRefresh {
        val podcast = state.podcast
        val baselineEpisodes = loadPiBaseline(podcast.id)
        val outcome =
            episodeSupplementPort.refreshFromFeed(
                podcastIndexId = podcast.id,
                feedUrl = podcast.feedUrl.orEmpty(),
                baselineEpisodes = baselineEpisodes,
                podcastTitle = podcast.title,
                podcastImageUrl = podcast.imageUrl,
                podcastGenre = podcast.genre,
                podcastArtist = podcast.artist,
            )
        val message =
            if (!announceResult) {
                null
            } else {
                when (outcome) {
                    is EpisodeSupplementOutcome.Success ->
                        if (outcome.addedCount > 0) {
                            "Added ${outcome.addedCount} episodes"
                        } else {
                            "Episode list updated"
                        }
                    is EpisodeSupplementOutcome.NoDisconnect -> "Episode list updated"
                    is EpisodeSupplementOutcome.Failure -> outcome.message
                }
            }
        val tip =
            (outcome as? EpisodeSupplementOutcome.Success)?.newestFeedEpisode
        val reloaded = reloadDisplayPage(state)
        return MissingEpisodesRefresh(
            state =
            remountWithSupplements(
                state = state,
                piEpisodes = reloaded.episodes,
                hasMoreEpisodes = reloaded.hasMore,
                userMessage = message,
                isFetchingFromFeed = false,
            ),
            libraryTip = tip,
            pageSourceCount = reloaded.sourceCount,
        )
    }

    /**
     * On subscribe: persist the publisher feed as the local catalog. Do not compare
     * PI vs RSS for extras opt-in — subscribed shows are feed-first.
     */
    suspend fun autoOptInOnSubscribeIfDisconnected(
        state: PodcastInfoUiState.Success,
        announce: Boolean = false,
    ): MissingEpisodesRefresh? {
        val podcast = state.podcast
        if (podcast.isRss) return null
        if (isCatalogReady(podcast.id)) return null
        if (!EpisodeSupplementEligibility.canLoadMissingEpisodes(podcast)) return null
        if (episodeSupplementPort.hasDirectFeedOptIn(podcast.id)) return null

        val outcome =
            episodeSupplementPort.optInFromFeedIfDisconnected(
                podcastIndexId = podcast.id,
                feedUrl = podcast.feedUrl.orEmpty(),
                baselineEpisodes = state.piEpisodes,
                podcastTitle = podcast.title,
                podcastImageUrl = podcast.imageUrl,
                podcastGenre = podcast.genre,
                podcastArtist = podcast.artist,
            )
        return when (outcome) {
            is EpisodeSupplementOutcome.NoDisconnect -> null
            is EpisodeSupplementOutcome.Failure -> {
                if (!announce) {
                    null
                } else {
                    MissingEpisodesRefresh(
                        state = state.copy(userMessage = outcome.message),
                        libraryTip = null,
                    )
                }
            }
            is EpisodeSupplementOutcome.Success -> {
                val reloaded = reloadDisplayPage(state)
                MissingEpisodesRefresh(
                    state =
                    remountWithSupplements(
                        state = state,
                        piEpisodes = reloaded.episodes,
                        hasMoreEpisodes = reloaded.hasMore,
                        userMessage =
                        if (announce) {
                            if (outcome.addedCount > 0) {
                                "Added ${outcome.addedCount} episodes from the feed"
                            } else {
                                "Episode list updated from the feed"
                            }
                        } else {
                            null
                        },
                        isFetchingFromFeed = false,
                    ),
                    libraryTip = outcome.newestFeedEpisode,
                    pageSourceCount = reloaded.sourceCount,
                )
            }
        }
    }

    suspend fun shouldRefreshOnOpen(
        podcastId: String,
        isRss: Boolean,
    ): Boolean = !isRss &&
        !isCatalogReady(podcastId) &&
        episodeSupplementPort.hasDirectFeedOptIn(podcastId)

    suspend fun unionSearch(
        feedId: String,
        query: String,
        networkResults: List<Episode>,
        meta: PodcastListMeta,
        isRss: Boolean,
    ): List<Episode> {
        if (isRss) return networkResults
        val supplementMatches =
            episodeSupplementPort.search(
                podcastIndexId = feedId,
                query = query,
                podcastTitle = meta.title,
                podcastImageUrl = meta.imageUrl,
                podcastGenre = meta.genre,
                podcastArtist = meta.artist,
            )
        // Preferred list wins on identity match so enriched extras beat a PI duplicate.
        return EpisodeSupplementMergeLogic.unionSearchResults(
            preferred = supplementMatches,
            fallback = networkResults,
        )
    }

    private suspend fun reloadDisplayPage(state: PodcastInfoUiState.Success): PodcastRepository.EpisodePage {
        val oldest = state.currentSort == EpisodeSort.OLDEST
        return loadPage(
            state.podcast.id,
            if (oldest) OLDEST_PAGE_SIZE else DISPLAY_PAGE_SIZE,
            0,
            if (oldest) "oldest" else "newest",
        )
    }

    companion object {
        /**
         * Podcast Index paginated max is 1000. Using that as the matching baseline
         * avoids treating later-page PI episodes as feed-only extras.
         */
        const val SUPPLEMENT_BASELINE_LIMIT = 1000
        private const val DISPLAY_PAGE_SIZE = 20
        private const val OLDEST_PAGE_SIZE = 200
    }
}

internal data class PodcastListMeta(
    val title: String?,
    val imageUrl: String?,
    val genre: String?,
    val artist: String?,
)
