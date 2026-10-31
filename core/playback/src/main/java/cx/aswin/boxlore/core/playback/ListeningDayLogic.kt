package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionDay
import cx.aswin.boxlore.core.model.ListeningDayEpisode

/** Sessions (added up by the database) and daily summaries as [ListeningDayEpisode] parts, and two parts of one day and episode added up. */
internal object ListeningDayLogic {
    /** A day's sessions of an episode, as the database added them up: the longest session is a known sitting. */
    fun ofSessionDay(row: ListeningSessionDay, podcastId: String) = ListeningDayEpisode(
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
        longestSessionMs = row.longestSessionMs,
    )

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
}
