package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchLibraryStateTest {
    private val minute = 60_000L

    private fun show(id: String) = WatchShow(id = id, title = "Show $id", publisher = "", artworkUrl = null, subscribed = false)

    private fun episode(id: String, minutes: Int = 40) =
        WatchEpisode(
            id = id,
            showId = "1",
            title = "Episode $id",
            showTitle = "Show 1",
            artworkUrl = null,
            durationMinutes = minutes,
            audioUrl = "https://example.org/$id.mp3",
        )

    @Test
    fun `subscribing adds the show once, newest first, marked subscribed`() {
        val state = WatchLibraryState().subscribe(show("1")).subscribe(show("2")).subscribe(show("1"))

        assertEquals(listOf("2", "1"), state.shows.map { it.id })
        assertTrue(state.shows.all { it.subscribed })
        assertTrue(state.isSubscribed("1"))
    }

    @Test
    fun `unsubscribing removes only that show`() {
        val state = WatchLibraryState().subscribe(show("1")).subscribe(show("2")).unsubscribe("1")

        assertEquals(listOf("2"), state.shows.map { it.id })
        assertFalse(state.isSubscribed("1"))
    }

    @Test
    fun `progress puts an episode in Jump back in with its time left and place`() {
        val state = WatchLibraryState().progress(episode("a"), positionMs = 10 * minute, durationMs = 40 * minute)

        assertEquals(listOf("a"), state.jumpBackIn.map { it.id })
        assertEquals(30, state.jumpBackIn.single().minutesLeft)
        assertEquals(10 * minute, state.positions["a"])
    }

    @Test
    fun `the episode played most recently leads Jump back in`() {
        val state =
            WatchLibraryState()
                .progress(episode("a"), 5 * minute, 40 * minute)
                .progress(episode("b"), 5 * minute, 40 * minute)
                .progress(episode("a"), 6 * minute, 40 * minute)

        assertEquals(listOf("a", "b"), state.jumpBackIn.map { it.id })
    }

    @Test
    fun `progress uses the player's real length over the listed one`() {
        val state = WatchLibraryState().progress(episode("a", minutes = 0), positionMs = minute, durationMs = 25 * minute)

        assertEquals(25, state.episodes.getValue("a").durationMinutes)
        assertEquals(24, state.episodes.getValue("a").minutesLeft)
    }

    @Test
    fun `stopping at the very end counts as played`() {
        val state = WatchLibraryState().progress(episode("a"), positionMs = 40 * minute - 5_000, durationMs = 40 * minute)

        assertTrue(state.jumpBackIn.isEmpty())
        assertTrue("a" in state.played)
        assertNull(state.positions["a"])
    }

    @Test
    fun `marking played clears the place and keeps the episode only until the phone has heard`() {
        val started = WatchLibraryState().progress(episode("a"), 10 * minute, 40 * minute)

        val state = started.markPlayed(episode("a"))

        assertTrue(state.jumpBackIn.isEmpty())
        assertNull(state.positions["a"])
        assertEquals(0, state.merged(episode("a")).minutesLeft)
        assertTrue("a" in state.pendingEpisodes)
        assertTrue(state.episodes.containsKey("a"))
        val heard = state.copy(pendingEpisodes = emptyMap()).download(episode("a"), WatchDownload.None)
        assertFalse(heard.episodes.containsKey("a"))
        assertEquals(0, heard.merged(episode("a")).minutesLeft)
    }

    @Test
    fun `every follow, unfollow and listening change raises the revision`() {
        val state =
            WatchLibraryState()
                .subscribe(show("1"), nowMs = 5L)
                .subscribe(show("1"), nowMs = 6L)
                .progress(episode("a"), minute, 40 * minute, nowMs = 7L)
                .unsubscribe("1", nowMs = 8L)
                .unsubscribe("never followed", nowMs = 9L)
                .playNext(episode("b"))

        assertEquals(3L, state.revision)
        assertEquals(false, state.pendingShows.getValue("1").subscribed)
        assertEquals(8L, state.pendingShows.getValue("1").atMillis)
        assertEquals(mapOf("a" to 2L), state.pendingEpisodes)
        assertEquals(40 * minute, state.episodes.getValue("a").durationMs)
    }

    @Test
    fun `playing a finished episode again makes it unplayed`() {
        val state = WatchLibraryState().markPlayed(episode("a")).progress(episode("a"), minute, 40 * minute)

        assertFalse("a" in state.played)
        assertEquals(listOf("a"), state.jumpBackIn.map { it.id })
    }

    @Test
    fun `play next puts the episode first and never queues it twice`() {
        val state = WatchLibraryState().playNext(episode("a")).playNext(episode("b")).playNext(episode("a"))

        assertEquals(listOf("a", "b"), state.upNext.map { it.id })
    }

    @Test
    fun `starting a queued episode by hand takes it out of the queue`() {
        val queued = WatchLibraryState().playNext(episode("b")).playNext(episode("a"))

        assertEquals(listOf("b"), queued.dequeue("a").upNext.map { it.id })
        assertEquals(queued, queued.dequeue("not queued"))
    }

    @Test
    fun `taking the next episode shortens the queue`() {
        val queued = WatchLibraryState().playNext(episode("b")).playNext(episode("a"))

        val (next, rest) = queued.takeNext()

        assertEquals("a", next?.id)
        assertEquals(listOf("b"), rest.upNext.map { it.id })
    }

    @Test
    fun `an empty queue has nothing next`() {
        val (next, rest) = WatchLibraryState().takeNext()

        assertNull(next)
        assertEquals(WatchLibraryState(), rest)
    }

    @Test
    fun `a download is listed while running and once done`() {
        val running = WatchLibraryState().download(episode("a"), WatchDownload.InProgress(0.4f))
        val done = running.download(episode("a"), WatchDownload.Done)

        assertEquals(WatchDownload.InProgress(0.4f), running.downloads.single().download)
        assertEquals(WatchDownload.Done, done.downloads.single().download)
    }

    @Test
    fun `removing a download forgets the episode unless it is still needed`() {
        val stored = WatchLibraryState().download(episode("a"), WatchDownload.Done)
        val storedAndStarted = stored.progress(episode("a"), 5 * minute, 40 * minute)

        assertFalse(stored.download(episode("a"), WatchDownload.None).episodes.containsKey("a"))
        val kept = storedAndStarted.download(episode("a"), WatchDownload.None)
        assertEquals(WatchDownload.None, kept.episodes.getValue("a").download)
        assertEquals(listOf("a"), kept.jumpBackIn.map { it.id })
    }

    @Test
    fun `progress keeps the download and a download keeps the progress`() {
        val state =
            WatchLibraryState()
                .download(episode("a"), WatchDownload.Done)
                .progress(episode("a"), 10 * minute, 40 * minute)
                .download(episode("a"), WatchDownload.Done)

        val saved = state.episodes.getValue("a")
        assertEquals(WatchDownload.Done, saved.download)
        assertEquals(30, saved.minutesLeft)
    }

    @Test
    fun `a queue that comes along with the player goes first, in order, without repeats`() {
        val mine = WatchLibraryState().playNext(episode("own")).playNext(episode("b"))

        val taken = mine.takeOver(listOf(episode("a"), episode("b"), episode("playing"), episode("a")), playingId = "playing")

        assertEquals(listOf("a", "b", "own"), taken.upNext.map { it.id })
    }

    @Test
    fun `a queue handed to the phone leaves Up next empty and can be taken back`() {
        val mine = WatchLibraryState().playNext(episode("b")).playNext(episode("a"))
        val queued = mine.upNext

        val handed = mine.handOver()
        assertTrue(handed.upNext.isEmpty())
        assertFalse("a" in handed.episodes)

        assertEquals(listOf("a", "b"), handed.takeOver(queued).upNext.map { it.id })
    }

    @Test
    fun `the player's old copy of an episode never rewinds its download`() {
        // Download was tapped, then Play: the player keeps the copy that still says 0%.
        val atPlay = episode("a").copy(download = WatchDownload.InProgress(0f))
        val downloading = WatchLibraryState().download(episode("a"), WatchDownload.InProgress(0f)).progress(atPlay, 1 * minute, 40 * minute)

        val halfway = downloading.download(episode("a"), WatchDownload.InProgress(0.5f)).progress(atPlay, 2 * minute, 40 * minute)
        assertEquals(WatchDownload.InProgress(0.5f), halfway.episodes.getValue("a").download)

        val done = halfway.stored(episode("a"), bytes = 40_000_000L, nowMs = 5L).progress(atPlay, 3 * minute, 40 * minute)
        assertEquals(WatchDownload.Done, done.episodes.getValue("a").download)
        assertEquals(40_000_000L, done.episodes.getValue("a").storedBytes)

        // Removed downloads stay removed too, whatever the player's copy still says.
        val removed = done.download(episode("a"), WatchDownload.None).progress(atPlay.copy(download = WatchDownload.Done, storedBytes = 9L), 4 * minute, 40 * minute)
        assertEquals(WatchDownload.None, removed.episodes.getValue("a").download)
        assertEquals(0L, removed.episodes.getValue("a").storedBytes)
    }

    @Test
    fun `a fresh copy of an episode picks up what the watch saved about it`() {
        val state =
            WatchLibraryState()
                .download(episode("a"), WatchDownload.Done)
                .progress(episode("a"), 10 * minute, 40 * minute)

        val merged = state.merged(episode("a"))

        assertEquals(WatchDownload.Done, merged.download)
        assertEquals(30, merged.minutesLeft)
        assertEquals(episode("b"), state.merged(episode("b")))
    }

    @Test
    fun `downloads cut short by a restart are forgotten, finished ones kept`() {
        val state =
            WatchLibraryState()
                .download(episode("a"), WatchDownload.InProgress(0.5f))
                .download(episode("b"), WatchDownload.Done)
                .withoutUnfinishedDownloads()

        assertEquals(WatchDownload.None, state.episodes.getValue("a").download)
        assertEquals(WatchDownload.Done, state.episodes.getValue("b").download)
    }
}
