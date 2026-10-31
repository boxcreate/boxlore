package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.database.ListeningDayTotals
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningInsightsMaintenance
import cx.aswin.boxlore.core.database.ListeningOrigins
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.PlayedShowDao
import cx.aswin.boxlore.core.database.PlayedShowEntity
import cx.aswin.boxlore.core.network.model.ListeningDaySyncDto
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.PlayedShowSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.prefs.BoxcastPrefs

/**
 * Cloud sync of this phone's daily listening, the played shows and the history rows' listening dates
 * (Room v40), so a reinstalled or new phone gets its listening time back and every phone's listening
 * counts once.
 *
 * - **Whose listening:** this installation's [ListeningOrigins]: new listening under its listening
 *   device ID (`BoxcastPrefs.getOrCreateListeningDeviceId`, never in an Android backup or device transfer),
 *   so two phones never write the same totals; listening stored before v40 under its legacy origin
 *   (`BoxcastPrefs.getListeningLegacyOrigin`), claimed first, which a device transfer copies with the
 *   database, so both copies of the same old listening count once.
 * - **Push:** the days of both origins whose totals differ from what this phone knows the cloud holds,
 *   and a zero day for one it no longer holds (moved to another episode ID, or removed), oldest first
 *   and [MAX_DAYS_PER_PUSH] at a time; the played shows waiting for the push; and once, the listening dates of history rows that
 *   have them (sent with `updatedAt` [DATES_ONLY_UPDATED_AT], so the server merges the dates and keeps
 *   every playback value).
 *   All of it is recorded as sent only when the server answers that it stores listening
 *   (`listeningAccepted`); an older server ignores it, and it goes again after the server update.
 *   Each day carries its richer totals (Room v41, [ListeningDayTotals]). The first time a server answers
 *   a push or a pull saying it stores them (`listeningTotals`), every day with totals goes up once more,
 *   so days sent before reach the cloud with them, whichever of the app and the server updated first.
 *   Pushes say they carry totals (`SyncPushRequest.listeningTotals`), so the server keeps a day's
 *   stored totals when an older build pushes it. The time with a known release date (Room v42,
 *   [ListeningDayTotals.releaseKnownMs]) goes the same way under its own flag (`listeningReleaseKnown`):
 *   the first server answer saying it stores it sends every day that knows it once more, and pushes
 *   say they carry it.
 * - **Pull:** every phone's days and the played shows since the pulled mark (0 on the first pull, even
 *   when the history mark is recent; then the last pull's syncedAt - 1), stored by
 *   [ListeningInsightsMaintenance.applyCloudDailyListening]: another origin's day replaces only listening
 *   of it that it covers, and this installation's own day comes back only where it holds nothing.
 * - **Reset** (an account switch or a deleted cloud copy): everything goes up again.
 */
internal class ListeningSync(
    private val maintenance: ListeningInsightsMaintenance,
    private val playedShows: PlayedShowDao,
    private val history: ListeningHistoryDao,
    private val prefs: BoxcastPrefs,
) {
    /** What one push sends. */
    data class Outgoing(val days: List<ListeningRollupEntity>, val shows: List<PlayedShowEntity>, val dates: List<ListeningHistoryEntity>,) {
        val isEmpty: Boolean get() = days.isEmpty() && shows.isEmpty() && dates.isEmpty()
        val dayDtos: List<ListeningDaySyncDto> get() = days.map { it.toDto() }
        val showDtos: List<PlayedShowSyncDto> get() = shows.map { it.toDto() }
        val dateDtos: List<ListeningHistorySyncDto> get() = dates.map { it.toDatesDto() }
    }

    suspend fun outgoing(): Outgoing {
        val origins = origins()
        maintenance.claimLegacyListening(origins.legacy)
        val dates =
            if (prefs.areListeningDatesSent()) emptyList() else history.getHistoryWithListeningDatesAfter(prefs.getListeningDatesSentAfter(), MAX_DATES_PER_PUSH)
        return Outgoing(
            days = maintenance.listeningDaysToPush(origins.pushed, MAX_DAYS_PER_PUSH),
            shows = playedShows.getDirtyPlayedShows(MAX_SHOWS_PER_PUSH),
            dates = dates,
        )
    }

    /** After a push: records what the server stored, when it says it stores listening. */
    suspend fun onPushed(sent: Outgoing, listeningAccepted: Boolean, listeningTotals: Boolean = false, listeningReleaseKnown: Boolean = false,) {
        if (!listeningAccepted) return
        maintenance.recordPushedDays(sent.days)
        if (listeningTotals) onServerStoresTotals()
        if (listeningReleaseKnown) onServerStoresReleaseKnown()
        for (show in sent.shows) playedShows.markPlayedShowSynced(show)
        if (!prefs.areListeningDatesSent()) {
            sent.dates.lastOrNull()?.let { prefs.setListeningDatesSentAfter(it.episodeId) }
            if (sent.dates.size < MAX_DATES_PER_PUSH) prefs.setListeningDatesSent(true)
        }
    }

    fun pulledUntil(): Long = prefs.getListeningPulledUntil()

    /** Stores a pull's listening; a pull from an older server (no listening in it) moves nothing. */
    suspend fun applyPull(body: SyncPullResponse) {
        if (body.listeningTotals) onServerStoresTotals()
        if (body.listeningReleaseKnown) onServerStoresReleaseKnown()
        val days = body.listeningDays
        val shows = body.playedShows
        if (days == null && shows == null) return
        maintenance.applyCloudDailyListening(
            days.orEmpty().mapNotNull { it.toEntity() },
            origins(),
            cloudStoresTotals = body.listeningTotals,
            cloudStoresReleaseKnown = body.listeningReleaseKnown,
        )
        playedShows.mergePlayedShows(shows.orEmpty().mapNotNull { it.toEntity() }, fromCloud = true)
        // A push stored in the same millisecond after this pull carries syncedAt itself; the next pull
        // reads that millisecond again (merging what it brings is idempotent).
        prefs.setListeningPulledUntil((body.syncedAt - 1L).coerceAtLeast(0L))
    }

    /** Starts over for another cloud copy: every day, played show and listening date goes up again. */
    suspend fun reset() {
        maintenance.forgetPushedDays()
        playedShows.markAllPlayedShowsDirty()
        prefs.setListeningPulledUntil(0L)
        prefs.setListeningDatesSentAfter("")
        prefs.setListeningDatesSent(false)
    }

    /**
     * The first time a server says it stores the days' richer totals (on a push or a pull, which every
     * sync makes even with nothing to push), every day with totals goes up again once, so days sent
     * before (without them, or to a server that dropped them) reach the cloud with them. Only the totals
     * are forgotten: a day pushed before that is gone still gets its zero day.
     */
    private suspend fun onServerStoresTotals() {
        if (prefs.areListeningTotalsSent()) return
        maintenance.forgetPushedTotals()
        prefs.setListeningTotalsSent(true)
    }

    /**
     * The first time a server says it stores the days' time with a known release date (Room v42),
     * every day that knows it goes up again once, so days sent before to a server that dropped it reach
     * the cloud with it.
     */
    private suspend fun onServerStoresReleaseKnown() {
        if (prefs.areListeningReleaseKnownSent()) return
        maintenance.forgetPushedReleaseKnown()
        prefs.setListeningReleaseKnownSent(true)
    }

    private fun origins() = ListeningOrigins(prefs.getOrCreateListeningDeviceId(), prefs.getListeningLegacyOrigin())

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
            totals = ListeningDayTotals(
                longestSessionMs = longestSessionMs.coerceAtLeast(0L),
                speakerMs = speakerMs.coerceAtLeast(0L),
                headphonesMs = headphonesMs.coerceAtLeast(0L),
                bluetoothMs = bluetoothMs.coerceAtLeast(0L),
                carMs = carMs.coerceAtLeast(0L),
                castMs = castMs.coerceAtLeast(0L),
                loreMs = loreMs.coerceAtLeast(0L),
                smartQueueMs = smartQueueMs.coerceAtLeast(0L),
                newReleaseMs = newReleaseMs.coerceAtLeast(0L),
                releaseKnownMs = releaseKnownMs.coerceAtLeast(0L),
            ),
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
        const val MAX_DATES_PER_PUSH = 100

        /**
         * The playback clock of a row sent only for its listening dates: older than every clock the
         * server stores, so it never replaces a playback value. Never 0: the app leaves 0 out of the
         * JSON, and the server (every build) stores a row without one at its own current time.
         */
        const val DATES_ONLY_UPDATED_AT = 1L

        private fun ListeningRollupEntity.toDto() = ListeningDaySyncDto(
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
            longestSessionMs = totals.longestSessionMs,
            speakerMs = totals.speakerMs,
            headphonesMs = totals.headphonesMs,
            bluetoothMs = totals.bluetoothMs,
            carMs = totals.carMs,
            castMs = totals.castMs,
            loreMs = totals.loreMs,
            smartQueueMs = totals.smartQueueMs,
            newReleaseMs = totals.newReleaseMs,
            releaseKnownMs = totals.releaseKnownMs,
        )

        private fun PlayedShowEntity.toDto() = PlayedShowSyncDto(
            podcastId = podcastId,
            podcastName = podcastName.takeIf { it.isNotBlank() },
            imageUrl = imageUrl,
            genre = genre,
            firstPlayedAt = firstPlayedAt,
            lastPlayedAt = lastPlayedAt,
        )

        /**
         * A history row carrying only its listening dates in effect: [DATES_ONLY_UPDATED_AT] loses to
         * every stored row's playback clock, so the server keeps its playback values and merges the dates.
         */
        private fun ListeningHistoryEntity.toDatesDto() = ListeningHistorySyncDto(
            episodeId = episodeId,
            podcastId = podcastId,
            episodeTitle = episodeTitle.takeIf { it.isNotBlank() },
            podcastName = podcastName.takeIf { it.isNotBlank() },
            progressMs = progressMs,
            durationMs = durationMs,
            isCompleted = isCompleted,
            isLiked = isLiked,
            likedAt = likedAt,
            lastPlayedAt = lastPlayedAt,
            updatedAt = DATES_ONLY_UPDATED_AT,
            firstPlayedAt = firstPlayedAt,
            finishedAt = finishedAt,
        )
    }
}
