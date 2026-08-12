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
    private val repository: PodcastRepository,
    private val episodeSupplementPort: EpisodeSupplementPort,
) {
    data class MissingEpisodesRefresh(
        val state: PodcastInfoUiState.Success,
        /** Promote into Room `latestEpisode` for Home filter chips when subscribed. */
        val libraryTip: Episode?,
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
        val optedIn =
            !podcast.isRss && episodeSupplementPort.hasDirectFeedOptIn(podcast.id)
        val chip =
            when {
                !eligible && !optedIn -> DirectFeedChipState.Hidden
                isFetchingFromFeed -> DirectFeedChipState.Fetching
                optedIn -> DirectFeedChipState.Updated
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
        val baseline =
            repository.getEpisodesPaginated(
                podcast.id,
                SUPPLEMENT_BASELINE_LIMIT,
                0,
                "oldest",
            )
        val outcome =
            episodeSupplementPort.refreshFromFeed(
                podcastIndexId = podcast.id,
                feedUrl = podcast.feedUrl.orEmpty(),
                baselineEpisodes = baseline.episodes,
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
        )
    }

    /**
     * On subscribe: compare the loaded PI page to the publisher feed and auto-opt-in
     * only when there is a disconnect. Silent — no snackbar unless [announce] is true.
     */
    suspend fun autoOptInOnSubscribeIfDisconnected(
        state: PodcastInfoUiState.Success,
        announce: Boolean = false,
    ): MissingEpisodesRefresh? {
        val podcast = state.podcast
        if (podcast.isRss) return null
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
                )
            }
        }
    }

    suspend fun shouldRefreshOnOpen(podcastId: String, isRss: Boolean): Boolean =
        !isRss && episodeSupplementPort.hasDirectFeedOptIn(podcastId)

    suspend fun unionSearch(
        feedId: String,
        query: String,
        networkResults: List<Episode>,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
        isRss: Boolean,
    ): List<Episode> {
        if (isRss) return networkResults
        val supplementMatches =
            episodeSupplementPort.search(
                podcastIndexId = feedId,
                query = query,
                podcastTitle = podcastTitle,
                podcastImageUrl = podcastImageUrl,
                podcastGenre = podcastGenre,
                podcastArtist = podcastArtist,
            )
        // Supplement matches first so enriched extras win if the repository already unioned.
        return EpisodeSupplementMergeLogic.unionSearchResults(supplementMatches, networkResults)
    }

    private suspend fun reloadDisplayPage(
        state: PodcastInfoUiState.Success,
    ): PodcastRepository.EpisodePage {
        val oldest = state.currentSort == EpisodeSort.OLDEST
        return repository.getEpisodesPaginated(
            state.podcast.id,
            if (oldest) OLDEST_PAGE_SIZE else DISPLAY_PAGE_SIZE,
            0,
            if (oldest) "oldest" else "newest",
        )
    }

    companion object {
        const val SUPPLEMENT_BASELINE_LIMIT = 1000
        private const val DISPLAY_PAGE_SIZE = 20
        private const val OLDEST_PAGE_SIZE = 200
    }
}
