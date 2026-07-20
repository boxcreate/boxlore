package cx.aswin.boxlore.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningRollupMergeTest {
    private fun session(
        id: String,
        consumedMs: Long,
        completed: Boolean = false,
        endedAt: Long = 1_000L,
        timeBucket: Int = 0,
    ) = ListeningSessionEntity(
        sessionId = id,
        episodeId = "ep-1",
        podcastId = "pod-1",
        startedAt = endedAt - consumedMs,
        endedAt = endedAt,
        consumedMs = consumedMs,
        completed = completed,
        localDay = 10L,
        timeBucket = timeBucket,
    )

    @Test
    fun mergesIntoEmptyRollupAcrossBuckets() {
        val merged =
            ListeningRollupMerge.mergeSessionsIntoRollup(
                localDay = 10L,
                episodeId = "ep-1",
                sessions =
                    listOf(
                        session("a", 100, timeBucket = 0),
                        session("b", 200, completed = true, endedAt = 2_000, timeBucket = 1),
                        session("c", 50, endedAt = 3_000, timeBucket = 2),
                        session("d", 25, endedAt = 4_000, timeBucket = 3),
                    ),
                existing = null,
            )

        assertEquals(375L, merged.consumedMs)
        assertEquals(4, merged.sessionCount)
        assertEquals(1, merged.completionCount)
        assertEquals(4_000L, merged.lastListenedAt)
        assertEquals(100L, merged.morningMs)
        assertEquals(200L, merged.afternoonMs)
        assertEquals(50L, merged.eveningMs)
        assertEquals(25L, merged.nightMs)
    }

    @Test
    fun accumulatesOntoExistingRollup() {
        val existing =
            ListeningRollupEntity(
                localDay = 10L,
                episodeId = "ep-1",
                podcastId = "pod-1",
                consumedMs = 10L,
                sessionCount = 1,
                completionCount = 0,
                lastListenedAt = 500L,
                morningMs = 10L,
                afternoonMs = 0L,
                eveningMs = 0L,
                nightMs = 0L,
            )
        val merged =
            ListeningRollupMerge.mergeSessionsIntoRollup(
                localDay = 10L,
                episodeId = "ep-1",
                sessions = listOf(session("x", 40, completed = true, endedAt = 800L, timeBucket = 0)),
                existing = existing,
            )

        assertEquals(50L, merged.consumedMs)
        assertEquals(2, merged.sessionCount)
        assertEquals(1, merged.completionCount)
        assertEquals(800L, merged.lastListenedAt)
        assertEquals(50L, merged.morningMs)
    }
}
