package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddToPlaylistCopyTest {
    private fun UiText.english() = resolve(ApplicationProvider.getApplicationContext<android.content.Context>())

    @Test
    fun `confirmations name the playlist`() {
        assertEquals("Added to Comedy", AddToPlaylistCopy.added("Comedy").english())
        assertEquals("Removed from Comedy", AddToPlaylistCopy.removed("Comedy").english())
    }

    @Test
    fun `show counts`() {
        assertEquals("No shows yet", PlaylistCopy.showCount(0).english())
        assertEquals("1 show", PlaylistCopy.showCount(1).english())
        assertEquals("12 shows", PlaylistCopy.showCount(12).english())
    }

    @Test
    fun `delete asks with the playlist's name`() {
        assertEquals(
            "“Road” will be deleted. This can’t be undone. The shows, your downloads and your listening history stay as they are.",
            PlaylistCopy.deleteBody("Road").english(),
        )
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
