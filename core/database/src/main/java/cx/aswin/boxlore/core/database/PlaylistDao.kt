package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Playlists and their episodes (Room v43). Items are read in playlist order (`position`, then insertion);
 * an episode ID is in a playlist at most once, and deleting a playlist deletes its items.
 */
@Dao
@Suppress("TooManyFunctions")
interface PlaylistDao {
    /** Every playlist with its episode count, oldest first. */
    @Query(
        """
        SELECT p.*, COUNT(i.id) AS itemCount
        FROM playlists p
        LEFT JOIN playlist_items i ON i.playlistId = p.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAt ASC, p.playlistId ASC
        """,
    )
    fun observePlaylistSummaries(): Flow<List<PlaylistSummary>>

    /**
     * The artwork of each playlist's first [COVER_LIMIT] episodes, in playlist order: the episode's own
     * image, else its show's. Without window functions, so it runs on every supported SQLite.
     */
    @Query(
        """
        SELECT i.playlistId AS playlistId, COALESCE(NULLIF(i.imageUrl, ''), i.podcastImageUrl) AS imageUrl
        FROM playlist_items i
        WHERE (
            SELECT COUNT(*) FROM playlist_items j
            WHERE j.playlistId = i.playlistId
                AND (j.position < i.position OR (j.position = i.position AND j.id < i.id))
        ) < $COVER_LIMIT
        ORDER BY i.playlistId ASC, i.position ASC, i.id ASC
        """,
    )
    fun observePlaylistCovers(): Flow<List<PlaylistCover>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun observePlaylist(playlistId: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC, playlistId ASC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, id ASC")
    fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, id ASC")
    suspend fun getItems(playlistId: String): List<PlaylistItemEntity>

    /** Every playlist's items, each playlist in its order (for backups). */
    @Query("SELECT * FROM playlist_items ORDER BY playlistId ASC, position ASC, id ASC")
    suspend fun getAllItems(): List<PlaylistItemEntity>

    /** Which of [episodeIds] (at most 500) are already in [playlistId]. */
    @Query("SELECT episodeId FROM playlist_items WHERE playlistId = :playlistId AND episodeId IN (:episodeIds)")
    suspend fun episodeIdsIn(playlistId: String, episodeIds: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun countPlaylists(): Int

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun countItems(playlistId: String): Int

    @Query("SELECT MAX(position) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int?

    /** Adds [playlist]; throws when its ID is taken. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    /** Renames [playlistId] and sets its [updatedAt]; returns the rows changed (0 when it doesn't exist). */
    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long): Int

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAt: Long)

    /** Deletes [playlistId]; its items go with it. */
    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    /** Inserts [item] as given; -1 when its episode is already in the playlist. Prefer [appendItem]. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    /**
     * Adds [item] after the playlist's last episode and sets the playlist's `updatedAt` to its `addedAt`.
     * Returns the new row ID, or null when the playlist doesn't exist or already holds the episode.
     */
    @Transaction
    suspend fun appendItem(item: PlaylistItemEntity): Long? {
        if (getPlaylist(item.playlistId) == null) return null
        val position = (getMaxPosition(item.playlistId) ?: -1) + 1
        val id = insertItem(item.copy(id = 0, position = position))
        if (id == -1L) return null
        touchPlaylist(item.playlistId, item.addedAt)
        return id
    }

    /** [appendItem] for each of [items], in order, in one transaction; returns how many were added. */
    @Transaction
    suspend fun appendItems(items: List<PlaylistItemEntity>): Int = items.count { appendItem(it) != null }

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    /** Removes [episodeIds] (at most 500) from [playlistId]; returns how many rows went. */
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND episodeId IN (:episodeIds)")
    suspend fun deleteItemsByEpisodeIds(playlistId: String, episodeIds: List<String>): Int

    @Query("UPDATE playlist_items SET position = :position WHERE id = :itemId")
    suspend fun setItemPosition(itemId: Long, position: Int)

    /**
     * Puts [playlistId]'s items in the order of [orderedItemIds] (positions 0, 1, 2…). IDs not in the
     * playlist are ignored; items left out keep their relative order after the listed ones.
     */
    @Transaction
    suspend fun reorder(playlistId: String, orderedItemIds: List<Long>) {
        val items = getItems(playlistId)
        val byId = items.associateBy { it.id }
        val listed = orderedItemIds.distinct().mapNotNull { byId[it] }
        val listedIds = listed.mapTo(HashSet()) { it.id }
        (listed + items.filter { it.id !in listedIds }).forEachIndexed { index, item ->
            if (item.position != index) setItemPosition(item.id, index)
        }
    }

    /** Points every playlist row of show [oldPodcastId] at show [newPodcastId] with its details (RSS follow upgrade). */
    @Query(
        "UPDATE playlist_items SET podcastId = :newPodcastId, podcastTitle = :podcastTitle, podcastGenre = :podcastGenre, " +
            "podcastArtist = :podcastArtist, podcastImageUrl = :podcastImageUrl WHERE podcastId = :oldPodcastId",
    )
    suspend fun reassignPodcast(
        oldPodcastId: String,
        newPodcastId: String,
        podcastTitle: String,
        podcastGenre: String,
        podcastArtist: String,
        podcastImageUrl: String?,
    ): Int

    @Query("DELETE FROM playlist_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    /** Deletes every playlist and item (account switch). */
    @Transaction
    suspend fun deleteAll() {
        deleteAllItems()
        deleteAllPlaylists()
    }

    companion object {
        /** How many episodes' artwork [observePlaylistCovers] returns per playlist. */
        const val COVER_LIMIT = 4
    }
}
