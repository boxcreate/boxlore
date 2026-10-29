package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.rss.RenumberedShowIds
import cx.aswin.boxlore.feature.info.PodcastInfoUiState

/**
 * The show a page shows for its route: the route's own show, a followed `rss:` feed linked to it,
 * or the show Podcast Index renumbered it to ([shownPodcastId]). Empty before the first load.
 */
data class PodcastInfoPageShow(val routePodcastId: String = "", val shownPodcastId: String = "")

/**
 * Show-page rules for a show Podcast Index renumbered: the route's show (the old number) and the
 * show it now answers as are one page (docs/EPISODE_IDS.md §5, renumbered shows).
 */
internal object RenumberedShowPageLogic {
    /** True when details asked for [requestedId] answered as [answeredId], another numeric show ID. */
    fun isRenumbered(requestedId: String, answeredId: String): Boolean = RenumberedShowIds.isRenumbering(requestedId, answeredId)

    /**
     * The page is followed when the show it shows is, the route's show it replaced still is, or a
     * show recorded as renumbered into it still is (a follow not moved yet, whichever number the
     * page was opened with): Subscribe is never offered for a show the listener follows under its
     * old number, and the page takes the followed paths (no "Missing episodes?" offer).
     */
    fun isFollowed(shownFollowed: Boolean, routeFollowed: Boolean, formerFollowed: Boolean = false): Boolean =
        shownFollowed || routeFollowed || formerFollowed

    /**
     * What the page shows before the show's details arrive: its stored row, else the details
     * answer that named this show for a renumbered route ([answered], when it is [shownPodcastId]),
     * so a renumbered page never drops back to loading, or to an error when the next request fails.
     */
    fun shownBeforeDetails(stored: Podcast?, answered: Podcast?, shownPodcastId: String): Podcast? =
        stored ?: answered?.takeIf { it.id == shownPodcastId }

    /**
     * The podcast whose artwork themes the page for [routePodcastId]: the loaded podcast when it is
     * the route's show, or the show [page] says this route resolved to. A podcast left from another
     * route (the page moving between shows) is never used.
     */
    fun artworkPodcast(state: PodcastInfoUiState, routePodcastId: String, page: PodcastInfoPageShow): Podcast? {
        val podcast = (state as? PodcastInfoUiState.Success)?.podcast ?: return null
        if (podcast.id == routePodcastId) return podcast
        val resolvedForRoute = page.routePodcastId == routePodcastId && page.shownPodcastId.isNotEmpty() && page.shownPodcastId == podcast.id
        return podcast.takeIf { resolvedForRoute }
    }
}
