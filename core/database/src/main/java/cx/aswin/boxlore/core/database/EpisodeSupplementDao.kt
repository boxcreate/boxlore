package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface EpisodeSupplementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplement(entity: EpisodeSupplementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<EpisodeSupplementItemEntity>)

    @Query("SELECT * FROM episode_supplements WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getSupplement(podcastId: String): EpisodeSupplementEntity?

    @Query("SELECT podcastId FROM episode_supplements")
    suspend fun listOptedInPodcastIds(): List<String>

    @Query("SELECT * FROM episode_supplement_items WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getEpisode(episodeId: String): EpisodeSupplementItemEntity?

    @Query(
        """
        SELECT * FROM episode_supplement_items
        WHERE podcastId = :podcastId
        ORDER BY publishedDate DESC, episodeId ASC
        """,
    )
    suspend fun getAllNewest(podcastId: String): List<EpisodeSupplementItemEntity>

    /**
     * [query] must already be escaped for SQL LIKE (see [cx.aswin.boxlore.core.rss.escapeForSqlLike])
     * so literal `%`/`_` characters in a user's search don't get treated as wildcards.
     */
    @Query(
        """
        SELECT * FROM episode_supplement_items
        WHERE podcastId = :podcastId
          AND (title LIKE '%' || :query || '%' ESCAPE '\' OR description LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY publishedDate DESC, episodeId ASC
        """,
    )
    suspend fun search(podcastId: String, query: String): List<EpisodeSupplementItemEntity>

    @Query("DELETE FROM episode_supplement_items WHERE podcastId = :podcastId")
    suspend fun deleteItemsForPodcast(podcastId: String)

    @Query("DELETE FROM episode_supplements WHERE podcastId = :podcastId")
    suspend fun deleteSupplement(podcastId: String)

    @Transaction
    suspend fun replaceAll(podcastId: String, supplement: EpisodeSupplementEntity, items: List<EpisodeSupplementItemEntity>,) {
        deleteItemsForPodcast(podcastId)
        upsertSupplement(supplement)
        upsertItems(items)
    }

    /** Atomically refresh validators and optionally insert a feed-only tip item. */
    @Transaction
    suspend fun upsertSupplementAndOptionalItems(supplement: EpisodeSupplementEntity, items: List<EpisodeSupplementItemEntity>,) {
        upsertSupplement(supplement)
        if (items.isNotEmpty()) {
            upsertItems(items)
        }
    }

    @Query("SELECT * FROM episode_supplements")
    suspend fun listSupplements(): List<EpisodeSupplementEntity>
}
