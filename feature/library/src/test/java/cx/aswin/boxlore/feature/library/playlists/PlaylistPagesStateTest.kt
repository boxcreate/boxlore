package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.runtime.saveable.SaverScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaylistPagesStateTest {
    private fun roundTrip(state: PlaylistPagesState): PlaylistPagesState {
        val saved = with(PlaylistPagesState.Saver) { SaverScope { true }.save(state) }!!
        return PlaylistPagesState.Saver.restore(saved)!!
    }

    @Test
    fun `the open dialog, draft and pending order survive saving`() {
        val state = PlaylistPagesState().apply {
            openRename("p1", "Commute")
            nameDraft = "Drive"
            move(listOf(1L, 2L, 3L), 3L, 1L)
        }
        val restored = roundTrip(state)
        assertEquals(PlaylistEditor.Rename("p1"), restored.editor)
        assertEquals("Drive", restored.nameDraft)
        assertEquals(listOf(3L, 1L, 2L), restored.pendingOrder)
        assertEquals(PlaylistEditor.Delete("p2"), roundTrip(PlaylistPagesState().apply { openDelete("p2") }).editor)
        assertEquals(PlaylistEditor.Create, roundTrip(PlaylistPagesState().apply { openCreate() }).editor)
    }

    @Test
    fun `a pending order settles once stored, or when its rows change`() {
        val state = PlaylistPagesState()
        state.move(listOf(1L, 2L), 2L, 1L)
        state.settle(listOf(1L, 2L))
        assertEquals(listOf(2L, 1L), state.pendingOrder)
        state.settle(listOf(2L, 1L))
        assertNull(state.pendingOrder)

        state.move(listOf(1L, 2L, 3L), 3L, 1L)
        state.settle(listOf(1L, 2L))
        assertNull(state.pendingOrder)
    }

    @Test
    fun `closing a dialog drops its draft`() {
        val state = PlaylistPagesState().apply {
            openCreate()
            nameDraft = "New"
            closeEditor()
        }
        assertNull(state.editor)
        assertEquals("", state.nameDraft)
    }
}
