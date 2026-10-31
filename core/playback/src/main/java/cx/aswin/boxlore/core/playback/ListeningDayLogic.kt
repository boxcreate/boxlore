package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningDayTotals
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
        speakerMs = row.speakerMs,
        headphonesMs = row.headphonesMs,
        bluetoothMs = row.bluetoothMs,
        carMs = row.carMs,
        castMs = row.castMs,
        loreMs = row.loreMs,
        smartQueueMs = row.smartQueueMs,
        newReleaseMs = row.newReleaseMs,
        releaseKnownMs = row.releaseKnownMs,
    )

    /** A daily summary: its richer totals as stored (Room v41); the longest session, else known only when it sums one session. */
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
        longestSessionMs = row.knownTotals().longestSessionMs,
        speakerMs = row.totals.speakerMs,
        headphonesMs = row.totals.headphonesMs,
        bluetoothMs = row.totals.bluetoothMs,
        carMs = row.totals.carMs,
        castMs = row.totals.castMs,
        loreMs = row.totals.loreMs,
        smartQueueMs = row.totals.smartQueueMs,
        newReleaseMs = row.totals.newReleaseMs,
        releaseKnownMs = row.totals.releaseKnownMs,
    )

    fun sum(a: ListeningDayEpisode, b: ListeningDayEpisode) = a.copy(
        consumedMs = a.consumedMs + b.consumedMs,
        sessionCount = a.sessionCount + b.sessionCount,
        lastListenedAt = maxOf(a.lastListenedAt, b.lastListenedAt),
        morningMs = a.morningMs + b.morningMs,
        afternoonMs = a.afternoonMs + b.afternoonMs,
        eveningMs = a.eveningMs + b.eveningMs,
        nightMs = a.nightMs + b.nightMs,
        // Not known when a part without a known longest could hold a longer sitting.
        longestSessionMs = ListeningDayTotals.longestOf(a.longestSessionMs, a.consumedMs, b.longestSessionMs, b.consumedMs),
        speakerMs = a.speakerMs + b.speakerMs,
        headphonesMs = a.headphonesMs + b.headphonesMs,
        bluetoothMs = a.bluetoothMs + b.bluetoothMs,
        carMs = a.carMs + b.carMs,
        castMs = a.castMs + b.castMs,
        loreMs = a.loreMs + b.loreMs,
        smartQueueMs = a.smartQueueMs + b.smartQueueMs,
        // Within the time with a known release date, once either part knows it.
        newReleaseMs = ListeningDayTotals.newReleaseOf(a.newReleaseMs, a.releaseKnownMs, b.newReleaseMs, b.releaseKnownMs),
        releaseKnownMs = a.releaseKnownMs + b.releaseKnownMs,
    )
}
