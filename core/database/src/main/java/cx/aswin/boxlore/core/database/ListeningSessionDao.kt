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

    @Query("SELECT * FROM listening_sessions ORDER BY endedAt DESC")
    fun observeAllSessions(): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM listening_sessions ORDER BY endedAt DESC")
    suspend fun getAllSessions(): List<ListeningSessionEntity>

    /**
     * Sessions added up in the database: one row per day, episode and show, with the time in each
     * part of the day and the longest single session. Screens that only need days read this instead
     * of every session (up to 400 days of them).
     */
    @Query(
        """
        SELECT localDay, episodeId, podcastId,
            SUM(consumedMs) AS consumedMs,
            COUNT(*) AS sessionCount,
            MAX(endedAt) AS lastListenedAt,
            SUM(CASE WHEN timeBucket = 0 THEN consumedMs ELSE 0 END) AS morningMs,
            SUM(CASE WHEN timeBucket = 1 THEN consumedMs ELSE 0 END) AS afternoonMs,
            SUM(CASE WHEN timeBucket = 2 THEN consumedMs ELSE 0 END) AS eveningMs,
            SUM(CASE WHEN timeBucket = 3 THEN consumedMs ELSE 0 END) AS nightMs,
            MAX(consumedMs) AS longestSessionMs
        FROM listening_sessions
        GROUP BY localDay, episodeId, podcastId
        """,
    )
    fun observeSessionDays(): Flow<List<ListeningSessionDay>>

    @Query(
        """
        SELECT * FROM listening_sessions
        WHERE endedAt < :cutoffEndedAtExclusive
          AND localDay < :todayLocalDay
        ORDER BY localDay ASC, episodeId ASC
        """,
    )
    suspend fun getSessionsEligibleForRollup(cutoffEndedAtExclusive: Long, todayLocalDay: Long,): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_sessions WHERE episodeId = :episodeId")
    suspend fun getSessionsForEpisode(episodeId: String): List<ListeningSessionEntity>

    /**
     * Moves the sessions of [origins] (this installation's, [ListeningOrigins.pushed]) and unclaimed ones
     * to [newEpisodeId]. Another origin's sessions keep the ID it stores them under, so its cloud days
     * keep replacing them instead of adding a second copy (Room v40).
     */
    @Query(
        """
        UPDATE listening_sessions SET episodeId = :newEpisodeId
        WHERE episodeId = :oldEpisodeId AND (deviceId IS NULL OR deviceId = '' OR deviceId IN (:origins))
        """,
    )
    suspend fun reassignEpisodeId(oldEpisodeId: String, newEpisodeId: String, origins: List<String>,)

    @Query("UPDATE listening_sessions SET podcastId = :newPodcastId WHERE podcastId = :oldPodcastId")
    suspend fun reassignPodcastId(oldPodcastId: String, newPodcastId: String,)

    @Query(
        """
        DELETE FROM listening_sessions
        WHERE endedAt < :cutoffEndedAtExclusive
          AND localDay < :todayLocalDay
        """,
    )
    suspend fun deleteSessionsEligibleForRollup(cutoffEndedAtExclusive: Long, todayLocalDay: Long,)

    @Query("DELETE FROM listening_sessions WHERE episodeId = :episodeId")
    suspend fun deleteSessionsForEpisode(episodeId: String)

    @Query("DELETE FROM listening_sessions")
    suspend fun deleteAllSessions()

    /**
     * Deletes the sessions of [deviceId] ([rollupDeviceId]), [localDay] and [episodeId] that a daily summary
     * whose last listening is [upToEndedAt] already sums: those that ended by then.
     */
    @Query(
        """
        DELETE FROM listening_sessions
        WHERE COALESCE(deviceId, '') = :deviceId AND localDay = :localDay AND episodeId = :episodeId AND endedAt <= :upToEndedAt
        """,
    )
    suspend fun deleteSessionsOfDailySummary(deviceId: String, localDay: Long, episodeId: String, upToEndedAt: Long,)

    /** Gives sessions recorded before Room v40 (no phone on them) to [deviceId]. */
    @Query("UPDATE listening_sessions SET deviceId = :deviceId WHERE deviceId IS NULL OR deviceId = ''")
    suspend fun claimLegacySessions(deviceId: String)
}

/** One day's sessions of an episode under one show, added up by [ListeningSessionDao.observeSessionDays]. */
data class ListeningSessionDay(
    val localDay: Long,
    val episodeId: String,
    val podcastId: String,
    val consumedMs: Long,
    val sessionCount: Int,
    val lastListenedAt: Long,
    val morningMs: Long,
    val afternoonMs: Long,
    val eveningMs: Long,
    val nightMs: Long,
    val longestSessionMs: Long,
)
