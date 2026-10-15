package cx.aswin.boxlore.feature.player.v2

import androidx.compose.runtime.saveable.SaverScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FullPlayerUiStateSaverTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun `saver preserves showRemoveDownloadDialog state`() {
        val original = FullPlayerUiState().apply {
            showRemoveDownloadDialog = true
        }

        val scope = SaverScope { true }
        val saved = with(FullPlayerUiStateSaver) { scope.save(original) } as List<Boolean>
        val restored = FullPlayerUiStateSaver.restore(saved)

        assertTrue(restored != null)
        assertTrue(restored!!.showRemoveDownloadDialog)
    }

    @Test
    fun `restores safely from legacy saved state without out of bounds`() {
        // Prior versions had only 10 elements
        val legacyList = List(10) { false }
        val restored = FullPlayerUiStateSaver.restore(legacyList)

        assertTrue(restored != null)
        assertFalse(restored!!.showRemoveDownloadDialog)
        assertEquals(PlayerSupportingTab.UpNext, restored.supportingTab)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `every supporting tab survives restoration without changing legacy flags`() {
        PlayerSupportingTab.entries.forEach { tab ->
            val original = FullPlayerUiState().apply {
                supportingTab = tab
                showSleepSheet = true
                isSyncEnabled = false
            }
            val scope = SaverScope { true }
            val saved = with(FullPlayerUiStateSaver) { scope.save(original) } as List<Boolean>
            val restored = requireNotNull(FullPlayerUiStateSaver.restore(saved))
            assertEquals(tab, restored.supportingTab)
            assertTrue(restored.showSleepSheet)
            assertFalse(restored.isSyncEnabled)
        }
    }

    @Test
    fun `resizing an open supporting surface moves it to its tablet tab`() {
        val state = FullPlayerUiState().apply { showInlineTranscript = true }
        state.moveSupportingContentToTabs()
        assertEquals(PlayerSupportingTab.Transcript, state.supportingTab)
        assertFalse(state.showInlineTranscript)
        state.showChaptersSheet = true
        state.moveSupportingContentToTabs()
        assertEquals(PlayerSupportingTab.Chapters, state.supportingTab)
        assertFalse(state.showChaptersSheet)
        state.showQueueSheet = true
        state.moveSupportingContentToTabs()
        assertEquals(PlayerSupportingTab.UpNext, state.supportingTab)
        assertFalse(state.showQueueSheet)
    }

    @Test
    fun `presentation store retains tabs across layouts restores after recreation and resets for a new episode`() {
        val store = PlayerPresentationStore()
        val original = store.forEpisode("rss:episode/one")
        original.supportingTab = PlayerSupportingTab.Notes
        assertEquals(original, store.forEpisode("rss:episode/one"))
        val scope = SaverScope { true }
        val saved = with(PlayerPresentationStoreSaver) { scope.save(store) }
        val restored = requireNotNull(PlayerPresentationStoreSaver.restore(requireNotNull(saved)))
        assertEquals(PlayerSupportingTab.Notes, restored.forEpisode("rss:episode/one").supportingTab)
        assertEquals(PlayerSupportingTab.UpNext, restored.forEpisode("-42").supportingTab)
    }
}
