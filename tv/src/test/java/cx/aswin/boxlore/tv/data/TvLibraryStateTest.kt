package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TvLibraryStateTest {
    private val minute = 60_000L

    private fun show(id: String) = TvShow(id, "Show $id", "Publisher", null)

    private fun episode(id: String, showId: String = "1", released: Long = 0L, minutes: Int = 60) =
        TvEpisode(id, showId, "Episode $id", "Show $showId", null, minutes, releasedAtMillis = released, audioUrl = "https://example.com/$id.mp3")

    @Test
    fun `listening moves an episode into Jump back in and the history, and finishing it takes it out`() {
        val a = episode("a")
        val started = TvLibraryState().progress(a, positionMs = 10 * minute, durationMs = 60 * minute)
        assertEquals(listOf("a"), started.jumpBackIn.map { it.id })
        assertEquals(50, started.jumpBackIn.single().minutesLeft)
        assertEquals(listOf("a"), started.historyEpisodes.map { it.id })

        // The last half minute counts as the end, as on the phone.
        val finished = started.progress(a, positionMs = 60 * minute - 10_000L, durationMs = 60 * minute)
        assertTrue(finished.jumpBackIn.isEmpty())
        assertTrue(finished.historyEpisodes.single().played)
        assertEquals(1, finished.listens["1"])
        // Marking it played again does not count a second listen.
        assertEquals(1, finished.markPlayed(a).listens["1"])
    }

    @Test
    fun `Daily Mix takes the newest unfinished episode of each followed show, newest first`() {
        val state =
            TvLibraryState().subscribe(show("1")).subscribe(show("2")).subscribe(show("3"))
                .newest(episode("old", showId = "1", released = 100), readAtMillis = 1)
                .newest(episode("new", showId = "2", released = 300), readAtMillis = 1)
                .newest(episode("done", showId = "3", released = 200), readAtMillis = 1)
                .markPlayed(episode("done", showId = "3", released = 200))
                // A show not followed never joins the mix.
                .newest(episode("stranger", showId = "9", released = 900), readAtMillis = 1)
        assertEquals(listOf("new", "old"), state.dailyMix.map { it.id })
        // New episodes are the same newest episodes until the listener starts one.
        assertEquals(listOf("new", "old"), state.newEpisodes.map { it.id })
        assertEquals(listOf("old"), state.progress(episode("new", showId = "2", released = 300), 60_000L, 600_000L).newEpisodes.map { it.id })
        assertEquals(listOf("old"), state.unsubscribe("2").dailyMix.map { it.id })
    }

    @Test
    fun `Up next keeps its order, never lists an episode twice, and hands over the first`() {
        val state = TvLibraryState().addToUpNext(episode("a")).addToUpNext(episode("b")).addToUpNext(episode("a"))
        assertEquals(listOf("a", "b"), state.upNext.map { it.id })
        val (next, rest) = state.takeNext()
        assertEquals("a", next?.id)
        assertEquals(listOf("b"), rest.upNext.map { it.id })
        assertTrue(rest.dequeue("b").upNext.isEmpty())
    }

    @Test
    fun `likes are listed newest first and keep their episode until unliked`() {
        val liked = TvLibraryState().setLiked(episode("a"), true).setLiked(episode("b"), true)
        assertEquals(listOf("b", "a"), liked.likedEpisodes.map { it.id })
        assertTrue(liked.likedEpisodes.all { it.liked })
        val unliked = liked.setLiked(episode("a"), false)
        assertEquals(listOf("b"), unliked.likedEpisodes.map { it.id })
        // Nothing else needs "a", so the TV forgets it.
        assertFalse("a" in unliked.episodes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the library is the same after a restart`(@TempDir folder: File) {
        val file = File(folder, TvLibraryStore.FILE_NAME)
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val store = TvLibraryStore(file, scope, dispatcher, writeDelayMs = 0)
        store.update { it.subscribe(show("1")).progress(episode("a"), 5 * minute, 60 * minute).setLiked(episode("b"), true) }
        scope.advanceUntilIdle()

        val reopened = TvLibraryStore(file, scope, dispatcher)
        assertEquals(store.state.value, reopened.state.value)
        assertEquals(listOf("1"), reopened.state.value.shows.map { it.id })
    }
}
