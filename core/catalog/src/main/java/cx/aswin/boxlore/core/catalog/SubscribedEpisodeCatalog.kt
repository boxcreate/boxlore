package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta

/** Routes by show identity, never by episode sign (PI supplements also have negative IDs). */
@Suppress("TooManyFunctions") // Routes the complete read/refresh port without changing either storage contract.
class SubscribedEpisodeCatalog(
    private val catalog: LocalEpisodeCatalogPort,
    private val rss: LocalEpisodeCatalogPort,
    private val isSubscribed: suspend (String) -> Boolean = { false },
) : LocalEpisodeCatalogPort by catalog {
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
    override suspend fun refresh(request: LocalEpisodeCatalogPort.RefreshRequest) = source(request.podcastIndexId).refresh(request)
    override suspend fun isRefreshDue(podcastId: String, feedUrl: String, nowMillis: Long) = source(podcastId).isRefreshDue(podcastId, feedUrl, nowMillis)
    override suspend fun isPublisherFeedUnchanged(podcastId: String, feedUrl: String) = source(podcastId).isPublisherFeedUnchanged(podcastId, feedUrl)
    override suspend fun markFeedUrlLookup(podcastId: String, atMillis: Long) = source(podcastId).markFeedUrlLookup(podcastId, atMillis)
    override suspend fun lastFeedUrlLookupAt(podcastId: String) = source(podcastId).lastFeedUrlLookupAt(podcastId)
    override suspend fun setUnsubscribedTtl(podcastId: String, ttlExpiresAt: Long?) = source(podcastId).setUnsubscribedTtl(podcastId, ttlExpiresAt)
}
