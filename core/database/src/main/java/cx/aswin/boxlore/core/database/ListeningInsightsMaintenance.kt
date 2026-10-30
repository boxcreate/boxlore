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

    /**
     * This phone's daily listening (its sessions summed into its daily summaries) whose last listening
     * is after [changedAfter], oldest first, at most [limit] days, as [ListeningRollupEntity.THIS_DEVICE]
     * rows: what a cloud sync push sends.
     */
    suspend fun ownDailyListening(changedAfter: Long, limit: Int,): List<ListeningRollupEntity> = database.withTransaction {
        val own = sessions.getAllSessions().filter { it.deviceId == null }.groupBy { it.localDay to it.episodeId }
        val summaries = rollups.getAllRollups().filter { it.deviceId == ListeningRollupEntity.THIS_DEVICE }.associateBy { it.localDay to it.episodeId }
        (own.keys + summaries.keys)
            .mapNotNull { key ->
                val daySessions = own[key]
                if (daySessions.isNullOrEmpty()) {
                    summaries[key]
                } else {
                    ListeningRollupMerge.mergeSessionsIntoRollup(key.first, key.second, daySessions, summaries[key])
                }
            }.filter { it.lastListenedAt > changedAfter }
            .sortedWith(compareBy({ it.lastListenedAt }, { it.localDay }, { it.episodeId }))
            .take(limit)
    }

    /**
     * Daily listening from the cloud. Another phone's day replaces the summary of that phone, day and
     * episode and the sessions of it a restored backup holds, so it counts once. A day of
     * [thisDeviceId] (this phone's own, pushed before a reinstall) is kept only when this phone holds
     * nothing for that day and episode.
     */
    suspend fun applyCloudDailyListening(days: List<ListeningRollupEntity>, thisDeviceId: String,) {
        if (days.isEmpty()) return
        database.withTransaction {
            val ownKeys =
                (
                    sessions.getAllSessions().filter { it.deviceId == null }.map { it.localDay to it.episodeId } +
                        rollups.getAllRollups().filter { it.deviceId == ListeningRollupEntity.THIS_DEVICE }.map { it.localDay to it.episodeId }
                    ).toMutableSet()
            for (day in days) {
                if (day.deviceId == thisDeviceId || day.deviceId == ListeningRollupEntity.THIS_DEVICE) {
                    if (ownKeys.add(day.localDay to day.episodeId)) rollups.upsertRollup(day.copy(deviceId = ListeningRollupEntity.THIS_DEVICE))
                } else {
                    rollups.upsertRollup(day)
                    sessions.deleteSessionsOfDailySummary(day.deviceId, day.localDay, day.episodeId)
                }
            }
        }
    }
}
