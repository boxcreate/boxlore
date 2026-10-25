package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Links the episodes of an RSS show the listener switched to with the episodes its Podcast Index
 * show holds listener state for ([ListenerStateMoves.switchedShowLinks]; decision D8;
 * docs/EPISODE_IDS.md §5). Nothing moves and no ID changes: only aliases are written.
 *
 * Linking is idempotent, so it runs both at the switch and again later, because links exist only
 * where they were written: after a restore from a backup or the cloud brings the RSS show back with
 * its `linkedPodcastIndexId`, after the RSS show is followed again, and after each read of its
 * feed ([relink]). A later run adds links for rows that arrived since, settles old IDs that now
 * have exact evidence, and turns an ambiguous link trusted once exact evidence exists; it never
 * downgrades or replaces another alias.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, re-key paths narrowed; decision D8).
 * COMPAT(own-ids): a show switched to its feed reads its old IDs' state. Permanent: never remove (MIGRATION.md §5).
 */
internal class SwitchedShowLinker(private val database: BoxLoreDatabase) {
    private val podcastDao get() = database.podcastDao()
    private val identityDao get() = database.episodeIdentityDao()

    /**
     * Links [rssEpisodes] to the episodes [podcastIndexId]'s history, queue and download rows hold.
     * Runs in the caller's transaction.
     *
     * @param feedProven the RSS feed is the Podcast Index show's own feed (same address or podcast GUID)
     */
    suspend fun link(podcastIndexId: String, rssEpisodes: List<RssEpisodeEntity>, feedProven: Boolean,) {
        if (rssEpisodes.isEmpty()) return
        val references = database.listenerReferences(podcastIndexId)
        if (references.isEmpty()) return
        val aliasesById = aliasesOf(references.map { it.episodeId })
        val plan =
            ListenerStateMoves.switchedShowLinks(
                podcastIndexId = podcastIndexId,
                references = references,
                aliasesById = aliasesById,
                rssEpisodes = rssEpisodes,
                epNs = namespaceOf(podcastIndexId),
                feedProven = feedProven,
                nowMs = System.currentTimeMillis(),
            )
        for (settled in plan.settled) {
            val key = settled.catalogKey ?: continue
            val hasOwner = identityDao.aliasesForKey(podcastIndexId, key).any { it.owner && it.state == EpisodeAliasState.TRUSTED }
            identityDao.upsertAlias(settled.copy(owner = !hasOwner))
        }
        val stored = aliasesOf(plan.links.map { it.aliasId })
        val inserts = ArrayList<EpisodeAliasEntity>()
        for (link in plan.links) {
            val existing = stored[link.aliasId]
            val write = ListenerStateMoves.linkWrite(existing, link) ?: continue
            if (existing == null) inserts += write else identityDao.upsertAlias(write)
        }
        inserts.chunked(MAX_IDS_PER_QUERY).forEach { identityDao.insertAliasesIfAbsent(it) }
    }

    /**
     * Links the followed RSS show [rssPodcastId] again with the Podcast Index show it replaced, if
     * any (its `linkedPodcastIndexId`), from its stored episodes. Does nothing for an RSS show the
     * listener doesn't follow (an RSS show the RSS follow upgrade retired names its new Podcast Index
     * show the same way, and its rows moved there with their IDs).
     */
    suspend fun relink(rssPodcastId: String) {
        val show = podcastDao.getPodcast(rssPodcastId)?.takeIf { it.isRss && it.isSubscribed } ?: return
        val podcastIndexId = show.linkedPodcastIndexId ?: return
        if (EpisodeRecipeId.showIdOrNull(podcastIndexId) == null) return
        val podcastIndexShow = podcastDao.getPodcast(podcastIndexId)
        if (podcastIndexShow?.isRss == true) return
        val feedUrl = show.feedUrl
        val feedProven =
            podcastIndexShow != null &&
                feedUrl != null &&
                RssSourceMatcher.feedIdentityMatches(feedUrl, show.podcastGuid, podcastIndexShow)
        database.withTransaction {
            link(podcastIndexId, database.rssEpisodeDao().getAllNewest(rssPodcastId), feedProven)
        }
    }

    /** [relink], logged instead of thrown: a failed link leaves every row as it was and is retried by the next one. */
    suspend fun relinkQuietly(rssPodcastId: String) {
        try {
            relink(rssPodcastId)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w(LOG_TAG, "Could not link $rssPodcastId to its Podcast Index show", error)
        }
    }

    /** The Podcast Index show's namespace as its catalog read uses it: the catalog's stored value, then the show's (decision D12). */
    private suspend fun namespaceOf(podcastIndexId: String): Long? {
        val catalogEpNs = database.localEpisodeCatalogDao().getFeed(podcastIndexId)?.epNs
        val showEpNs = podcastDao.getEpNs(podcastIndexId)
        if (catalogEpNs != null && showEpNs != null && catalogEpNs != showEpNs) {
            android.util.Log.w(LOG_TAG, "Show $podcastIndexId: catalog namespace $catalogEpNs differs from the show's $showEpNs; keeping the catalog's")
        }
        return EpisodeNamespaces.choose(podcastIndexId, catalogEpNs, showEpNs)
    }

    private suspend fun aliasesOf(ids: List<String>): Map<String, EpisodeAliasEntity> =
        ids.distinct().chunked(MAX_IDS_PER_QUERY).flatMap { identityDao.getAliases(it) }.associateBy { it.aliasId }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
        const val LOG_TAG = "SwitchedShowLinker"
    }
}
