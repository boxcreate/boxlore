package cx.aswin.boxlore.feature.player.v2

import cx.aswin.boxlore.core.catalog.TranscriptSegment
import cx.aswin.boxlore.core.playback.PlayerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerTranscriptActionTest {
    private val state = PlayerState(currentTranscript = listOf(TranscriptSegment(0, 1_000, "Fixture transcript")))

    @Test
    fun `phone action toggles inline transcript without changing tablet tab`() {
        val ui = FullPlayerUiState().apply { supportingTab = PlayerSupportingTab.Notes }
        togglePlayerTranscript(state, ui, false)
        assertTrue(ui.showInlineTranscript)
        assertEquals(PlayerSupportingTab.Notes, ui.supportingTab)
        togglePlayerTranscript(state, ui, false)
        assertFalse(ui.showInlineTranscript)
        assertEquals(PlayerSupportingTab.Notes, ui.supportingTab)
    }

    @Test
    fun `tablet action toggles transcript tab without opening phone content`() {
        val ui = FullPlayerUiState().apply { supportingTab = PlayerSupportingTab.Notes }
        togglePlayerTranscript(state, ui, true)
        assertEquals(PlayerSupportingTab.Transcript, ui.supportingTab)
        assertFalse(ui.showInlineTranscript)
        togglePlayerTranscript(state, ui, true)
        assertEquals(PlayerSupportingTab.UpNext, ui.supportingTab)
        assertFalse(ui.showInlineTranscript)
    }

    @Test
    fun `unavailable transcript leaves both presentations unchanged`() {
        listOf(false, true).forEach { inline ->
            val ui = FullPlayerUiState().apply {
                supportingTab = PlayerSupportingTab.Notes
                showInlineTranscript = true
            }
            togglePlayerTranscript(PlayerState(), ui, inline)
            assertEquals(PlayerSupportingTab.Notes, ui.supportingTab)
            assertTrue(ui.showInlineTranscript)
        }
    }

    @Test
    fun `phone to tablet transition keeps the transcript action on the current presentation`() {
        val ui = FullPlayerUiState()
        togglePlayerTranscript(state, ui, false)
        ui.updateSupportingContent(true)
        assertEquals(PlayerSupportingTab.Transcript, ui.supportingTab)
        assertFalse(ui.showInlineTranscript)
        togglePlayerTranscript(state, ui, true)
        assertEquals(PlayerSupportingTab.UpNext, ui.supportingTab)
        ui.updateSupportingContent(false)
        assertFalse(ui.showQueueSheet)
        togglePlayerTranscript(state, ui, false)
        assertTrue(ui.showInlineTranscript)
        assertEquals(PlayerSupportingTab.UpNext, ui.supportingTab)
    }
}
