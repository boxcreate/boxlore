package cx.aswin.boxlore.core.database

/**
 * Pure merge of raw listening sessions into a daily episode rollup row.
 * Kept outside the Room DAO so roll-up stays hermetic and under complexity limits.
 */
object ListeningRollupMerge {
    fun mergeSessionsIntoRollup(
        localDay: Long,
        episodeId: String,
        sessions: List<ListeningSessionEntity>,
        existing: ListeningRollupEntity?,
    ): ListeningRollupEntity {
        require(sessions.isNotEmpty()) { "sessions must not be empty" }
        require(sessions.all { it.rollupDeviceId == sessions.first().rollupDeviceId }) { "sessions of one phone only" }
        val start =
            existing ?: ListeningRollupEntity(
                localDay = localDay,
                episodeId = episodeId,
                podcastId = sessions.first().podcastId,
                consumedMs = 0L,
                sessionCount = 0,
                completionCount = 0,
                lastListenedAt = 0L,
                deviceId = sessions.first().rollupDeviceId,
            )
        return sessions
            .fold(start, ::addSession)
            .copy(localDay = localDay, episodeId = episodeId, podcastId = sessions.first().podcastId)
    }

    /** [rollup] with one more [session] summed in. */
    private fun addSession(rollup: ListeningRollupEntity, session: ListeningSessionEntity): ListeningRollupEntity {
        val ms = session.consumedMs
        return rollup.copy(
            consumedMs = rollup.consumedMs + ms,
            sessionCount = rollup.sessionCount + 1,
            completionCount = rollup.completionCount + if (session.completed) 1 else 0,
            lastListenedAt = maxOf(rollup.lastListenedAt, session.endedAt),
            morningMs = rollup.morningMs + if (bucket(session) == 0) ms else 0L,
            afternoonMs = rollup.afternoonMs + if (bucket(session) == 1) ms else 0L,
            eveningMs = rollup.eveningMs + if (bucket(session) == 2) ms else 0L,
            nightMs = rollup.nightMs + if (bucket(session) == 3) ms else 0L,
            wallMs = rollup.wallMs + (session.wallMs ?: 0L),
            totals = rollup.totals.plus(session),
        )
    }

    private fun bucket(session: ListeningSessionEntity): Int = session.timeBucket.coerceIn(0, 3)

    /** Two summaries of one phone, day and episode added up, keeping [a]'s key and show. */
    fun sum(a: ListeningRollupEntity, b: ListeningRollupEntity): ListeningRollupEntity = a.copy(
        consumedMs = a.consumedMs + b.consumedMs,
        sessionCount = a.sessionCount + b.sessionCount,
        completionCount = a.completionCount + b.completionCount,
        lastListenedAt = maxOf(a.lastListenedAt, b.lastListenedAt),
        morningMs = a.morningMs + b.morningMs,
        afternoonMs = a.afternoonMs + b.afternoonMs,
        eveningMs = a.eveningMs + b.eveningMs,
        nightMs = a.nightMs + b.nightMs,
        wallMs = a.wallMs + b.wallMs,
        totals = a.totals + b.totals,
    )
}
