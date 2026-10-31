package cx.aswin.boxlore.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningRollupMergeTest {
    private fun session(id: String, consumedMs: Long, completed: Boolean = false, endedAt: Long = 1_000L, timeBucket: Int = 0,) = ListeningSessionEntity(
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

    @Test(expected = IllegalArgumentException::class)
    fun emptySessionsThrows() {
        ListeningRollupMerge.mergeSessionsIntoRollup(
            localDay = 10L,
            episodeId = "ep-1",
            sessions = emptyList(),
            existing = null,
        )
    }

    @Test
    fun theDaysTotalsSayWhereHowAndHowFreshAndKeepTheLongestSession() {
        val day = 10L * 86_400_000L
        fun play(id: String, minutes: Long, output: String?, source: String? = null, publishedDaysBefore: Long? = null) =
            session(id, minutes * 60_000L).copy(
                startedAt = day,
                output = output,
                source = source,
                episodePublishedAt = publishedDaysBefore?.let { day - it * 86_400_000L },
            )
        val merged =
            ListeningRollupMerge.mergeSessionsIntoRollup(
                localDay = 10L,
                episodeId = "ep-1",
                sessions = listOf(
                    play("car", 30, ListeningSessionValues.OUTPUT_CAR, publishedDaysBefore = 2),
                    play("speaker", 20, ListeningSessionValues.OUTPUT_SPEAKER, ListeningSessionValues.SOURCE_LORE, publishedDaysBefore = 8),
                    play("buds", 45, ListeningSessionValues.OUTPUT_BLUETOOTH, ListeningSessionValues.SOURCE_SMART_QUEUE),
                    play("unknown", 5, output = null),
                ),
                existing = null,
            )

        assertEquals(
            ListeningDayTotals(
                longestSessionMs = 45L * 60_000L,
                speakerMs = 20L * 60_000L,
                bluetoothMs = 45L * 60_000L,
                carMs = 30L * 60_000L,
                loreMs = 20L * 60_000L,
                smartQueueMs = 45L * 60_000L,
                // Released 2 days before: new; 8 days before or unknown: not.
                newReleaseMs = 30L * 60_000L,
            ),
            merged.totals,
        )
    }

    @Test
    fun addingSummariesAddsTheirTotalsAndKeepsTheLongerSession() {
        val a = ListeningDayTotals(longestSessionMs = 40L, carMs = 10L, newReleaseMs = 5L)
        val b = ListeningDayTotals(longestSessionMs = 25L, carMs = 7L, castMs = 3L)

        assertEquals(ListeningDayTotals(longestSessionMs = 40L, carMs = 17L, castMs = 3L, newReleaseMs = 5L), a + b)
        assertEquals(ListeningDayTotals(), ListeningRollupEntity(1L, "e", "p", 9L, 1, 0, 9L, totals = a).zeroed().totals)
        val summed = ListeningRollupMerge.sum(
            ListeningRollupEntity(1L, "e", "p", 40L, 2, 0, 9L, totals = a),
            ListeningRollupEntity(1L, "e", "p", 25L, 2, 0, 8L, totals = b),
        )
        assertEquals(a + b, summed.totals)
    }

    @Test
    fun aSummaryFromBeforeV41WithOneSessionKeepsItsLengthAsTheLongestWhenAddedTo() {
        // Stored before v41: one hour-long session, no totals.
        val legacy = ListeningRollupEntity(10L, "ep-1", "p", 60L * 60_000L, sessionCount = 1, completionCount = 0, lastListenedAt = 5L)
        val later = session("later", 20L * 60_000L).copy(localDay = 10L, endedAt = 9L)

        assertEquals(60L * 60_000L, ListeningRollupMerge.mergeSessionsIntoRollup(10L, "ep-1", listOf(later), legacy).totals.longestSessionMs)
        assertEquals(60L * 60_000L, ListeningRollupMerge.sum(legacy, legacy.copy(consumedMs = 1_000L, sessionCount = 3)).totals.longestSessionMs)
        // Two sessions with no totals: their longest isn't known.
        assertEquals(0L, legacy.copy(sessionCount = 2).knownTotals().longestSessionMs)
    }

    @Test
    fun aSummaryWhoseLongestIsUnknownKeepsItUnknownWhenItCouldHideALongerSitting() {
        // Two hours over two sessions before v41: one of them was at least an hour.
        val legacy = ListeningRollupEntity(10L, "ep-1", "p", 120L * 60_000L, sessionCount = 2, completionCount = 0, lastListenedAt = 5L)
        val short = session("short", 5L * 60_000L).copy(localDay = 10L, endedAt = 9L)

        assertEquals(0L, ListeningRollupMerge.mergeSessionsIntoRollup(10L, "ep-1", listOf(short), legacy).totals.longestSessionMs)
        val known = ListeningRollupEntity(10L, "ep-1", "p", 5L * 60_000L, sessionCount = 1, completionCount = 0, lastListenedAt = 9L)
        assertEquals(0L, ListeningRollupMerge.sum(known, legacy).totals.longestSessionMs)
        // Less listening than the known sitting: it can't hide a longer one.
        assertEquals(5L * 60_000L, ListeningDayTotals.longestOf(5L * 60_000L, 5L * 60_000L, 0L, 4L * 60_000L))
    }
}
