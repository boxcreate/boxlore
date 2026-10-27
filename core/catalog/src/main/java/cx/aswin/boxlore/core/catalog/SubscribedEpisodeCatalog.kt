package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import kotlinx.coroutines.CancellationException

/**
 * Follows feeds that moved for good on every catalog refresh path (the show page, pushes,
 * auto-download, restores, the ingest after a follow), not only the background pass: a refresh of
 * a Podcast Index show checks for a confirmed move ([RefreshRequest.detectMove]), and a move it
 * finds is saved through [adopt] (the one address writer) only while the show still holds the
 * address that was read ([RefreshRequest.feedUrl]): a push or a restore may read another address,
 * and a redirect from it is no move of the saved one. [enabled] is the
 * `feed-fix-moved-feed-off` switch; [showTitle] gives the stored title a move is compared with
 * when the caller sent none. Requests that already ask for move detection are left to their caller.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: redirects and `itunes:new-feed-url`).
 * COMPAT(own-ids): followed shows stay reachable. Permanent: never remove (MIGRATION.md §5).
 */
class FeedMoveFollower(
    val enabled: () -> Boolean,
    val showTitle: suspend (podcastId: String) -> String?,
    val adopt: suspend (podcastId: String, readFrom: String, movedTo: String) -> Unit,
) {
    companion object {
        /** Follows no moves (tests, and callers built without the app's wiring). */
        val NONE = FeedMoveFollower(enabled = { false }, showTitle = { null }, adopt = { _, _, _ -> })
    }
}

/** Routes by show identity, never by episode sign (PI supplements also have negative IDs). */
@Suppress("TooManyFunctions") // Routes the complete read/refresh port without changing either storage contract.
class SubscribedEpisodeCatalog(
    private val catalog: LocalEpisodeCatalogPort,
    private val rss: LocalEpisodeCatalogPort,
    private val isSubscribed: suspend (String) -> Boolean = { false },
    private val feedMoves: FeedMoveFollower = FeedMoveFollower.NONE,
) : LocalEpisodeCatalogPort {
    private fun source(id: String) = if (id.startsWith("rss:")) rss else catalog

    override suspend fun isReady(podcastId: String) = source(podcastId).isReady(podcastId)
    override suspend fun getPage(podcastId: String, limit: Int, offset: Int, sort: String, meta: PodcastMeta) = source(podcastId).getPage(podcastId, limit, offset, sort, meta)
    override suspend fun getWindow(podcastId: String, sort: String, bound: Int, aroundEpisodeId: String?, meta: PodcastMeta) = source(podcastId).getWindow(podcastId, sort, bound, aroundEpisodeId, meta)
    override suspend fun getEpisode(episodeId: String, meta: PodcastMeta): cx.aswin.boxlore.core.model.Episode? {
        val piEpisode = catalog.getEpisode(episodeId, meta) ?: return rss.getEpisode(episodeId, meta)
        val rssEpisode = rss.getEpisode(episodeId, meta)
        // A retired PI compatibility row may share a negative ID with the active RSS source.
        if (rssEpisode?.podcastId?.let { isSubscribed(it) } == true) return rssEpisode
        return piEpisode
    }
    override suspend fun findByCatalogKey(podcastId: String, guid: String?, enclosureUrl: String?, meta: PodcastMeta) = source(podcastId).findByCatalogKey(podcastId, guid, enclosureUrl, meta)
    override suspend fun search(podcastId: String, query: String, meta: PodcastMeta) = source(podcastId).search(podcastId, query, meta)
    override suspend fun newest(podcastId: String, meta: PodcastMeta) = source(podcastId).newest(podcastId, meta)
    override suspend fun count(podcastId: String) = source(podcastId).count(podcastId)
    override suspend fun refresh(request: RefreshRequest): RefreshOutcome {
        val id = request.podcastIndexId
        if (id.startsWith("rss:") || request.detectMove || !feedMoves.enabled()) return source(id).refresh(request)
        val title = request.meta.title?.takeIf(String::isNotBlank) ?: feedMoves.showTitle(id)
        val outcome = catalog.refresh(request.copy(detectMove = true, meta = request.meta.copy(title = title)))
        val movedTo = (outcome as? RefreshOutcome.Success)?.movedTo ?: return outcome
        try {
            feedMoves.adopt(id, request.feedUrl.trim(), movedTo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save the new address of $id", e)
        }
        return outcome
    }
    override suspend fun isRefreshDue(podcastId: String, feedUrl: String, nowMillis: Long) = source(podcastId).isRefreshDue(podcastId, feedUrl, nowMillis)
    override suspend fun isPublisherFeedUnchanged(podcastId: String, feedUrl: String) = source(podcastId).isPublisherFeedUnchanged(podcastId, feedUrl)
    override suspend fun markFeedUrlLookup(podcastId: String, atMillis: Long) = source(podcastId).markFeedUrlLookup(podcastId, atMillis)
    override suspend fun lastFeedUrlLookupAt(podcastId: String) = source(podcastId).lastFeedUrlLookupAt(podcastId)
    override suspend fun setUnsubscribedTtl(podcastId: String, ttlExpiresAt: Long?) = source(podcastId).setUnsubscribedTtl(podcastId, ttlExpiresAt)
    override suspend fun sweepExpired(nowMillis: Long) {
        catalog.sweepExpired(nowMillis)
        rss.sweepExpired(nowMillis)
    }

    private companion object {
        const val TAG = "SubscribedEpisodeCatalog"
    }
}
