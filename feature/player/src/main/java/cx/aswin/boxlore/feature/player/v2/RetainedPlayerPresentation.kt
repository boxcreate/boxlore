package cx.aswin.boxlore.feature.player.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState

interface PlayerPresentationController {
    var expanded: Boolean
}

internal class PlayerPresentationStore(
    var episodeId: String = "",
    var ui: FullPlayerUiState = FullPlayerUiState(),
    expanded: Boolean = false,
    /** Set once a tablet layout is in use. Until then phones keep their own state, as before tablet layouts. */
    var tabletUsed: Boolean = false,
) : PlayerPresentationController {
    override var expanded by mutableStateOf(expanded)

    /** A new episode starts fresh UI state, except a Save queue as playlist draft, which belongs to the queue. */
    fun forEpisode(id: String): FullPlayerUiState {
        if (episodeId != id) {
            episodeId = id
            ui = FullPlayerUiState().apply { saveQueueName = ui.saveQueueName }
        }
        return ui
    }

    /** Phone-only sessions use [local] but still hand it to the first tablet presentation. */
    fun uiFor(id: String, local: FullPlayerUiState): FullPlayerUiState {
        if (tabletUsed) return forEpisode(id)
        if (episodeId != id && local !== ui && local.saveQueueName == null) local.saveQueueName = ui.saveQueueName
        episodeId = id
        ui = local
        return local
    }

    fun opensExpanded(): Boolean = tabletUsed && expanded
}

internal val LocalPlayerPresentationStore = staticCompositionLocalOf<PlayerPresentationStore?> { null }

@Composable
fun playerPresentationController(): PlayerPresentationController? = LocalPlayerPresentationStore.current

// tabletUsed is saved as an Int so layouts saved without it still read their Boolean flags unchanged.
internal val PlayerPresentationStoreSaver = listSaver<PlayerPresentationStore, Any>(
    save = { store ->
        val scope: SaverScope = this
        val flags = with(FullPlayerUiStateSaver) { scope.save(store.ui) } as List<*>
        // The flags, then the player's one text value (a Save queue as playlist draft).
        listOf(store.episodeId, if (store.tabletUsed) 1 else 0) + flags.filterIsInstance<Boolean>() + flags.filterIsInstance<String>() + store.expanded
    },
    restore = { values ->
        val ui = values.drop(1).dropLast(1)
        PlayerPresentationStore(
            values.first() as String,
            requireNotNull(FullPlayerUiStateSaver.restore(ui.filterIsInstance<Boolean>() + ui.filterIsInstance<String>())),
            values.lastOrNull() as? Boolean ?: false,
            values.getOrNull(1) == 1,
        )
    },
)

/**
 * Keeps the same saved tabs when the app moves playback between docked and pane presentations.
 * Retention starts with the first [tabletLayout] composition and then lasts for this saved scope.
 */
@Composable
fun RetainedPlayerPresentation(tabletLayout: Boolean, content: @Composable () -> Unit) {
    val store = rememberSaveable(saver = PlayerPresentationStoreSaver) { PlayerPresentationStore() }
    // Set before children compose so the first tablet frame already receives the phone's state.
    if (tabletLayout) store.tabletUsed = true
    CompositionLocalProvider(LocalPlayerPresentationStore provides store, content = content)
}

/** Tablet presentations start from the current state so their first frame is never blank; phones keep their original start. */
@Composable
internal fun rememberInitialPlayerState(playbackRepository: PlaybackRepository): PlayerState {
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    return remember(playbackRepository, tablet) {
        if (tablet) playbackRepository.playerState.value.copy(position = 0, bufferedPosition = 0) else PlayerState()
    }
}
