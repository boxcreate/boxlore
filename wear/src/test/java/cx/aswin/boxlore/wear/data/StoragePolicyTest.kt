package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.ui.WatchFormat
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoragePolicyTest {
    private val mb = 1024L * 1024L
    private val day = 24L * 60L * 60L * 1000L
    private val now = 100 * day
    private val plenty = 10_000 * mb

    private fun episode(id: String) =
        WatchEpisode(id = id, showId = "1", title = "Episode $id", showTitle = "Show", artworkUrl = null, durationMinutes = 40, audioUrl = "https://example.org/$id.mp3")

    /** A library holding [stored] downloads, each given as ID, megabytes and how many days ago it was stored. */
    private fun library(vararg stored: Triple<String, Int, Int>): WatchLibraryState =
        stored.fold(WatchLibraryState()) { state, (id, megabytes, daysAgo) ->
            state.stored(episode(id), megabytes * mb, now - daysAgo * day)
        }

    // --- cleanup

    @Test
    fun `nothing is removed while downloads fit and are fresh`() {
        val state = library(Triple("a", 100, 1), Triple("b", 100, 2))

        assertTrue(StoragePolicy.cleanup(state, 500 * mb, now, playingId = null).isEmpty())
    }

    @Test
    fun `a played episode that is downloaded again is kept`() {
        val state = WatchLibraryState().markPlayed(episode("a")).stored(episode("a"), 100 * mb, now)

        assertTrue(StoragePolicy.cleanup(state, 500 * mb, now, playingId = null).isEmpty())
    }

    @Test
    fun `a download left alone for a month is removed, one played since is kept`() {
        val state =
            library(Triple("old", 50, 31), Triple("played", 50, 31), Triple("fresh", 50, 29))
                .progress(episode("played"), 60_000, 2_400_000, nowMs = now - 2 * day)

        assertEquals(listOf("old"), StoragePolicy.cleanup(state, 500 * mb, now, playingId = null))
    }

    @Test
    fun `over the limit, the least recently used go first until the rest fits`() {
        val state = library(Triple("oldest", 200, 9), Triple("middle", 200, 5), Triple("newest", 200, 1))

        assertEquals(listOf("oldest"), StoragePolicy.cleanup(state, 450 * mb, now, playingId = null))
        assertEquals(listOf("oldest", "middle"), StoragePolicy.cleanup(state, 250 * mb, now, playingId = null))
    }

    @Test
    fun `playing an old download counts as using it`() {
        val state =
            library(Triple("a", 200, 9), Triple("b", 200, 5))
                .progress(episode("a"), 60_000, 2_400_000, nowMs = now)

        assertEquals(listOf("b"), StoragePolicy.cleanup(state, 250 * mb, now, playingId = null))
    }

    @Test
    fun `the episode in the player is never removed, even when it is stale and the oldest`() {
        val state = library(Triple("playing", 300, 40), Triple("other", 300, 1))

        val removed = StoragePolicy.cleanup(state, 400 * mb, now, playingId = "playing")

        assertEquals(listOf("other"), removed)
    }

    @Test
    fun `a download still running is left alone`() {
        val state = WatchLibraryState().download(episode("a"), WatchDownload.InProgress(0.5f))

        assertTrue(StoragePolicy.cleanup(state, 1 * mb, now, playingId = null).isEmpty())
    }

    // --- room for a new download

    @Test
    fun `a download that fits starts without removing anything`() {
        val state = library(Triple("a", 100, 1))

        assertEquals(StorageRoom.Available(emptyList()), StoragePolicy.room(100 * mb, state, 500 * mb, plenty, null))
    }

    @Test
    fun `older downloads make way for a new one`() {
        val state = library(Triple("oldest", 200, 9), Triple("newer", 200, 1))

        assertEquals(StorageRoom.Available(listOf("oldest")), StoragePolicy.room(200 * mb, state, 500 * mb, plenty, null))
    }

    @Test
    fun `an episode larger than the limit is refused`() {
        val room = StoragePolicy.room(600 * mb, WatchLibraryState(), 500 * mb, plenty, null)

        assertEquals(StorageRoom.Refused(WatchDownloadProblem.OverLimit), room)
    }

    @Test
    fun `a download is refused when only the playing episode could make room`() {
        val state = library(Triple("playing", 400, 3))

        val room = StoragePolicy.room(200 * mb, state, 500 * mb, plenty, playingId = "playing")

        assertEquals(StorageRoom.Refused(WatchDownloadProblem.OverLimit), room)
    }

    // Regression: a host that gives no size let one download grow to the whole limit on top of what was stored.
    @Test
    fun `a download of unknown size is held to the limit together with what is already stored`() {
        val state = library(Triple("old", 200, 5), Triple("new", 200, 1))

        // 60 MB in: everything still fits a 500 MB limit.
        assertEquals(StorageRoom.Available(emptyList()), StoragePolicy.roomWhileArriving(60 * mb, state, 500 * mb, plenty, playingId = null))
        // 150 MB in: the least recently used download makes way.
        assertEquals(StorageRoom.Available(listOf("old")), StoragePolicy.roomWhileArriving(150 * mb, state, 500 * mb, plenty, playingId = null))
        // Past the limit by itself: refused, whatever else could be removed.
        assertEquals(StorageRoom.Refused(WatchDownloadProblem.OverLimit), StoragePolicy.roomWhileArriving(501 * mb, state, 500 * mb, plenty, playingId = null))
    }

    @Test
    fun `a download of unknown size stops when the watch itself runs short of room`() {
        val short = StoragePolicy.FREE_SPACE_FLOOR_BYTES - 1

        assertEquals(StorageRoom.Refused(WatchDownloadProblem.WatchFull), StoragePolicy.roomWhileArriving(10 * mb, library(), 500 * mb, short, playingId = null))
        // What it has written is already off the free space, so it is not counted against it twice.
        assertEquals(StorageRoom.Available(emptyList()), StoragePolicy.roomWhileArriving(400 * mb, library(), 500 * mb, StoragePolicy.FREE_SPACE_FLOOR_BYTES + mb, playingId = null))
    }

    @Test
    fun `a nearly full watch refuses a download whatever the limit`() {
        val free = StoragePolicy.FREE_SPACE_FLOOR_BYTES + 10 * mb

        assertEquals(StorageRoom.Refused(WatchDownloadProblem.WatchFull), StoragePolicy.room(50 * mb, WatchLibraryState(), 500 * mb, free, null))
        assertEquals(StorageRoom.Available(emptyList()), StoragePolicy.room(5 * mb, WatchLibraryState(), 500 * mb, free, null))
    }

    @Test
    fun `a download of unknown size may start when the watch has space`() {
        val state = library(Triple("a", 400, 1))

        assertEquals(StorageRoom.Available(emptyList()), StoragePolicy.room(null, state, 500 * mb, plenty, null))
    }

    // --- settings and state

    @Test
    fun `the default limit is one of the choices`() {
        assertTrue(StoragePolicy.DEFAULT_LIMIT_MB in StoragePolicy.LIMIT_CHOICES_MB)
        assertEquals(500 * mb, StoragePolicy.limitBytes(500))
    }

    @Test
    fun `a stored download knows its size, and removing it gives the space back`() {
        val state = library(Triple("a", 30, 0), Triple("b", 70, 0))

        assertEquals(100 * mb, state.storedBytes)
        assertEquals(70 * mb, state.download(episode("a"), WatchDownload.None).storedBytes)
        assertEquals(30 * mb, state.merged(episode("a")).storedBytes)
    }

    @Test
    fun `sizes read as megabytes, then gigabytes`() {
        assertEquals("0 MB", WatchFormat.megabytes(0, Locale.UK))
        assertEquals("30 MB", WatchFormat.megabytes(30 * mb + 200_000, Locale.UK))
        assertEquals("500 MB", WatchFormat.megabytes(500 * mb, Locale.UK))
        assertEquals("1 GB", WatchFormat.megabytes(1000 * mb, Locale.UK))
        assertEquals("2 GB", WatchFormat.megabytes(2000 * mb, Locale.UK))
        assertEquals("1.5 GB", WatchFormat.megabytes(1500 * mb, Locale.UK))
    }
}
