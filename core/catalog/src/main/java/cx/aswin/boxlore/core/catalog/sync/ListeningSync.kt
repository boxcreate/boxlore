package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.database.ListeningInsightsMaintenance
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.PlayedShowDao
import cx.aswin.boxlore.core.database.PlayedShowEntity
import cx.aswin.boxlore.core.network.model.ListeningDaySyncDto
import cx.aswin.boxlore.core.network.model.PlayedShowSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.prefs.BoxcastPrefs

/**
 * Cloud sync of this phone's daily listening and the played shows (Room v40), so a reinstalled or new
 * phone gets its listening time back and every phone's listening counts once.
 *
 * - **Push:** this phone's days whose last listening is after the pushed mark (all of them on the
 *   first push), oldest first, [MAX_DAYS_PER_PUSH] at a time under its sync device ID, and the played
 *   shows waiting for the push. The mark moves, and the shows are marked sent, only when the server
 *   answers that it stored listening (`listeningAccepted`); an older server ignores it all, so nothing
 *   is lost before the server update. The last [OVERLAP_MS] of days go again with the next push, so a
 *   session saved just after a push still goes.
 * - **Pull:** every phone's days and the played shows since the pulled mark (0 on the first pull, even
 *   when the history mark is recent; then the last pull's syncedAt - 1). Another phone's days are stored under its ID; this phone's own
 *   only where it holds nothing ([ListeningInsightsMaintenance.applyCloudDailyListening]).
 * - **Account switch:** both marks go back to 0.
 */
internal class ListeningSync(
    private val maintenance: ListeningInsightsMaintenance,
    private val playedShows: PlayedShowDao,
    private val prefs: BoxcastPrefs,
) {
    /** What one push sends, and the pushed mark it reaches once stored. */
    data class Outgoing(val days: List<ListeningDaySyncDto>, val shows: List<PlayedShowEntity>, val pushedUntil: Long?,) {
        val isEmpty: Boolean get() = days.isEmpty() && shows.isEmpty()
        val showDtos: List<PlayedShowSyncDto> get() = shows.map { it.toDto() }
    }

    suspend fun outgoing(): Outgoing {
        val pushedUntil = prefs.getListeningPushedUntil()
        val changedAfter = if (pushedUntil <= 0L) -1L else pushedUntil - OVERLAP_MS
        val days = maintenance.ownDailyListening(changedAfter, MAX_DAYS_PER_PUSH)
        val deviceId = prefs.getOrCreateSyncDeviceId()
        return Outgoing(
            days = days.map { it.toDto(deviceId) },
            shows = playedShows.getDirtyPlayedShows(MAX_SHOWS_PER_PUSH),
            pushedUntil = days.maxOfOrNull { it.lastListenedAt }?.let { maxOf(it, pushedUntil) },
        )
    }

    /** After a push: marks what the server stored as sent, when it says it stores listening. */
    suspend fun onPushed(sent: Outgoing, listeningAccepted: Boolean,) {
        if (!listeningAccepted) return
        sent.pushedUntil?.let(prefs::setListeningPushedUntil)
        for (show in sent.shows) playedShows.markPlayedShowSynced(show.podcastId, show.lastPlayedAt)
    }

    fun pulledUntil(): Long = prefs.getListeningPulledUntil()

    /** Stores a pull's listening; a pull from an older server (no listening in it) moves nothing. */
    suspend fun applyPull(body: SyncPullResponse) {
        val days = body.listeningDays
        val shows = body.playedShows
        if (days == null && shows == null) return
        maintenance.applyCloudDailyListening(days.orEmpty().mapNotNull { it.toEntity() }, prefs.getOrCreateSyncDeviceId())
        playedShows.mergePlayedShows(shows.orEmpty().mapNotNull { it.toEntity() }, fromCloud = true)
        // A push stored in the same millisecond after this pull carries syncedAt itself; the next pull
        // reads that millisecond again (merging what it brings is idempotent).
        prefs.setListeningPulledUntil((body.syncedAt - 1L).coerceAtLeast(0L))
    }

    fun reset() {
        prefs.setListeningPushedUntil(0L)
        prefs.setListeningPulledUntil(0L)
    }

    private fun ListeningRollupEntity.toDto(deviceId: String) = ListeningDaySyncDto(
        deviceId = deviceId,
        localDay = localDay,
        episodeId = episodeId,
        podcastId = podcastId,
        consumedMs = consumedMs,
        wallMs = wallMs,
        sessionCount = sessionCount,
        completionCount = completionCount,
        morningMs = morningMs,
        afternoonMs = afternoonMs,
        eveningMs = eveningMs,
        nightMs = nightMs,
        lastListenedAt = lastListenedAt,
    )

    private fun ListeningDaySyncDto.toEntity(): ListeningRollupEntity? {
        if (deviceId.isBlank() || episodeId.isBlank() || localDay < 0L) return null
        return ListeningRollupEntity(
            localDay = localDay,
            episodeId = episodeId,
            podcastId = podcastId,
            consumedMs = consumedMs.coerceAtLeast(0L),
            sessionCount = sessionCount.coerceAtLeast(0),
            completionCount = completionCount.coerceAtLeast(0),
            lastListenedAt = lastListenedAt,
            morningMs = morningMs,
            afternoonMs = afternoonMs,
            eveningMs = eveningMs,
            nightMs = nightMs,
            deviceId = deviceId,
            wallMs = wallMs.coerceAtLeast(0L),
        )
    }

    private fun PlayedShowSyncDto.toEntity(): PlayedShowEntity? {
        if (podcastId.isBlank()) return null
        return PlayedShowEntity(
            podcastId = podcastId,
            podcastName = podcastName.orEmpty(),
            imageUrl = imageUrl,
            genre = PlayedShowEntity.storedGenre(genre),
            firstPlayedAt = firstPlayedAt.coerceAtLeast(0L),
            lastPlayedAt = lastPlayedAt,
            isDirty = false,
        )
    }

    companion object {
        /** Well under the server's 1,000 per push and its 1 MiB body limit, with history and the queue beside them. */
        const val MAX_DAYS_PER_PUSH = 250
        const val MAX_SHOWS_PER_PUSH = 250

        /** Days whose last listening is this close to the pushed mark go again (a late-saved session). */
        const val OVERLAP_MS = 5 * 60 * 1_000L

        private fun PlayedShowEntity.toDto() = PlayedShowSyncDto(
            podcastId = podcastId,
            podcastName = podcastName.takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
            genre = genre,
            firstPlayedAt = firstPlayedAt,
            lastPlayedAt = lastPlayedAt,
        )
    }
}
