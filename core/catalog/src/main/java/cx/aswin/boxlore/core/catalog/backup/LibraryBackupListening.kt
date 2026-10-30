package cx.aswin.boxlore.core.catalog.backup

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
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
    /** The sync device ID of the phone that made the backup: its own sessions and summaries carry it. */
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
 * Exports and restores [ListeningBackup]. This phone's own sessions and summaries are stored without
 * a phone (`deviceId` null and [ListeningRollupEntity.THIS_DEVICE]) and exported under [thisDeviceId];
 * a backup's rows of [thisDeviceId] come back as this phone's, and another phone's keep its ID, so
 * a later cloud sync knows whose listening each is and never counts it twice.
 */
internal class LibraryBackupListening(private val database: BoxLoreDatabase, private val thisDeviceId: String?,) {
    suspend fun export(): ListeningBackup {
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
     * not among [importedShowIds] (their feeds weren't restored). A daily summary is kept when it
     * sums more than the one stored and replaces the sessions it sums; a session is added unless a
     * summary of its phone, day and episode already holds it. Played shows merge. Restoring the same
     * backup again changes nothing.
     */
    suspend fun restore(backup: ListeningBackup?, refusedEpisodeIds: Set<String>, importedShowIds: Collection<String> = emptyList(),) {
        if (backup == null) return
        fun kept(episodeId: String, podcastId: String) = episodeId !in refusedEpisodeIds && !(podcastId.startsWith(RSS_PREFIX) && podcastId !in importedShowIds)
        val summaries = backup.dailySummaries.orEmpty().mapNotNull { it.toEntity() }.filter { kept(it.episodeId, it.podcastId) }
        val sessions = backup.sessions.orEmpty().mapNotNull { it.toEntity() }.filter { kept(it.episodeId, it.podcastId) }
        val shows = backup.playedShows.orEmpty().mapNotNull { it.toEntity() }.filter { kept("", it.podcastId) }
        database.withTransaction {
            val rollupDao = database.listeningRollupDao()
            val sessionDao = database.listeningSessionDao()
            for (summary in summaries) {
                val stored = rollupDao.getRollup(summary.deviceId, summary.localDay, summary.episodeId)
                if (stored == null || summary.consumedMs > stored.consumedMs) rollupDao.upsertRollup(summary)
                sessionDao.deleteSessionsOfDailySummary(summary.deviceId, summary.localDay, summary.episodeId)
            }
            val summarized = rollupDao.getAllRollups().map { Triple(it.deviceId, it.localDay, it.episodeId) }.toSet()
            sessionDao.upsertSessions(sessions.filter { Triple(it.rollupDeviceId, it.localDay, it.episodeId) !in summarized })
            database.playedShowDao().mergePlayedShows(shows, fromCloud = false)
        }
    }

    private fun ownOrNull(deviceId: String?): String? = deviceId?.takeIf { it.isNotBlank() && it != thisDeviceId }

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
        deviceId = deviceId ?: thisDeviceId,
        wallMs = wallMs,
        source = source,
        output = output,
        episodePublishedAt = episodePublishedAt,
    )

    private fun ListeningSessionBackup.toEntity(): ListeningSessionEntity? {
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
            deviceId = ownOrNull(deviceId),
            wallMs = wallMs?.takeIf { it > 0L },
            source = source,
            output = output,
            episodePublishedAt = episodePublishedAt?.takeIf { it > 0L },
        )
    }

    private fun ListeningRollupEntity.toBackup() = ListeningRollupBackup(
        deviceId = deviceId.takeIf { it != ListeningRollupEntity.THIS_DEVICE } ?: thisDeviceId,
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
    )

    private fun ListeningRollupBackup.toEntity(): ListeningRollupEntity? {
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
            deviceId = ownOrNull(deviceId) ?: ListeningRollupEntity.THIS_DEVICE,
            wallMs = wallMs?.coerceAtLeast(0L) ?: 0L,
        )
    }

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
