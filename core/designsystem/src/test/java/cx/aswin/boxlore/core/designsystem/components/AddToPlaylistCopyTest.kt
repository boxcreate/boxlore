package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.runtime.saveable.SaverScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddToPlaylistCopyTest {
    @Test
    fun `confirmations name the playlist`() {
        assertEquals("Added to Comedy", AddToPlaylistCopy.added("Comedy"))
        assertEquals("Removed from Comedy", AddToPlaylistCopy.removed("Comedy"))
    }

    @Test
    fun `show counts`() {
        assertEquals("No shows yet", PlaylistCopy.showCount(0))
        assertEquals("1 show", PlaylistCopy.showCount(1))
        assertEquals("12 shows", PlaylistCopy.showCount(12))
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
