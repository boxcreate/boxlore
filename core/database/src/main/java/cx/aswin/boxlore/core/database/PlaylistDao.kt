package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Playlists and their shows (Room v44). Shows are read in playlist order (`position`, then when
 * they were added); a show is in a playlist at most once, and deleting a playlist deletes its rows.
 * Also reads the stored episodes and in-progress history a playlist plays from.
 */
@Dao
@Suppress("TooManyFunctions")
interface PlaylistDao {
    /** Every playlist with its show count, oldest first. */
    @Query(
        """
        SELECT p.*, COUNT(s.podcastId) AS showCount
        FROM playlists p
        LEFT JOIN playlist_shows s ON s.playlistId = p.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAt ASC, p.playlistId ASC
        """,
    )
    fun observePlaylistSummaries(): Flow<List<PlaylistSummary>>

    /**
     * The artwork of each playlist's first [COVER_LIMIT] shows, in playlist order. Without window
     * functions, so it runs on every supported SQLite.
     */
    @Query(
        """
        SELECT s.playlistId AS playlistId, p.imageUrl AS imageUrl
        FROM playlist_shows s
        LEFT JOIN podcasts p ON p.podcastId = s.podcastId
        WHERE (
            SELECT COUNT(*) FROM playlist_shows t
            WHERE t.playlistId = s.playlistId
                AND (t.position < s.position OR (t.position = s.position AND t.addedAt < s.addedAt)
                    OR (t.position = s.position AND t.addedAt = s.addedAt AND t.podcastId < s.podcastId))
        ) < $COVER_LIMIT
        ORDER BY s.playlistId ASC, s.position ASC, s.addedAt ASC, s.podcastId ASC
        """,
    )
    fun observePlaylistCovers(): Flow<List<PlaylistCover>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC, playlistId ASC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query(
        """
        SELECT s.playlistId, s.podcastId, s.position, s.addedAt, p.title AS title, p.imageUrl AS imageUrl,
            p.author AS author, p.genre AS genre, COALESCE(p.isSubscribed, 0) AS isSubscribed
        FROM playlist_shows s
        LEFT JOIN podcasts p ON p.podcastId = s.podcastId
        WHERE s.playlistId = :playlistId
        ORDER BY s.position ASC, s.addedAt ASC, s.podcastId ASC
        """,
    )
    fun observeShows(playlistId: String): Flow<List<PlaylistShowRow>>

    @Query(
        """
        SELECT s.playlistId, s.podcastId, s.position, s.addedAt, p.title AS title, p.imageUrl AS imageUrl,
            p.author AS author, p.genre AS genre, COALESCE(p.isSubscribed, 0) AS isSubscribed
        FROM playlist_shows s
        LEFT JOIN podcasts p ON p.podcastId = s.podcastId
        WHERE s.playlistId = :playlistId
        ORDER BY s.position ASC, s.addedAt ASC, s.podcastId ASC
        """,
    )
    suspend fun getShows(playlistId: String): List<PlaylistShowRow>

    /** Every playlist's show rows, each playlist in its order (for backups). */
    @Query("SELECT * FROM playlist_shows ORDER BY playlistId ASC, position ASC, addedAt ASC, podcastId ASC")
    suspend fun getAllShowRows(): List<PlaylistShowEntity>

    /** The playlists holding [podcastId]. */
    @Query("SELECT playlistId FROM playlist_shows WHERE podcastId = :podcastId")
    fun observePlaylistIdsForShow(podcastId: String): Flow<List<String>>

    @Query("SELECT MAX(position) FROM playlist_shows WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int?

    /** Adds [playlist]; throws when its ID is taken. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    /** Renames [playlistId] and sets its [updatedAt]; returns the rows changed (0 when it doesn't exist). */
    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long): Int

    /** Sets [playlistId]'s episode order (`newest` or `oldest`); returns the rows changed. */
    @Query("UPDATE playlists SET episodeOrder = :episodeOrder, updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun setEpisodeOrder(playlistId: String, episodeOrder: String, updatedAt: Long): Int

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAt: Long)

    /** Deletes [playlistId]; its show rows go with it. */
    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    /** Inserts [row] as given; -1 when the show is already in the playlist. Prefer [appendShow]. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShow(row: PlaylistShowEntity): Long

    /**
     * Adds [podcastId] after the playlist's last show and sets the playlist's `updatedAt` to [addedAt]
     * when [touch]. False when the playlist doesn't exist or already holds the show.
     */
    @Transaction
    suspend fun appendShow(playlistId: String, podcastId: String, addedAt: Long, touch: Boolean = true): Boolean {
        if (getPlaylist(playlistId) == null) return false
        val position = (getMaxPosition(playlistId) ?: -1) + 1
        if (insertShow(PlaylistShowEntity(playlistId, podcastId, position, addedAt)) == -1L) return false
        if (touch) touchPlaylist(playlistId, addedAt)
        return true
    }

    /** Removes [podcastId] from [playlistId]; returns the rows removed. */
    @Query("DELETE FROM playlist_shows WHERE playlistId = :playlistId AND podcastId = :podcastId")
    suspend fun deleteShow(playlistId: String, podcastId: String): Int

    @Query("SELECT * FROM playlist_shows WHERE podcastId = :podcastId")
    suspend fun getRowsForShow(podcastId: String): List<PlaylistShowEntity>

    /**
     * Moves every playlist row of show [oldPodcastId] to [newPodcastId] (RSS follow upgrade). A
     * playlist that already holds the new show keeps that row, so the show stays in it once.
     */
    @Transaction
    suspend fun reassignPodcast(oldPodcastId: String, newPodcastId: String): Int {
        if (oldPodcastId == newPodcastId) return 0
        val rows = getRowsForShow(oldPodcastId)
        rows.forEach { row ->
            deleteShow(row.playlistId, oldPodcastId)
            insertShow(row.copy(podcastId = newPodcastId))
        }
        return rows.size
    }

    @Query("DELETE FROM playlist_shows")
    suspend fun deleteAllShows()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    /** Deletes every playlist and its shows (account switch). */
    @Transaction
    suspend fun deleteAll() {
        deleteAllShows()
        deleteAllPlaylists()
    }

    /**
     * Up to [limit] of [podcastIds]' stored episodes with audio that aren't finished under this ID,
     * newest release first, released before ([beforeDate], [beforeId]): keyset pages, so each page
     * reads only from where the last one ended. Reads `local_episodes` (Podcast Index shows) and
     * `rss_episodes` (`rss:` shows); each ID is bound twice, so at most 400 IDs per call.
     */
    @Query(
        """
        SELECT e.* FROM ($STORED_EPISODES) e WHERE e.audioUrl != ''
            AND (e.publishedDate < :beforeDate OR (e.publishedDate = :beforeDate AND e.episodeId < :beforeId))
            AND $NOT_FINISHED
        ORDER BY e.publishedDate DESC, e.episodeId DESC LIMIT :limit
        """,
    )
    suspend fun newestEpisodes(podcastIds: List<String>, beforeDate: Long, beforeId: String, limit: Int): List<PlaylistEpisodeRow>

    /** [newestEpisodes], oldest release first, released after ([afterDate], [afterId]). */
    @Query(
        """
        SELECT e.* FROM ($STORED_EPISODES) e WHERE e.audioUrl != ''
            AND (e.publishedDate > :afterDate OR (e.publishedDate = :afterDate AND e.episodeId > :afterId))
            AND $NOT_FINISHED
        ORDER BY e.publishedDate ASC, e.episodeId ASC LIMIT :limit
        """,
    )
    suspend fun oldestEpisodes(podcastIds: List<String>, afterDate: Long, afterId: String, limit: Int): List<PlaylistEpisodeRow>

    /** The stored episodes among [episodeIds] (bound twice, so at most 400), from either episode table. */
    @Query("SELECT * FROM ($STORED_EPISODES_BY_ID)")
    suspend fun storedEpisodes(episodeIds: List<String>): List<PlaylistEpisodeRow>

    /**
     * History rows of [podcastIds] (at most 997) the listener is midway through, at least
     * [minProgressMs] in, most recently played first.
     */
    @Query(
        """
        SELECT * FROM listening_history
        WHERE isCompleted = 0 AND dismissedAt <= 0 AND progressMs >= :minProgressMs AND progressMs > 0 AND podcastId IN (:podcastIds)
        ORDER BY lastPlayedAt DESC LIMIT :limit
        """,
    )
    suspend fun inProgressHistory(podcastIds: List<String>, minProgressMs: Long, limit: Int): List<ListeningHistoryEntity>

    companion object {
        /** How many shows' artwork [observePlaylistCovers] returns per playlist. */
        const val COVER_LIMIT = 4
    }
}

private const val EPISODE_COLUMNS =
    "episodeId, podcastId, title, description, audioUrl, imageUrl, duration, publishedDate, chaptersUrl, " +
        "transcriptUrl, seasonNumber, episodeNumber, episodeType, enclosureType"

private const val STORED_EPISODES =
    "SELECT $EPISODE_COLUMNS FROM local_episodes WHERE podcastId IN (:podcastIds) " +
        "UNION ALL SELECT $EPISODE_COLUMNS FROM rss_episodes WHERE podcastId IN (:podcastIds)"

/** Not finished nor archived under its own ID; the caller still checks the episode's other IDs. */
private const val NOT_FINISHED =
    "NOT EXISTS (SELECT 1 FROM listening_history h WHERE h.episodeId = e.episodeId AND (h.isCompleted = 1 OR h.dismissedAt > 0))"

private const val STORED_EPISODES_BY_ID =
    "SELECT $EPISODE_COLUMNS FROM local_episodes WHERE episodeId IN (:episodeIds) " +
        "UNION ALL SELECT $EPISODE_COLUMNS FROM rss_episodes WHERE episodeId IN (:episodeIds)"
