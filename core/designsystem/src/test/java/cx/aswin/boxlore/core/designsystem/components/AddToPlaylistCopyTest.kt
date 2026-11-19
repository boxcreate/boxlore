package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.runtime.saveable.SaverScope
import cx.aswin.boxlore.core.model.PLAYLIST_EPISODE_LIMIT
import cx.aswin.boxlore.core.model.Playlist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddToPlaylistCopyTest {
    @Test
    fun `the result says what was added, what was there and what didn't fit`() {
        assertEquals("Added to Commute", AddToPlaylistCopy.result("Commute", added = 1))
        assertEquals("Added 3 episodes to Commute", AddToPlaylistCopy.result("Commute", added = 3))
        assertEquals("Already in Commute", AddToPlaylistCopy.result("Commute", added = 0, alreadyIn = 2))
        assertEquals("Added 2 episodes to Commute. 1 was already in it.", AddToPlaylistCopy.result("Commute", added = 2, alreadyIn = 1))
        assertEquals("Added to Commute. 2 were already in it.", AddToPlaylistCopy.result("Commute", added = 1, alreadyIn = 2))
        assertEquals("Commute is full. Remove an episode to add more.", AddToPlaylistCopy.result("Commute", added = 0, didNotFit = 4))
        assertEquals("Added 2 episodes to Commute. It’s full, so 1 wasn’t added.", AddToPlaylistCopy.result("Commute", added = 2, didNotFit = 1))
        assertEquals("Added to Commute. It’s full, so 3 weren’t added.", AddToPlaylistCopy.result("Commute", added = 1, didNotFit = 3))
    }

    @Test
    fun `counts and details`() {
        assertEquals("No episodes yet", PlaylistCopy.episodeCount(0))
        assertEquals("1 episode", PlaylistCopy.episodeCount(1))
        assertEquals("12 episodes", PlaylistCopy.episodeCount(12))
        assertNull(AddToPlaylistCopy.subtitle(1))
        assertEquals("4 episodes", AddToPlaylistCopy.subtitle(4))
        assertEquals("500 episodes · Full", AddToPlaylistCopy.playlistDetail(Playlist("p", "P", 0L, 0L, PLAYLIST_EPISODE_LIMIT)))
        assertEquals("Saves the episode playing now and 2 episodes up next.", PlaylistCopy.saveQueueBody(2))
        assertEquals("Saves the episode playing now.", PlaylistCopy.saveQueueBody(0))
    }

    @Test
    fun `the sheet state keeps its draft through saving`() {
        val state = AddToPlaylistSheetState().apply {
            startCreating()
            newName = "Road"
        }
        val saved = with(AddToPlaylistSheetState.Saver) { SaverScope { true }.save(state) }!!
        val restored = AddToPlaylistSheetState.Saver.restore(saved)!!
        assertTrue(restored.isCreating)
        assertEquals("Road", restored.newName)

        restored.stopCreating()
        val closed = AddToPlaylistSheetState.Saver.restore(with(AddToPlaylistSheetState.Saver) { SaverScope { true }.save(restored) }!!)!!
        assertFalse(closed.isCreating)
    }
}
