package cx.aswin.boxlore.feature.player.v2

import cx.aswin.boxlore.core.analytics.PlayerSessionAggregator
import cx.aswin.boxlore.core.playback.PlayerState

internal fun togglePlayerTranscript(state: PlayerState, ui: FullPlayerUiState, inlineSupportingContent: Boolean) {
    if (state.currentTranscript.isEmpty()) return
    val visible = if (inlineSupportingContent) ui.supportingTab == PlayerSupportingTab.Transcript else ui.showInlineTranscript
    PlayerSessionAggregator.logAction(if (visible) "transcript_hide" else "transcript_inline")
    if (inlineSupportingContent) {
        ui.selectSupportingTab(if (visible) PlayerSupportingTab.UpNext else PlayerSupportingTab.Transcript, restoreOnCompact = !visible)
    } else {
        ui.showInlineTranscript = !ui.showInlineTranscript
    }
}
