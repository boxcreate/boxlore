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
        val buckets =
            longArrayOf(
                existing?.morningMs ?: 0L,
                existing?.afternoonMs ?: 0L,
                existing?.eveningMs ?: 0L,
                existing?.nightMs ?: 0L,
            )
        var consumedMs = existing?.consumedMs ?: 0L
        var sessionCount = existing?.sessionCount ?: 0
        var completionCount = existing?.completionCount ?: 0
        var lastListenedAt = existing?.lastListenedAt ?: 0L

        for (session in sessions) {
            consumedMs += session.consumedMs
            sessionCount += 1
            if (session.completed) completionCount += 1
            if (session.endedAt > lastListenedAt) lastListenedAt = session.endedAt
            buckets[session.timeBucket.coerceIn(0, 3)] += session.consumedMs
        }

        return ListeningRollupEntity(
            localDay = localDay,
            episodeId = episodeId,
            podcastId = sessions.first().podcastId,
            consumedMs = consumedMs,
            sessionCount = sessionCount,
            completionCount = completionCount,
            lastListenedAt = lastListenedAt,
            morningMs = buckets[0],
            afternoonMs = buckets[1],
            eveningMs = buckets[2],
            nightMs = buckets[3],
        )
    }
}
