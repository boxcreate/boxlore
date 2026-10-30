package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Shows the listener has played ([PlayedShowEntity], Room v40). */
@Dao
interface PlayedShowDao {
    @Query("SELECT * FROM played_shows WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getPlayedShow(podcastId: String): PlayedShowEntity?

    @Query("SELECT * FROM played_shows WHERE podcastId IN (:podcastIds)")
    suspend fun getPlayedShows(podcastIds: List<String>): List<PlayedShowEntity>

    @Query("SELECT * FROM played_shows ORDER BY lastPlayedAt DESC")
    suspend fun getAllPlayedShows(): List<PlayedShowEntity>

    @Query("SELECT * FROM played_shows ORDER BY lastPlayedAt DESC")
    fun observeAllPlayedShows(): Flow<List<PlayedShowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replacePlayedShows(rows: List<PlayedShowEntity>)

    /**
     * A play of [podcastId] at [playedAt]: a new show starts with that first play, a known one keeps
     * its earlier first play and takes the newer name, art and genre. Marks the row for the next push.
     */
    @Transaction
    suspend fun recordPlay(podcastId: String, podcastName: String, imageUrl: String?, genre: String?, playedAt: Long,) {
        if (podcastId.isBlank()) return
        val played =
            PlayedShowEntity(
                podcastId = podcastId,
                podcastName = podcastName,
                imageUrl = imageUrl,
                genre = PlayedShowEntity.storedGenre(genre),
                firstPlayedAt = playedAt,
                lastPlayedAt = playedAt,
            )
        val stored = getPlayedShow(podcastId)
        replacePlayedShows(listOf((stored?.mergedWith(played) ?: played).copy(isDirty = true)))
    }

    /**
     * Rows from a backup or another device merged into the stored ones. With [fromCloud], a merged row
     * that holds exactly what the cloud sent is stored clean; any other change is marked for the push.
     */
    @Transaction
    suspend fun mergePlayedShows(rows: List<PlayedShowEntity>, fromCloud: Boolean,) {
        if (rows.isEmpty()) return
        val stored = rows.map { it.podcastId }.distinct().chunked(PLAYED_SHOWS_CHUNK).flatMap { getPlayedShows(it) }.associateBy { it.podcastId }
        val merged =
            rows.mapNotNull { incoming ->
                val mine = stored[incoming.podcastId] ?: return@mapNotNull incoming.copy(isDirty = !fromCloud)
                val result = mine.mergedWith(incoming)
                when {
                    result.sameValuesAs(mine) -> null
                    fromCloud && result.sameValuesAs(incoming) -> result.copy(isDirty = mine.isDirty)
                    else -> result.copy(isDirty = true)
                }
            }
        if (merged.isNotEmpty()) replacePlayedShows(merged)
    }

    /** One cloud push batch of rows waiting for the push. */
    @Query("SELECT * FROM played_shows WHERE isDirty = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getDirtyPlayedShows(limit: Int): List<PlayedShowEntity>

    /** Clears the push mark of [sent] unless the row changed since it was sent (a play, a restore or a pull). */
    suspend fun markPlayedShowSynced(sent: PlayedShowEntity) {
        markPlayedShowSyncedIfUnchanged(sent.podcastId, sent.podcastName, sent.imageUrl, sent.genre, sent.firstPlayedAt, sent.lastPlayedAt)
    }

    @Query(
        """
        UPDATE played_shows SET isDirty = 0
        WHERE podcastId = :podcastId AND podcastName = :podcastName AND imageUrl IS :imageUrl AND genre IS :genre
          AND firstPlayedAt = :firstPlayedAt AND lastPlayedAt = :lastPlayedAt
        """,
    )
    @Suppress("LongParameterList")
    suspend fun markPlayedShowSyncedIfUnchanged(podcastId: String, podcastName: String, imageUrl: String?, genre: String?, firstPlayedAt: Long, lastPlayedAt: Long,)

    /** Marks every row for the next push (a new account or a deleted cloud copy). */
    @Query("UPDATE played_shows SET isDirty = 1")
    suspend fun markAllPlayedShowsDirty()

    @Query("DELETE FROM played_shows")
    suspend fun deleteAllPlayedShows()

    companion object {
        /** Show IDs per read, under SQLite's 999 bound parameters. */
        const val PLAYED_SHOWS_CHUNK = 900
    }
}
