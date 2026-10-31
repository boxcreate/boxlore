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
     * Gives timed listening stored before Room v40 (stored without an origin) to [legacyOrigin]
     * ([ListeningOrigins.legacy]): sessions, and summaries (merged into a summary of the same day and
     * episode under it, if any). Listening recorded since is stored under the installation's own ID.
     */
    suspend fun claimLegacyListening(legacyOrigin: String) {
        database.withTransaction {
            sessions.claimLegacySessions(legacyOrigin)
            for (legacy in rollups.getRollupsOfDevice(ListeningRollupEntity.THIS_DEVICE)) {
                val claimed = rollups.getRollup(legacyOrigin, legacy.localDay, legacy.episodeId)
                rollups.upsertRollup(claimed?.let { ListeningRollupMerge.sum(it, legacy) } ?: legacy.copy(deviceId = legacyOrigin))
                rollups.deleteRollup(ListeningRollupEntity.THIS_DEVICE, legacy.localDay, legacy.episodeId)
            }
        }
    }

    /**
     * The days of [origins]' listening ([ListeningOrigins.pushed]) the cloud doesn't hold as they are
     * now, oldest first, at most [limit]: each day whose total (its sessions summed into its summary)
     * differs from what this phone knows the cloud holds, and a zero day for each day the cloud holds
     * that this phone no longer does (its listening moved to another episode ID, or was removed).
     * [recordPushedDays] records them once stored.
     */
    suspend fun listeningDaysToPush(origins: Collection<String>, limit: Int,): List<ListeningRollupEntity> = database.withTransaction {
        val current = dailyListeningOf(origins.toSet()).associateBy { it.key }
        val pushed = origins.distinct().flatMap { pushes.getPushedDays(it) }.associateBy { Triple(it.deviceId, it.localDay, it.episodeId) }
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

    /** Forgets what the cloud holds (a new account or a deleted cloud copy): every day goes up again. */
    suspend fun forgetPushedDays() = pushes.deleteAllPushedDays()

    /**
     * Forgets only the richer totals the cloud holds (a server that now stores them): every day with
     * totals goes up again, and days pushed before that are gone still get their zero day.
     */
    suspend fun forgetPushedTotals() = pushes.forgetPushedTotals()

    /**
     * Daily listening from the cloud, in one transaction.
     * - The installation's own days ([ListeningOrigins.own]): it is their only writer, so what it holds
     *   is never older, and its sessions keep their details. A day comes back only where it holds
     *   nothing of it and didn't know the cloud held it (an account switch cleared both). A day it
     *   knew of and no longer holds was removed or moved here, and its next push zeroes it.
     * - Every other origin's day, its legacy origin's too (a copy of this phone's database may push
     *   it as well): it replaces what this phone holds of that origin, day and episode only when it
     *   covers all of it ([covers]); then the sessions it sums go, and a zero day removes the day. An
     *   older day (a later backup of that phone was restored here) changes nothing.
     * Days of the origins this phone pushes are recorded as the cloud holds them, so its next push sends
     * only what differs.
     */
    suspend fun applyCloudDailyListening(days: List<ListeningRollupEntity>, origins: ListeningOrigins,) {
        val incoming = days.filter { it.deviceId != ListeningRollupEntity.THIS_DEVICE }
        if (incoming.isEmpty()) return
        database.withTransaction {
            val heldSessions = sessions.getAllSessions().groupBy { Triple(it.rollupDeviceId, it.localDay, it.episodeId) }
            val known = pushes.getPushedDays(origins.own).mapTo(HashSet()) { Triple(it.deviceId, it.localDay, it.episodeId) }
            for (day in incoming) applyCloudDay(day, heldSessions[day.key].orEmpty(), isOwn = day.deviceId == origins.own, known = day.key in known)
            recordPushedDays(incoming.filter { it.deviceId in origins.pushed })
        }
    }

    /** One day of [applyCloudDailyListening]; [known]: this phone knew the cloud held it. */
    private suspend fun applyCloudDay(day: ListeningRollupEntity, daySessions: List<ListeningSessionEntity>, isOwn: Boolean, known: Boolean,) {
        val stored = rollups.getRollup(day.deviceId, day.localDay, day.episodeId)
        if (isOwn) {
            val holdsNothing = stored == null && daySessions.isEmpty()
            if (holdsNothing && !known && !day.isZero()) rollups.upsertRollup(day)
            return
        }
        if (!day.covers(stored, daySessions)) return
        // A zero day: that origin no longer holds the day (it moved to another episode ID, or was removed).
        if (day.isZero()) rollups.deleteRollup(day.deviceId, day.localDay, day.episodeId) else rollups.upsertRollup(day.keepingHeldTotals(stored, daySessions))
        sessions.deleteSessionsOfDailySummary(day.deviceId, day.localDay, day.episodeId, day.lastListenedAt)
    }

    /**
     * This day, with the richer totals of what this phone holds of it when the day has none (an older
     * build or server sent it) and holds just that listening: as late and as much. A later or larger day
     * has more listening than the held totals cover, so they don't stand for it.
     */
    private fun ListeningRollupEntity.keepingHeldTotals(stored: ListeningRollupEntity?, daySessions: List<ListeningSessionEntity>,): ListeningRollupEntity {
        if (totals != ListeningDayTotals()) return this
        val held = if (daySessions.isEmpty()) stored else ListeningRollupMerge.mergeSessionsIntoRollup(localDay, episodeId, daySessions, stored)
        if (held == null || held.lastListenedAt != lastListenedAt || held.consumedMs != consumedMs) return this
        return copy(totals = held.knownTotals())
    }

    /**
     * Whether this day holds all of what this phone holds of its origin, day and episode: its last
     * listening is later than the stored summary's and every stored session's, or as late and it sums
     * at least as much (a zero day as late still removes the day). Equal last listening alone doesn't
     * show coverage: a more complete copy of the same day can end at the same time.
     */
    private fun ListeningRollupEntity.covers(stored: ListeningRollupEntity?, daySessions: List<ListeningSessionEntity>,): Boolean {
        val latestHeld = maxOf(stored?.lastListenedAt ?: Long.MIN_VALUE, daySessions.maxOfOrNull { it.endedAt } ?: Long.MIN_VALUE)
        if (lastListenedAt != latestHeld) return lastListenedAt > latestHeld
        return isZero() || consumedMs >= (stored?.consumedMs ?: 0L) + daySessions.sumOf { it.consumedMs }
    }

    /** [origins]' listening per day and episode: their sessions summed into their daily summaries. */
    private suspend fun dailyListeningOf(origins: Set<String>): List<ListeningRollupEntity> {
        val held = sessions.getAllSessions().filter { it.deviceId in origins }.groupBy { Triple(it.rollupDeviceId, it.localDay, it.episodeId) }
        val summaries = origins.flatMap { rollups.getRollupsOfDevice(it) }.associateBy { it.key }
        return (held.keys + summaries.keys).mapNotNull { key ->
            val daySessions = held[key]
            if (daySessions.isNullOrEmpty()) summaries[key] else ListeningRollupMerge.mergeSessionsIntoRollup(key.second, key.third, daySessions, summaries[key])
        }
    }

    private val ListeningRollupEntity.key get() = Triple(deviceId, localDay, episodeId)
}

/**
 * The listening origins of this installation (Room v40): [own], the listening device ID it records new
 * listening under, and [legacy], the origin it claims listening stored before v40 under. It pushes both.
 */
data class ListeningOrigins(val own: String, val legacy: String,) {
    val pushed: Set<String> get() = setOf(own, legacy)
}
