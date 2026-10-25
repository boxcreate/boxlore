package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
@Suppress("TooManyFunctions")
interface LocalEpisodeCatalogDao {
    /**
     * Inserts [feed], or replaces the stored row while keeping its Room v39 feed-first columns
     * ([LocalEpisodeFeedEntity.keepingFeedFirstColumnsOf]). Catalog refreshes build this row from
     * scratch, and the v39 upgrade seeds `followed` and `epNs` only once, so a plain REPLACE would
     * reset them on the next refresh. Those columns change only through their own updates.
     *
     * Changed in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     * COMPAT(own-ids): keeps the seeded namespace and follow state. Permanent: never remove (MIGRATION.md §5).
     */
    @Transaction
    suspend fun upsertFeed(feed: LocalEpisodeFeedEntity) {
        val stored = getFeed(feed.podcastId)
        replaceFeedRow(if (stored == null) feed else feed.keepingFeedFirstColumnsOf(stored))
    }

    /**
     * Writes every column of [feed] as given, the v39 feed-first columns included. Use [upsertFeed],
     * which keeps those columns of a stored row.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 feed columns).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceFeedRow(feed: LocalEpisodeFeedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(episodes: List<LocalEpisodeEntity>)

    @Query("SELECT * FROM local_episode_feeds WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getFeed(podcastId: String): LocalEpisodeFeedEntity?

    @Query("SELECT * FROM local_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getEpisode(episodeId: String): LocalEpisodeEntity?

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId AND guid = :guid
        LIMIT 1
        """,
    )
    suspend fun getByGuid(podcastId: String, guid: String,): LocalEpisodeEntity?

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId AND audioUrl = :audioUrl
        LIMIT 1
        """,
    )
    suspend fun getByAudioUrl(podcastId: String, audioUrl: String,): LocalEpisodeEntity?

    @Query(
        """
        SELECT episodeId, guid, audioUrl FROM local_episodes
        WHERE podcastId = :podcastId
        """,
    )
    suspend fun listIdentities(podcastId: String): List<LocalEpisodeIdentity>

    /**
     * Owners of these episode IDs across every show, so a write can check before [upsertEpisodes]
     * replaces a row (docs/EPISODE_IDS.md §4). Callers pass at most 500 IDs per call.
     */
    @Query("SELECT episodeId, podcastId, guid FROM local_episodes WHERE episodeId IN (:episodeIds)")
    suspend fun findOwners(episodeIds: List<String>): List<LocalEpisodeOwner>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate DESC, episodeId ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getNewestPage(podcastId: String, limit: Int, offset: Int,): List<LocalEpisodeEntity>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate ASC, episodeId DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getOldestPage(podcastId: String, limit: Int, offset: Int,): List<LocalEpisodeEntity>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate DESC, episodeId ASC
        LIMIT 1
        """,
    )
    suspend fun getNewest(podcastId: String): LocalEpisodeEntity?

    @Query("SELECT COUNT(*) FROM local_episodes WHERE podcastId = :podcastId")
    suspend fun count(podcastId: String): Int

    /**
     * [query] must already be escaped for SQL LIKE by the caller, using `\`
     * as the escape character.
     */
    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
          AND (title LIKE '%' || :query || '%' ESCAPE '\'
            OR description LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY publishedDate DESC, episodeId ASC
        """,
    )
    suspend fun search(podcastId: String, query: String,): List<LocalEpisodeEntity>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
          AND (
            publishedDate > :publishedDate
            OR (publishedDate = :publishedDate AND episodeId < :episodeId)
          )
        ORDER BY publishedDate ASC, episodeId DESC
        LIMIT :limit
        """,
    )
    suspend fun getEpisodesAfter(podcastId: String, publishedDate: Long, episodeId: String, limit: Int,): List<LocalEpisodeEntity>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
          AND (
            publishedDate > :publishedDate
            OR (publishedDate = :publishedDate AND episodeId < :episodeId)
          )
        ORDER BY publishedDate ASC, episodeId DESC
        LIMIT :limit
        """,
    )
    suspend fun getOlderThan(podcastId: String, publishedDate: Long, episodeId: String, limit: Int,): List<LocalEpisodeEntity>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
          AND (
            publishedDate < :publishedDate
            OR (publishedDate = :publishedDate AND episodeId > :episodeId)
          )
        ORDER BY publishedDate DESC, episodeId ASC
        LIMIT :limit
        """,
    )
    suspend fun getNewerThan(podcastId: String, publishedDate: Long, episodeId: String, limit: Int,): List<LocalEpisodeEntity>

    @Query("UPDATE local_episode_feeds SET ttlExpiresAt = :ttlExpiresAt WHERE podcastId = :podcastId")
    suspend fun setTtl(podcastId: String, ttlExpiresAt: Long?,)

    @Query("UPDATE local_episode_feeds SET feedUrlLookupAt = :atMillis WHERE podcastId = :podcastId")
    suspend fun setFeedUrlLookupAt(podcastId: String, atMillis: Long,)

    /**
     * Sets this catalog's episode namespace when it has none. A stored value is never replaced:
     * recipe IDs already issued depend on it ([upsertFeed] keeps it on later writes).
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, recipe IDs; decision D12).
     * COMPAT(own-ids): an issued namespace never changes. Permanent: never remove (MIGRATION.md §5).
     */
    @Query("UPDATE local_episode_feeds SET epNs = :epNs WHERE podcastId = :podcastId AND epNs IS NULL")
    suspend fun setEpNsIfUnset(podcastId: String, epNs: Long,)

    @Query("DELETE FROM local_episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodes(podcastId: String)

    @Query("DELETE FROM local_episode_feeds WHERE podcastId = :podcastId")
    suspend fun deleteFeed(podcastId: String)

    @Query(
        """
        SELECT podcastId FROM local_episode_feeds
        WHERE ttlExpiresAt IS NOT NULL AND ttlExpiresAt <= :now
        """,
    )
    suspend fun listExpiredFeedIds(now: Long): List<String>

    @Transaction
    suspend fun deleteCatalog(podcastId: String) {
        deleteEpisodes(podcastId)
        deleteFeed(podcastId)
    }

    /**
     * Rechecks [ttlExpiresAt] inside the transaction so a refresh that cleared
     * the TTL after [listExpiredFeedIds] cannot delete a live catalog.
     */
    @Transaction
    suspend fun deleteCatalogIfExpired(podcastId: String, nowMillis: Long,) {
        val ttl = getFeed(podcastId)?.ttlExpiresAt ?: return
        if (ttl > nowMillis) return
        deleteCatalog(podcastId)
    }
}
