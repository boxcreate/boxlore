package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.runtime.saveable.SaverScope
import cx.aswin.boxlore.core.model.PlaylistMix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaylistPagesStateTest {
    private fun roundTrip(state: PlaylistPagesState): PlaylistPagesState {
        val saved = with(PlaylistPagesState.Saver) { SaverScope { true }.save(state) }!!
        return PlaylistPagesState.Saver.restore(saved)!!
    }

    @Test
    fun `a new playlist in progress survives saving with every step, pick and choice`() {
        val state = PlaylistPagesState().apply {
            openCreate()
            newDraft = newDraft!!.copy(
                step = NewPlaylistDraft.FINISH,
                pickedIds = listOf("b", "c"),
                search = "cast",
                genreFilter = "Comedy",
                name = "Laughs",
                nameEdited = true,
                icon = "comedy",
                iconChosen = true,
                mix = PlaylistMix.ONE_SHOW_AT_A_TIME,
                perShowLimit = null,
                fromSuggestion = true,
                origin = "genre",
            )
        }
        assertEquals(state.newDraft, roundTrip(state).newDraft)

        state.closeFlow()
        assertNull(roundTrip(state).newDraft)
    }

    @Test
    fun `add shows opens straight on the show grid for that playlist`() {
        val state = PlaylistPagesState().apply { openAddShows("p1") }
        val restored = roundTrip(state).newDraft!!
        assertEquals(NewPlaylistMode.AddShows, restored.mode)
        assertEquals("p1", restored.playlistId)
        assertEquals(NewPlaylistDraft.PICK, restored.step)
    }

    @Test
    fun `rename and delete dialogs and the typed name survive saving`() {
        val state = PlaylistPagesState().apply { openRename("p1", "Old name") }
        state.nameDraft = "New name"
        val restored = roundTrip(state)
        assertEquals(PlaylistEditor.Rename("p1"), restored.editor)
        assertEquals("New name", restored.nameDraft)
        listOf(PlaylistEditor.Rename("p1"), PlaylistEditor.Delete("p1"), null).forEach {
            assertEquals(it, PlaylistEditor.decode(PlaylistEditor.encode(it)))
        }
        // A dialog saved by the old sheet reads as nothing open.
        assertNull(PlaylistEditor.decode("create"))
    }

    @Test
    fun `a state saved by an older version, with picked show IDs where the draft goes, opens with no draft`() {
        listOf(arrayListOf<String>(), arrayListOf("a", "b"), ArrayList((1..20).map { "show$it" })).forEach { legacyPicks ->
            val restored = PlaylistPagesState.Saver.restore(listOf("", "", legacyPicks))!!
            assertNull(restored.newDraft)
        }
        assertNull(PlaylistPagesState.Saver.restore(listOf("create", "Old name", arrayListOf("a")))!!.newDraft)
    }
}
