package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.EpisodeLinkResult

/**
 * Pure decisions of [EpisodeInfoViewModel.loadEpisode] for pages opened from a link, a push or a
 * deep link with only an episode ID and maybe its show.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
 */
internal object EpisodeLoadRequests {
    /** What a page load was asked for: the episode ID and the show the caller named ("" for none). */
    data class Request(val episodeId: String, val podcastId: String)

    /**
     * True when a new request for [episodeId] adds nothing to the [previous] one: the page already
     * shows the episode, or the previous load of the same ID is still running (or ended on "not
     * found" or mid feed read) and the new request names no show it lacked. A request that brings
     * the show restarts the load, since only the show lets the page read the right feed.
     */
    fun isCovered(previous: Request?, previousActive: Boolean, state: EpisodeInfoUiState, episodeId: String, podcastId: String,): Boolean {
        if (state is EpisodeInfoUiState.Success && state.requestedEpisodeId == episodeId) return true
        if (previous == null || previous.episodeId != episodeId) return false
        val bringsShow = podcastId.isNotEmpty() && podcastId != previous.podcastId
        if (bringsShow) return false
        return previousActive || state is EpisodeInfoUiState.NotFound || state is EpisodeInfoUiState.ReadingFeed
    }

    /**
     * The state for a page whose episode couldn't be resolved, or null to go on with what the caller
     * passed in ([title] or [audioUrl] set) or with the resolved episode. Nothing listing it is
     * "not found" (with [podcastId] for its show); an unreachable source is the load error.
     */
    fun unresolvedState(link: EpisodeLinkResult, podcastId: String, title: String, audioUrl: String,): EpisodeInfoUiState? {
        if (link is EpisodeLinkResult.Found || title.isNotEmpty() || audioUrl.isNotEmpty()) return null
        return when (link) {
            EpisodeLinkResult.NotFound -> EpisodeInfoUiState.NotFound(podcastId.takeUnless { it == UNKNOWN_SHOW }.orEmpty())
            else -> EpisodeInfoUiState.Error
        }
    }

    /** The placeholder show ID older code stored for an episode with no show. */
    private const val UNKNOWN_SHOW = "unknown"
}
