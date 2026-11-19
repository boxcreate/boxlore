package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.runtime.saveable.SaverScope
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistPagesStateTest {
    private fun roundTrip(state: PlaylistPagesState): PlaylistPagesState {
        val saved = with(PlaylistPagesState.Saver) { SaverScope { true }.save(state) }!!
        return PlaylistPagesState.Saver.restore(saved)!!
    }

    @Test
    fun `the open editor, name and picked shows survive saving`() {
        val state = PlaylistPagesState().apply {
            openCreate()
            nameDraft = "Comedy"
            toggle("b")
            pickAll(listOf("a", "b", "c"))
            toggle("a")
        }
        val restored = roundTrip(state)
        assertEquals(PlaylistEditor.Create, restored.editor)
        assertEquals("Comedy", restored.nameDraft)
        assertEquals(listOf("b", "c"), restored.picked)

        restored.closeEditor()
        val closed = roundTrip(restored)
        assertNull(closed.editor)
        assertTrue(closed.picked.isEmpty())
    }

    @Test
    fun `every editor encodes and decodes`() {
        listOf(PlaylistEditor.Create, PlaylistEditor.AddShows("p1"), PlaylistEditor.Rename("p1"), PlaylistEditor.Delete("p1"), null).forEach {
            assertEquals(it, PlaylistEditor.decode(PlaylistEditor.encode(it)))
        }
        assertTrue(PlaylistEditor.AddShows("p1").picksShows)
        assertFalse(PlaylistEditor.Rename("p1").picksShows)
    }

    @Test
    fun `folder chips pre-fill only followed shows not yet in the playlist`() {
        val followed = listOf(Podcast("a", "A", "", ""), Podcast("b", "B", "", ""), Podcast("c", "C", "", ""))
        val pickable = PlaylistShowPickerLogic.pickable(followed, inPlaylist = setOf("a"))
        assertEquals(listOf("b", "c"), pickable.map { it.id })
        val folders = listOf(
            SubscriptionFolder("f1", "Comedy", podcastIds = listOf("a", "b", "gone")),
            SubscriptionFolder("f2", "Only in playlist", podcastIds = listOf("a")),
        )
        assertEquals(listOf("Comedy" to listOf("b")), PlaylistShowPickerLogic.folderChips(folders, pickable).map { it.first.name to it.second })
    }

    @Test
    fun `a new playlist needs a name and adding needs a show`() {
        assertFalse(PlaylistShowPickerLogic.canConfirm(PlaylistEditor.Create, "  ", listOf("a")))
        assertTrue(PlaylistShowPickerLogic.canConfirm(PlaylistEditor.Create, "Comedy", emptyList()))
        assertFalse(PlaylistShowPickerLogic.canConfirm(PlaylistEditor.AddShows("p1"), "", emptyList()))
        assertTrue(PlaylistShowPickerLogic.canConfirm(PlaylistEditor.AddShows("p1"), "", listOf("a")))
    }
}
