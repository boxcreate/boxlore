package cx.aswin.boxlore.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ListeningDatesTest {
    @Test
    fun theEarliestKnownValueWinsAndBeforeRecordingIsEarliest() {
        assertNull(ListeningDates.earliest(null, null))
        assertEquals(5L, ListeningDates.earliest(null, 5L))
        assertEquals(5L, ListeningDates.earliest(5L, null))
        assertEquals(3L, ListeningDates.earliest(5L, 3L))
        assertEquals(0L, ListeningDates.earliest(0L, 3L))
        assertEquals(0L, ListeningDates.earliest(null, 0L))
    }

    @Test
    fun aNewRowKeepsItsOwnValues() {
        val row = row(first = null, finished = null, completed = true)
        assertSame(row, row.keepingListeningDatesOf(null))
    }

    @Test
    fun aWholeRowWriteKeepsTheStoredDates() {
        val written = row(first = null, finished = null).keepingListeningDatesOf(stored(first = 100L, finished = 200L))
        assertEquals(100L, written.firstPlayedAt)
        assertEquals(200L, written.finishedAt)
    }

    @Test
    fun aRowFromBeforeRecordingStaysBeforeRecording() {
        val written = row(first = 900L, finished = 900L, completed = true).keepingListeningDatesOf(stored(first = 0L, finished = 0L))
        assertEquals(0L, written.firstPlayedAt)
        assertEquals(0L, written.finishedAt)
    }

    @Test
    fun anEarlierDateFromAnotherDeviceWins() {
        val written = row(first = 50L, finished = 60L, completed = true).keepingListeningDatesOf(stored(first = 100L, finished = 200L))
        assertEquals(50L, written.firstPlayedAt)
        assertEquals(60L, written.finishedAt)
    }

    @Test
    fun aWriteFromSyncABackupOrAMarkNeverDatesAFinish() {
        val listened = row(first = null, finished = null, completed = true, playedAt = 700L).keepingListeningDatesOf(stored(first = 0L, finished = null))
        val manual = row(first = null, finished = null, completed = true, manual = true).keepingListeningDatesOf(stored(first = 0L, finished = null))
        val bulk = row(first = null, finished = null, completed = true, bulk = true).keepingListeningDatesOf(stored(first = 0L, finished = null))
        assertNull(listened.finishedAt)
        assertNull(manual.finishedAt)
        assertNull(bulk.finishedAt)
    }

    @Suppress("LongParameterList")
    private fun row(first: Long?, finished: Long?, completed: Boolean = false, manual: Boolean = false, bulk: Boolean = false, playedAt: Long = 500L,) = ListeningHistoryEntity(
        episodeId = "1",
        podcastId = "show",
        episodeTitle = "Episode",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = 0L,
        durationMs = 1_000L,
        isCompleted = completed,
        lastPlayedAt = playedAt,
        isManualCompletion = manual,
        isBulkCompletion = bulk,
        firstPlayedAt = first,
        finishedAt = finished,
    )

    private fun stored(first: Long?, finished: Long?) = StoredListeningDates("1", first, finished)
}
