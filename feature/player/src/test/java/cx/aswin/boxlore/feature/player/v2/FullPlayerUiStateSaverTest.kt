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
        state.updateSupportingContent(true)
        assertEquals(PlayerSupportingTab.Transcript, state.supportingTab)
        assertFalse(state.showInlineTranscript)
        state.showChaptersSheet = true
        state.updateSupportingContent(true)
        assertEquals(PlayerSupportingTab.Chapters, state.supportingTab)
        assertFalse(state.showChaptersSheet)
        state.showQueueSheet = true
        state.updateSupportingContent(true)
        assertEquals(PlayerSupportingTab.UpNext, state.supportingTab)
        assertFalse(state.showQueueSheet)
    }

    @Test
    fun `each compact supporting surface survives repeated tablet round trips`() {
        listOf(PlayerSupportingTab.Transcript, PlayerSupportingTab.Chapters, PlayerSupportingTab.UpNext).forEach { tab ->
            val state = FullPlayerUiState().apply {
                showInlineTranscript = tab == PlayerSupportingTab.Transcript
                showChaptersSheet = tab == PlayerSupportingTab.Chapters
                showQueueSheet = tab == PlayerSupportingTab.UpNext
                showSleepSheet = true
                isSyncEnabled = false
            }
            repeat(3) {
                state.updateSupportingContent(true)
                state.updateSupportingContent(true)
                assertEquals(tab, state.supportingTab)
                assertSurface(state, null)
                state.updateSupportingContent(false)
                assertSurface(state, tab)
                state.updateSupportingContent(false)
                assertSurface(state, tab)
                assertTrue(state.showSleepSheet)
                assertFalse(state.isSyncEnabled)
            }
        }
    }

    @Test
    fun `default tablet tab and notes never open an unsolicited compact sheet`() {
        val state = FullPlayerUiState()
        state.updateSupportingContent(true)
        state.updateSupportingContent(false)
        assertSurface(state, null)

        state.updateSupportingContent(true)
        state.selectSupportingTab(PlayerSupportingTab.Notes)
        state.updateSupportingContent(false)
        assertSurface(state, null)
        assertEquals(PlayerSupportingTab.Notes, state.supportingTab)
    }

    @Test
    fun `explicit tablet selection restores the selected compact surface`() {
        PlayerSupportingTab.entries.forEach { tab ->
            val state = FullPlayerUiState().apply { showInlineTranscript = true }
            state.updateSupportingContent(true)
            state.selectSupportingTab(tab)
            state.updateSupportingContent(false)
            assertSurface(state, tab.takeUnless { it == PlayerSupportingTab.Notes })
        }
    }

    @Test
    fun `closing compact support prevents it reopening on a later resize`() {
        val state = FullPlayerUiState().apply { showInlineTranscript = true }
        state.updateSupportingContent(true)
        state.updateSupportingContent(false)
        state.showInlineTranscript = false
        state.updateSupportingContent(true)
        state.updateSupportingContent(false)
        assertSurface(state, null)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `tablet transition intent survives local state recreation before compacting`() {
        listOf(PlayerSupportingTab.Transcript, PlayerSupportingTab.Chapters, PlayerSupportingTab.UpNext).forEach { tab ->
            val state = FullPlayerUiState()
            state.updateSupportingContent(true)
            state.selectSupportingTab(tab)
            val saved = with(FullPlayerUiStateSaver) { SaverScope { true }.save(state) } as List<Boolean>
            val restored = requireNotNull(FullPlayerUiStateSaver.restore(saved))
            restored.updateSupportingContent(false)
            assertSurface(restored, tab)
        }
    }

    @Test
    fun `retained store restores compact intent without confusing it with expansion`() {
        listOf(false, true).forEach { expanded ->
            val state = FullPlayerUiState().apply { showInlineTranscript = true }
            state.updateSupportingContent(true)
            val store = PlayerPresentationStore("rss:episode/one", state, expanded)
            val saved = with(PlayerPresentationStoreSaver) { SaverScope { true }.save(store) }
            val restored = requireNotNull(PlayerPresentationStoreSaver.restore(requireNotNull(saved)))
            assertEquals(expanded, restored.expanded)
            restored.forEpisode("rss:episode/one").updateSupportingContent(false)
            assertSurface(restored.ui, PlayerSupportingTab.Transcript)
        }
    }

    @Test
    fun `legacy retained layouts preserve flags tabs and expansion without inventing compact intent`() {
        listOf(10, 13).forEach { flagCount ->
            listOf(false, true).forEach { expanded ->
                val legacyFlags = MutableList(flagCount) { false }
                legacyFlags[3] = true
                if (flagCount == 13) legacyFlags[11] = true
                val restored = requireNotNull(PlayerPresentationStoreSaver.restore(listOf("rss:episode/one") + legacyFlags + expanded))
                assertEquals(expanded, restored.expanded)
                assertFalse(restored.tabletUsed)
                assertTrue(restored.ui.showSleepSheet)
                assertEquals(if (flagCount == 13) PlayerSupportingTab.Transcript else PlayerSupportingTab.UpNext, restored.ui.supportingTab)
                restored.ui.updateSupportingContent(true)
                restored.ui.updateSupportingContent(false)
                assertSurface(restored.ui, null)
            }
        }
    }

    @Test
    fun `presentation store retains tabs across layouts restores after recreation and resets for a new episode`() {
        val store = PlayerPresentationStore()
        val original = store.forEpisode("rss:episode/one")
        original.supportingTab = PlayerSupportingTab.Notes
        store.expanded = true
        store.tabletUsed = true
        assertEquals(original, store.forEpisode("rss:episode/one"))
        val scope = SaverScope { true }
        val saved = with(PlayerPresentationStoreSaver) { scope.save(store) }
        val restored = requireNotNull(PlayerPresentationStoreSaver.restore(requireNotNull(saved)))
        assertEquals(PlayerSupportingTab.Notes, restored.forEpisode("rss:episode/one").supportingTab)
        assertTrue(restored.expanded)
        assertTrue(restored.tabletUsed)
        assertTrue(restored.opensExpanded())
        assertEquals(PlayerSupportingTab.UpNext, restored.forEpisode("-42").supportingTab)
        assertFalse(restored.ui.supportingContentUsesTablet)
        assertFalse(restored.ui.restoreSupportingContentOnCompact)
    }

    private fun assertSurface(state: FullPlayerUiState, expected: PlayerSupportingTab?) {
        assertEquals(expected == PlayerSupportingTab.Transcript, state.showInlineTranscript)
        assertEquals(expected == PlayerSupportingTab.Chapters, state.showChaptersSheet)
        assertEquals(expected == PlayerSupportingTab.UpNext, state.showQueueSheet)
    }
}
