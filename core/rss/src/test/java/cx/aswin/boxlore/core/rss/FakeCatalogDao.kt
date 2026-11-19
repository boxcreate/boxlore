package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.LocalEpisodeLinkRow
import cx.aswin.boxlore.core.database.LocalEpisodeOwner

/** In-memory [LocalEpisodeCatalogDao] for catalog refresh tests that need no Room. */
internal class FakeCatalogDao : LocalEpisodeCatalogDao {
    val feeds = mutableMapOf<String, LocalEpisodeFeedEntity>()
    val episodes = mutableListOf<LocalEpisodeEntity>()
    var clearTtlAfterListingExpired = false

    override suspend fun replaceFeedRow(feed: LocalEpisodeFeedEntity): Unit = feeds.set(feed.podcastId, feed)

    /** Room's REPLACE: a stored row is deleted when it shares the primary key or the unique (podcastId, guid) index. */
    override suspend fun upsertEpisodes(episodes: List<LocalEpisodeEntity>) {
        for (row in episodes) {
            this.episodes.removeAll { stored ->
                stored.episodeId == row.episodeId || (stored.podcastId == row.podcastId && stored.guid == row.guid)
            }
            this.episodes += row
        }
    }

    override suspend fun getFeed(podcastId: String): LocalEpisodeFeedEntity? = feeds[podcastId]

    override suspend fun previousFeedUrl(podcastId: String): String? = null

    override suspend fun getEpisode(episodeId: String): LocalEpisodeEntity? = findEpisode(episodeId)

    private fun findEpisode(id: String) = episodes.find { it.episodeId == id }

    override suspend fun getByGuid(podcastId: String, guid: String,): LocalEpisodeEntity? = episodes.firstOrNull { it.podcastId == podcastId && it.guid == guid }

    override suspend fun getByAudioUrl(podcastId: String, audioUrl: String,): LocalEpisodeEntity? = episodes.firstOrNull { it.podcastId == podcastId && it.audioUrl == audioUrl }

    override suspend fun listIdentities(podcastId: String): List<LocalEpisodeIdentity> = episodes.filter { it.podcastId == podcastId }.map {
        LocalEpisodeIdentity(it.episodeId, it.guid, it.audioUrl)
    }

    override suspend fun listForShow(podcastId: String): List<LocalEpisodeLinkRow> = episodes
        .filter { it.podcastId == podcastId }
        .map { LocalEpisodeLinkRow(it.episodeId, it.guid, it.audioUrl, it.title, it.publishedDate, it.legacy) }

    override suspend fun findOwners(episodeIds: List<String>): List<LocalEpisodeOwner> = episodes
        .filter { it.episodeId in episodeIds }
        .map { LocalEpisodeOwner(it.episodeId, it.podcastId, it.guid) }

    override suspend fun getNewestPage(podcastId: String, limit: Int, offset: Int,): List<LocalEpisodeEntity> = episodes
        .filter { it.podcastId == podcastId }
        .sortedWith(compareByDescending<LocalEpisodeEntity> { it.publishedDate }.thenBy { it.episodeId })
        .drop(offset)
        .take(limit)

    override suspend fun getOldestPage(podcastId: String, limit: Int, offset: Int,): List<LocalEpisodeEntity> = episodes
        .filter { it.podcastId == podcastId }
        .sortedWith(compareBy<LocalEpisodeEntity> { it.publishedDate }.thenByDescending { it.episodeId })
        .drop(offset)
        .take(limit)

    override suspend fun getNewest(podcastId: String): LocalEpisodeEntity? = getNewestPage(podcastId, limit = 1, offset = 0).firstOrNull()

    override suspend fun count(podcastId: String): Int = episodes.count { it.podcastId == podcastId }

    override suspend fun hasUndatedEpisode(podcastId: String): Boolean = episodes.any { it.podcastId == podcastId && it.publishedDate <= 0L }

    override suspend fun search(podcastId: String, query: String,): List<LocalEpisodeEntity> = emptyList()

    override suspend fun getEpisodesAfter(
        podcastId: String,
        publishedDate: Long,
        episodeId: String,
        limit: Int,
    ): List<LocalEpisodeEntity> {
        val comparator = compareBy<LocalEpisodeEntity> { it.publishedDate }.thenByDescending { it.episodeId }
        val matching =
            episodes.filter {
                it.podcastId == podcastId &&
                    (it.publishedDate > publishedDate || (it.publishedDate == publishedDate && it.episodeId < episodeId))
            }
        return matching.sortedWith(comparator).take(limit)
    }

    override suspend fun getOlderThan(
        podcastId: String,
        publishedDate: Long,
        episodeId: String,
        limit: Int,
    ): List<LocalEpisodeEntity> = emptyList()

    override suspend fun getNewerThan(
        podcastId: String,
        publishedDate: Long,
        episodeId: String,
        limit: Int,
    ): List<LocalEpisodeEntity> = emptyList()

    override suspend fun setTtl(podcastId: String, ttlExpiresAt: Long?,) {
        feeds[podcastId] = feeds.getValue(podcastId).copy(ttlExpiresAt = ttlExpiresAt)
    }

    override suspend fun setFeedUrlLookupAt(podcastId: String, atMillis: Long,) {
        feeds[podcastId] = feeds.getValue(podcastId).copy(feedUrlLookupAt = atMillis)
    }

    override suspend fun setArchiveComplete(podcastId: String, archiveComplete: Boolean,) {
        feeds[podcastId]?.let { feeds[podcastId] = it.copy(archiveComplete = archiveComplete) }
    }

    override suspend fun setLastOpenedAt(podcastId: String, atMillis: Long,) {
        feeds[podcastId]?.let { feeds[podcastId] = it.copy(lastOpenedAt = atMillis) }
    }

    override suspend fun setTtlAndFollowed(podcastId: String, ttlExpiresAt: Long?, followed: Boolean,) {
        feeds[podcastId]?.let { feeds[podcastId] = it.copy(ttlExpiresAt = ttlExpiresAt, followed = followed) }
    }

    override suspend fun seedFollowed(podcastId: String) = Unit

    override suspend fun searchLimited(podcastId: String, query: String, limit: Int,): List<LocalEpisodeEntity> = emptyList()

    override suspend fun listUnfollowedCopies(): List<cx.aswin.boxlore.core.database.UnfollowedCatalogCopy> = feeds.values
        .filter { !it.followed && it.ttlExpiresAt == null }
        .map { feed -> cx.aswin.boxlore.core.database.UnfollowedCatalogCopy(feed.podcastId, feed.lastOpenedAt, feed.fetchedAt, episodes.count { it.podcastId == feed.podcastId }) }

    override suspend fun isUnfollowedCopy(podcastId: String): Boolean = feeds[podcastId]?.let { !it.followed && it.ttlExpiresAt == null } == true

    override suspend fun listClearableCopyIds(): List<String> = feeds.values.filter { !it.followed }.map { it.podcastId }

    override suspend fun isClearableCopy(podcastId: String): Boolean = feeds[podcastId]?.followed == false

    override suspend fun setEpNsIfUnset(podcastId: String, epNs: Long,) {
        feeds[podcastId]?.takeIf { it.epNs == null }?.let { feeds[podcastId] = it.copy(epNs = epNs) }
    }

    override suspend fun deleteEpisodes(podcastId: String) {
        episodes.removeAll { it.podcastId == podcastId }
    }

    override suspend fun deleteFeed(podcastId: String) {
        feeds.remove(podcastId)
    }

    override suspend fun listExpiredFeedIds(now: Long): List<String> {
        val ids =
            feeds.values
                .filter { feed -> feed.ttlExpiresAt != null && feed.ttlExpiresAt!! <= now }
                .map { it.podcastId }
        if (clearTtlAfterListingExpired) {
            for (id in ids) {
                feeds[id] = feeds.getValue(id).copy(ttlExpiresAt = null)
            }
        }
        return ids
    }
}
