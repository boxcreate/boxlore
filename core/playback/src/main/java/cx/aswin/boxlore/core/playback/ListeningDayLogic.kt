package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.ListeningDayEpisode

/** Sessions and daily summaries as [ListeningDayEpisode] parts, and two parts of one day and episode added up. */
internal object ListeningDayLogic {
    /** A session: its time in its time-of-day bucket, and its time as a known sitting. */
    fun ofSession(row: ListeningSessionEntity, podcastId: String): ListeningDayEpisode {
        val ms = row.consumedMs
        return ListeningDayEpisode(
            localDay = row.localDay,
            episodeId = row.episodeId,
            podcastId = podcastId,
            consumedMs = ms,
            sessionCount = 1,
            lastListenedAt = row.endedAt,
            morningMs = if (row.timeBucket == MORNING) ms else 0L,
            afternoonMs = if (row.timeBucket == AFTERNOON) ms else 0L,
            eveningMs = if (row.timeBucket == EVENING) ms else 0L,
            nightMs = if (row.timeBucket == NIGHT) ms else 0L,
            longestSessionMs = ms,
        )
    }

    /** A daily summary: its sitting is known only when it sums one session. */
    fun ofRollup(row: ListeningRollupEntity, podcastId: String) = ListeningDayEpisode(
        localDay = row.localDay,
        episodeId = row.episodeId,
        podcastId = podcastId,
        consumedMs = row.consumedMs,
        sessionCount = row.sessionCount,
        lastListenedAt = row.lastListenedAt,
        morningMs = row.morningMs,
        afternoonMs = row.afternoonMs,
        eveningMs = row.eveningMs,
        nightMs = row.nightMs,
        longestSessionMs = if (row.sessionCount == 1) row.consumedMs else 0L,
    )

    fun sum(a: ListeningDayEpisode, b: ListeningDayEpisode) = a.copy(
        consumedMs = a.consumedMs + b.consumedMs,
        sessionCount = a.sessionCount + b.sessionCount,
        lastListenedAt = maxOf(a.lastListenedAt, b.lastListenedAt),
        morningMs = a.morningMs + b.morningMs,
        afternoonMs = a.afternoonMs + b.afternoonMs,
        eveningMs = a.eveningMs + b.eveningMs,
        nightMs = a.nightMs + b.nightMs,
        longestSessionMs = maxOf(a.longestSessionMs, b.longestSessionMs),
    )

    private const val MORNING = 0
    private const val AFTERNOON = 1
    private const val EVENING = 2
    private const val NIGHT = 3
}
