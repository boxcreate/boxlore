package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.logic.EpisodeSupplementEligibility
import cx.aswin.boxlore.feature.info.logic.EpisodeSupplementMergeLogic

/**
 * PI episode-list supplement helpers for [PodcastInfoViewModel].
 * Keeps feed hydrate / merge off the main ViewModel file (1000-line budget).
 */
internal class PodcastInfoSupplementSupport(
    private val repository: PodcastRepository,
    private val episodeSupplementPort: EpisodeSupplementPort,
) {
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
        val supplements =
            if (podcast.isRss) {
                emptyList()
            } else {
                episodeSupplementPort.getEpisodesForPodcast(
                    podcastIndexId = podcast.id,
                    podcastTitle = podcast.title,
                    podcastImageUrl = podcast.imageUrl,
                    podcastGenre = podcast.genre,
                    podcastArtist = podcast.artist,
                )
            }
        val merged =
            if (podcast.isRss) {
                piEpisodes
            } else {
                EpisodeSupplementMergeLogic.merge(piEpisodes, supplements, state.currentSort)
            }
        return state.copy(
            episodes = merged,
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
    ): PodcastInfoUiState.Success {
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
                    is EpisodeSupplementOutcome.Failure -> outcome.message
                }
            }
        return remountWithSupplements(
            state = state,
            piEpisodes = state.piEpisodes,
            userMessage = message,
            isFetchingFromFeed = false,
        )
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
        return EpisodeSupplementMergeLogic.unionSearchResults(networkResults, supplementMatches)
    }

    companion object {
        const val SUPPLEMENT_BASELINE_LIMIT = 1000
    }
}
