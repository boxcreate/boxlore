package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ListeningSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<ListeningSessionEntity>)

    @Query("SELECT * FROM listening_sessions WHERE endedAt >= :sinceMs ORDER BY endedAt DESC")
    fun observeSessionsSince(sinceMs: Long): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM listening_sessions ORDER BY endedAt DESC")
    fun observeAllSessions(): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM listening_sessions WHERE endedAt >= :sinceMs ORDER BY endedAt DESC")
    suspend fun getSessionsSince(sinceMs: Long): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_sessions ORDER BY endedAt DESC")
    suspend fun getAllSessions(): List<ListeningSessionEntity>

    @Query(
        """
        SELECT * FROM listening_sessions
        WHERE endedAt < :cutoffEndedAtExclusive
          AND localDay < :todayLocalDay
        ORDER BY localDay ASC, episodeId ASC
        """,
    )
    suspend fun getSessionsEligibleForRollup(
        cutoffEndedAtExclusive: Long,
        todayLocalDay: Long,
    ): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_sessions WHERE episodeId = :episodeId")
    suspend fun getSessionsForEpisode(episodeId: String): List<ListeningSessionEntity>

    @Query(
        """
        DELETE FROM listening_sessions
        WHERE endedAt < :cutoffEndedAtExclusive
          AND localDay < :todayLocalDay
        """,
    )
    suspend fun deleteSessionsEligibleForRollup(
        cutoffEndedAtExclusive: Long,
        todayLocalDay: Long,
    )

    @Query("DELETE FROM listening_sessions WHERE episodeId = :episodeId")
    suspend fun deleteSessionsForEpisode(episodeId: String)

    @Query("DELETE FROM listening_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT MIN(endedAt) FROM listening_sessions")
    suspend fun getEarliestSessionEndedAt(): Long?
}
