package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LocalEpisodeCatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeed(feed: LocalEpisodeFeedEntity)

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
    suspend fun getByGuid(podcastId: String, guid: String): LocalEpisodeEntity?

    @Query(
        """
        SELECT episodeId, guid, audioUrl FROM local_episodes
        WHERE podcastId = :podcastId
        """,
    )
    suspend fun listIdentities(podcastId: String): List<LocalEpisodeIdentity>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate DESC, episodeId ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getNewestPage(podcastId: String, limit: Int, offset: Int): List<LocalEpisodeEntity>

    @Query(
        """
        SELECT * FROM local_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate ASC, episodeId DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getOldestPage(podcastId: String, limit: Int, offset: Int): List<LocalEpisodeEntity>

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
     * [query] must already be escaped for SQL LIKE
     * (see [cx.aswin.boxlore.core.rss.escapeForSqlLike]).
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
    suspend fun search(podcastId: String, query: String): List<LocalEpisodeEntity>

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
    suspend fun getOlderThan(
        podcastId: String,
        publishedDate: Long,
        episodeId: String,
        limit: Int,
    ): List<LocalEpisodeEntity>

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
    suspend fun getNewerThan(
        podcastId: String,
        publishedDate: Long,
        episodeId: String,
        limit: Int,
    ): List<LocalEpisodeEntity>

    @Query("UPDATE local_episode_feeds SET ttlExpiresAt = :ttlExpiresAt WHERE podcastId = :podcastId")
    suspend fun setTtl(podcastId: String, ttlExpiresAt: Long?)

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
}
