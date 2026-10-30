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
    private val pushes: ListeningPushDao = database.listeningPushDao(),
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
     * Gives this phone's timed listening recorded before Room v40 (stored without a phone) to
     * [deviceId], this installation's listening ID: sessions, and summaries (merged into a summary of
     * the same day and episode under [deviceId], if any). Later sessions are stored with the ID.
     */
    suspend fun claimLegacyListening(deviceId: String) {
        database.withTransaction {
            sessions.claimLegacySessions(deviceId)
            for (legacy in rollups.getRollupsOfDevice(ListeningRollupEntity.THIS_DEVICE)) {
                val mine = rollups.getRollup(deviceId, legacy.localDay, legacy.episodeId)
                rollups.upsertRollup(mine?.let { ListeningRollupMerge.sum(it, legacy) } ?: legacy.copy(deviceId = deviceId))
                rollups.deleteRollup(ListeningRollupEntity.THIS_DEVICE, legacy.localDay, legacy.episodeId)
            }
        }
    }

    /**
     * The days of [deviceId]'s listening the cloud doesn't hold as they are now, oldest first, at most
     * [limit]: each day whose total (its sessions summed into its summary) differs from what was last
     * pushed, and a zero day for each day pushed before that this phone no longer holds (its listening
     * moved to another episode ID, or was removed). [recordPushedDays] records them once stored.
     */
    suspend fun listeningDaysToPush(deviceId: String, limit: Int,): List<ListeningRollupEntity> = database.withTransaction {
        val current = dailyListeningOf(deviceId).associateBy { Triple(it.deviceId, it.localDay, it.episodeId) }
        val pushed = pushes.getPushedDays(deviceId).associateBy { Triple(it.deviceId, it.localDay, it.episodeId) }
        val changed = current.filter { (key, day) -> pushed[key] != PushedListeningDayEntity.of(day) }.values
        val gone = pushed.filterKeys { it !in current }.values.map { it.toRollup().zeroed() }
        (changed + gone).sortedWith(compareBy({ it.lastListenedAt }, { it.localDay }, { it.episodeId })).take(limit)
    }

    /** Records [days] as the cloud now holds them; a zero day leaves nothing to remember. */
    suspend fun recordPushedDays(days: List<ListeningRollupEntity>) {
        if (days.isEmpty()) return
        database.withTransaction {
            val (zero, held) = days.partition { it.isZero() }
            pushes.deletePushedDays(zero.map { PushedListeningDayEntity.of(it) })
            pushes.upsertPushedDays(held.map { PushedListeningDayEntity.of(it) })
        }
    }

    /** Forgets what was pushed (a new account or a deleted cloud copy): every day goes up again. */
    suspend fun forgetPushedDays() = pushes.deleteAllPushedDays()

    /**
     * Daily listening from the cloud. This installation's own days ([thisDeviceId]) are stored only
     * where it holds nothing of that day and episode (an account switch cleared them): it is their only
     * writer, so what it holds is never older, and its sessions keep their details. Another phone's day
     * replaces what this phone holds of that phone, day and episode only when it covers all of it (its
     * last listening is at least as late as the stored summary's and every stored session's); then the
     * sessions it sums go, and a zero day removes that day. A day older than what this phone holds (a
     * later backup of that phone was restored here) changes nothing.
     */
    suspend fun applyCloudDailyListening(days: List<ListeningRollupEntity>, thisDeviceId: String,) {
        val incoming = days.filter { it.deviceId != ListeningRollupEntity.THIS_DEVICE }
        if (incoming.isEmpty()) return
        database.withTransaction {
            val heldSessions = sessions.getAllSessions().groupBy { Triple(it.rollupDeviceId, it.localDay, it.episodeId) }
            for (day in incoming) {
                val stored = rollups.getRollup(day.deviceId, day.localDay, day.episodeId)
                val daySessions = heldSessions[Triple(day.deviceId, day.localDay, day.episodeId)].orEmpty()
                if (day.deviceId == thisDeviceId) applyOwnCloudDay(day, stored, daySessions) else applyOtherPhonesCloudDay(day, stored, daySessions)
            }
        }
    }

    /** Its own day comes back only where it holds nothing of it (after an account switch). */
    private suspend fun applyOwnCloudDay(day: ListeningRollupEntity, stored: ListeningRollupEntity?, daySessions: List<ListeningSessionEntity>,) {
        if (stored == null && daySessions.isEmpty() && !day.isZero()) rollups.upsertRollup(day)
    }

    private suspend fun applyOtherPhonesCloudDay(day: ListeningRollupEntity, stored: ListeningRollupEntity?, daySessions: List<ListeningSessionEntity>,) {
        val latestHeld = maxOf(stored?.lastListenedAt ?: Long.MIN_VALUE, daySessions.maxOfOrNull { it.endedAt } ?: Long.MIN_VALUE)
        if (day.lastListenedAt < latestHeld) return
        // A zero day: that phone no longer holds the day (it moved to another episode ID, or was removed).
        if (day.isZero()) rollups.deleteRollup(day.deviceId, day.localDay, day.episodeId) else rollups.upsertRollup(day)
        sessions.deleteSessionsOfDailySummary(day.deviceId, day.localDay, day.episodeId, day.lastListenedAt)
    }

    /** [deviceId]'s listening per day and episode: its sessions summed into its daily summaries. */
    private suspend fun dailyListeningOf(deviceId: String): List<ListeningRollupEntity> {
        val own = sessions.getAllSessions().filter { it.deviceId == deviceId }.groupBy { it.localDay to it.episodeId }
        val summaries = rollups.getRollupsOfDevice(deviceId).associateBy { it.localDay to it.episodeId }
        return (own.keys + summaries.keys).mapNotNull { key ->
            val daySessions = own[key]
            if (daySessions.isNullOrEmpty()) summaries[key] else ListeningRollupMerge.mergeSessionsIntoRollup(key.first, key.second, daySessions, summaries[key])
        }
    }
}
