package cx.aswin.boxlore.feature.player.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf

internal class PlayerPresentationStore(
    var episodeId: String = "",
    var ui: FullPlayerUiState = FullPlayerUiState(),
) {
    fun forEpisode(id: String): FullPlayerUiState {
        if (episodeId != id) {
            episodeId = id
            ui = FullPlayerUiState()
        }
        return ui
    }
}

internal val LocalPlayerPresentationStore = staticCompositionLocalOf<PlayerPresentationStore?> { null }

internal val PlayerPresentationStoreSaver = listSaver<PlayerPresentationStore, Any>(
    save = { store ->
        val scope: SaverScope = this
        val flags = with(FullPlayerUiStateSaver) { scope.save(store.ui) } as List<*>
        listOf(store.episodeId) + flags.filterIsInstance<Boolean>()
    },
    restore = { values ->
        PlayerPresentationStore(
            values.first() as String,
            requireNotNull(FullPlayerUiStateSaver.restore(values.drop(1).filterIsInstance<Boolean>())),
        )
    },
)

/** Keeps the same saved tabs when the app moves playback between docked and pane presentations. */
@Composable
fun RetainedPlayerPresentation(content: @Composable () -> Unit) {
    val store = rememberSaveable(saver = PlayerPresentationStoreSaver) { PlayerPresentationStore() }
    CompositionLocalProvider(LocalPlayerPresentationStore provides store, content = content)
}
