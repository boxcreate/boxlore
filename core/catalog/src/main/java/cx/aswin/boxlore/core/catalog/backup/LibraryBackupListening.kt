package cx.aswin.boxlore.core.catalog.backup

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningDayTotals
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.PlayedShowEntity
import cx.aswin.boxlore.core.database.rollupDeviceId

/**
 * The timed listening of a JSON library backup (version 8+): every listening session and daily
 * summary, and the played shows. Older backups have none of it, and every field is optional.
 * Field names are restore contracts; never rename them.
 */
data class ListeningBackup(
    /** The listening device ID of the installation that made the backup (rows without one were its own). */
    val deviceId: String? = null,
    val sessions: List<ListeningSessionBackup>? = null,
    val dailySummaries: List<ListeningRollupBackup>? = null,
    val playedShows: List<PlayedShowBackup>? = null,
)

/** One `listening_sessions` row ([ListeningSessionEntity]). Gson can leave any field null. */
data class ListeningSessionBackup(
    val sessionId: String? = null,
    val episodeId: String? = null,
    val podcastId: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val consumedMs: Long? = null,
    val completed: Boolean? = null,
    val localDay: Long? = null,
    val timeBucket: Int? = null,
    val deviceId: String? = null,
    val wallMs: Long? = null,
    val source: String? = null,
    val output: String? = null,
    val episodePublishedAt: Long? = null,
)

/** One `listening_rollups` row ([ListeningRollupEntity]). Gson can leave any field null. */
data class ListeningRollupBackup(
    val deviceId: String? = null,
    val localDay: Long? = null,
    val episodeId: String? = null,
    val podcastId: String? = null,
    val consumedMs: Long? = null,
    val sessionCount: Int? = null,
    val completionCount: Int? = null,
    val lastListenedAt: Long? = null,
    val morningMs: Long? = null,
    val afternoonMs: Long? = null,
    val eveningMs: Long? = null,
    val nightMs: Long? = null,
    val wallMs: Long? = null,
    /** The day's richer totals (backup v9, Room v41); missing in older backups, read as 0 (not known). */
    val longestSessionMs: Long? = null,
    val speakerMs: Long? = null,
    val headphonesMs: Long? = null,
    val bluetoothMs: Long? = null,
    val carMs: Long? = null,
    val castMs: Long? = null,
    val loreMs: Long? = null,
    val smartQueueMs: Long? = null,
    val newReleaseMs: Long? = null,
)

/** One `played_shows` row ([PlayedShowEntity]). Gson can leave any field null. */
data class PlayedShowBackup(
    val podcastId: String? = null,
    val podcastName: String? = null,
    val imageUrl: String? = null,
    val genre: String? = null,
    val firstPlayedAt: Long? = null,
    val lastPlayedAt: Long? = null,
)

/**
 * Exports and restores [ListeningBackup]. Every session and summary carries the listening origin that
 * recorded it ([thisDeviceId] for this installation; listening stored before Room v40 is claimed under
 * [legacyOrigin] first), so a restore on another phone or after a reinstall keeps the restored listening
 * that origin's, and a cloud sync never counts it twice.
 */
internal class LibraryBackupListening(private val database: BoxLoreDatabase, private val thisDeviceId: String?, private val legacyOrigin: String? = null,) {
    suspend fun export(): ListeningBackup {
        legacyOrigin?.let { database.listeningInsightsMaintenance().claimLegacyListening(it) }
        val sessions = database.listeningSessionDao().getAllSessions()
        val summaries = database.listeningRollupDao().getAllRollups()
        return ListeningBackup(
            deviceId = thisDeviceId,
            sessions = sessions.map { it.toBackup() },
            dailySummaries = summaries.map { it.toBackup() },
            playedShows = database.playedShowDao().getAllPlayedShows().map { it.toBackup() },
        )
    }

    /**
     * Restores [backup] in one transaction, leaving out what the history restore also skipped: the
     * timing of [refusedEpisodeIds] (IDs this phone holds for another episode) and of `rss:` shows
     * not among [importedShowIds] (their feeds weren't restored). Per phone, day and episode, the later
     * summary is kept (the one with the later last listening, then the larger total), and the sessions it
     * sums go: those that ended by its last listening. A session is added unless such a summary already
     * sums it, so sessions newer than a summary stay beside it. Played shows merge. Restoring the same
     * backup again changes nothing.
     */
    suspend fun restore(backup: ListeningBackup?, refusedEpisodeIds: Set<String>, importedShowIds: Collection<String> = emptyList(),) {
        if (backup == null) return
        fun kept(episodeId: String, podcastId: String) = episodeId !in refusedEpisodeIds && !(podcastId.startsWith(RSS_PREFIX) && podcastId !in importedShowIds)
        val owner = backup.deviceId?.takeIf { it.isNotBlank() }
        val summaries = backup.dailySummaries.orEmpty().mapNotNull { it.toEntity(owner) }.filter { kept(it.episodeId, it.podcastId) }
        val sessions = backup.sessions.orEmpty().mapNotNull { it.toEntity(owner) }.filter { kept(it.episodeId, it.podcastId) }
        val shows = backup.playedShows.orEmpty().mapNotNull { it.toEntity() }.filter { kept("", it.podcastId) }
        database.withTransaction {
            val rollupDao = database.listeningRollupDao()
            val sessionDao = database.listeningSessionDao()
            for (summary in summaries) {
                val stored = rollupDao.getRollup(summary.deviceId, summary.localDay, summary.episodeId)
                if (stored != null && !summary.isLaterThan(stored)) continue
                rollupDao.upsertRollup(summary)
                sessionDao.deleteSessionsOfDailySummary(summary.deviceId, summary.localDay, summary.episodeId, summary.lastListenedAt)
            }
            val summarizedUntil = rollupDao.getAllRollups().associate { Triple(it.deviceId, it.localDay, it.episodeId) to it.lastListenedAt }
            sessionDao.upsertSessions(
                sessions.filter { session ->
                    val until = summarizedUntil[Triple(session.rollupDeviceId, session.localDay, session.episodeId)]
                    until == null || session.endedAt > until
                },
            )
            database.playedShowDao().mergePlayedShows(shows, fromCloud = false)
        }
    }

    private fun ListeningRollupEntity.isLaterThan(other: ListeningRollupEntity): Boolean =
        lastListenedAt > other.lastListenedAt || (lastListenedAt == other.lastListenedAt && consumedMs > other.consumedMs)

    private fun ListeningSessionEntity.toBackup() = ListeningSessionBackup(
        sessionId = sessionId,
        episodeId = episodeId,
        podcastId = podcastId,
        startedAt = startedAt,
        endedAt = endedAt,
        consumedMs = consumedMs,
        completed = completed,
        localDay = localDay,
        timeBucket = timeBucket,
        deviceId = deviceId,
        wallMs = wallMs,
        source = source,
        output = output,
        episodePublishedAt = episodePublishedAt,
    )

    /** A session as stored; one with no phone on it was the backing-up phone's own ([owner]). */
    private fun ListeningSessionBackup.toEntity(owner: String?): ListeningSessionEntity? {
        val id = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val episode = episodeId?.takeIf { it.isNotBlank() } ?: return null
        return ListeningSessionEntity(
            sessionId = id,
            episodeId = episode,
            podcastId = podcastId.orEmpty(),
            startedAt = startedAt ?: return null,
            endedAt = endedAt ?: return null,
            consumedMs = consumedMs?.coerceAtLeast(0L) ?: return null,
            completed = completed == true,
            localDay = localDay ?: return null,
            timeBucket = (timeBucket ?: return null).coerceIn(0, 3),
            deviceId = deviceId?.takeIf { it.isNotBlank() } ?: owner,
            wallMs = wallMs?.takeIf { it > 0L },
            source = source,
            output = output,
            episodePublishedAt = episodePublishedAt?.takeIf { it > 0L },
        )
    }

    private fun ListeningRollupEntity.toBackup() = ListeningRollupBackup(
        deviceId = deviceId,
        localDay = localDay,
        episodeId = episodeId,
        podcastId = podcastId,
        consumedMs = consumedMs,
        sessionCount = sessionCount,
        completionCount = completionCount,
        lastListenedAt = lastListenedAt,
        morningMs = morningMs,
        afternoonMs = afternoonMs,
        eveningMs = eveningMs,
        nightMs = nightMs,
        wallMs = wallMs,
        longestSessionMs = totals.longestSessionMs,
        speakerMs = totals.speakerMs,
        headphonesMs = totals.headphonesMs,
        bluetoothMs = totals.bluetoothMs,
        carMs = totals.carMs,
        castMs = totals.castMs,
        loreMs = totals.loreMs,
        smartQueueMs = totals.smartQueueMs,
        newReleaseMs = totals.newReleaseMs,
    )

    private fun ListeningRollupBackup.toEntity(owner: String?): ListeningRollupEntity? {
        val episode = episodeId?.takeIf { it.isNotBlank() } ?: return null
        return ListeningRollupEntity(
            localDay = localDay ?: return null,
            episodeId = episode,
            podcastId = podcastId.orEmpty(),
            consumedMs = consumedMs?.coerceAtLeast(0L) ?: return null,
            sessionCount = sessionCount ?: 0,
            completionCount = completionCount ?: 0,
            lastListenedAt = lastListenedAt ?: 0L,
            morningMs = morningMs ?: 0L,
            afternoonMs = afternoonMs ?: 0L,
            eveningMs = eveningMs ?: 0L,
            nightMs = nightMs ?: 0L,
            deviceId = deviceId?.takeIf { it.isNotBlank() } ?: owner ?: ListeningRollupEntity.THIS_DEVICE,
            wallMs = wallMs?.coerceAtLeast(0L) ?: 0L,
            totals = totals(),
        )
    }

    private fun ListeningRollupBackup.totals() = ListeningDayTotals(
        longestSessionMs = longestSessionMs?.coerceAtLeast(0L) ?: 0L,
        speakerMs = speakerMs?.coerceAtLeast(0L) ?: 0L,
        headphonesMs = headphonesMs?.coerceAtLeast(0L) ?: 0L,
        bluetoothMs = bluetoothMs?.coerceAtLeast(0L) ?: 0L,
        carMs = carMs?.coerceAtLeast(0L) ?: 0L,
        castMs = castMs?.coerceAtLeast(0L) ?: 0L,
        loreMs = loreMs?.coerceAtLeast(0L) ?: 0L,
        smartQueueMs = smartQueueMs?.coerceAtLeast(0L) ?: 0L,
        newReleaseMs = newReleaseMs?.coerceAtLeast(0L) ?: 0L,
    )

    private fun PlayedShowEntity.toBackup() = PlayedShowBackup(podcastId, podcastName, imageUrl, genre, firstPlayedAt, lastPlayedAt)

    private fun PlayedShowBackup.toEntity(): PlayedShowEntity? {
        val show = podcastId?.takeIf { it.isNotBlank() } ?: return null
        val last = lastPlayedAt ?: return null
        return PlayedShowEntity(
            podcastId = show,
            podcastName = podcastName.orEmpty(),
            imageUrl = imageUrl,
            genre = PlayedShowEntity.storedGenre(genre),
            firstPlayedAt = firstPlayedAt ?: 0L,
            lastPlayedAt = last,
        )
    }

    private companion object {
        const val RSS_PREFIX = "rss:"
    }
}
