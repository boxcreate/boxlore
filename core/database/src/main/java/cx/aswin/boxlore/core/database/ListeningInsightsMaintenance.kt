package cx.aswin.boxlore.core.database

import androidx.room.withTransaction

/**
 * Multi-table listening-analytics maintenance. Uses [BoxLoreDatabase.withTransaction]
 * so Room DAOs stay under TooManyFunctions while roll-up / clear stay atomic.
 */
class ListeningInsightsMaintenance(
    private val database: BoxLoreDatabase,
    private val sessions: ListeningSessionDao = database.listeningSessionDao(),
    private val rollups: ListeningRollupDao = database.listeningRollupDao(),
) {
    suspend fun deleteEpisodeAnalytics(episodeId: String) {
        database.withTransaction {
            sessions.deleteSessionsForEpisode(episodeId)
            rollups.deleteRollupsForEpisode(episodeId)
        }
    }

    /** Clears all timed listening and the played shows (clearing the whole history). */
    suspend fun clearAllAnalytics() {
        database.withTransaction {
            sessions.deleteAllSessions()
            rollups.deleteAllRollups()
            database.playedShowDao().deleteAllPlayedShows()
        }
    }

    /**
     * Merge eligible raw sessions into daily episode rollups and delete those raw rows.
     * Never touches sessions from [todayLocalDay].
     */
    suspend fun rollUpEligibleSessions(cutoffEndedAtExclusive: Long, todayLocalDay: Long,): Int = database.withTransaction {
        val eligible =
            sessions.getSessionsEligibleForRollup(cutoffEndedAtExclusive, todayLocalDay)
        if (eligible.isEmpty()) return@withTransaction 0

        val grouped = eligible.groupBy { Triple(it.rollupDeviceId, it.localDay, it.episodeId) }
        for ((key, groupSessions) in grouped) {
            val (deviceId, localDay, episodeId) = key
            val existing = rollups.getRollup(deviceId, localDay, episodeId)
            rollups.upsertRollup(
                ListeningRollupMerge.mergeSessionsIntoRollup(
                    localDay = localDay,
                    episodeId = episodeId,
                    sessions = groupSessions,
                    existing = existing,
                ),
            )
        }

        sessions.deleteSessionsEligibleForRollup(cutoffEndedAtExclusive, todayLocalDay)
        eligible.size
    }
}
