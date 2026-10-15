package cx.aswin.boxlore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.feature.player.v2.PlayerFullscreenOverlay
import cx.aswin.boxlore.feature.player.v2.PlayerPresentationController
import cx.aswin.boxlore.navigation.TabletDetailNavigation

/** Restores the originating pane or dock when a full-window media overlay closes. */
@Composable
internal fun rememberTabletPlayerFullscreenRequest(
    episodeId: String,
    navigation: TabletDetailNavigation,
    presentation: PlayerPresentationController?,
    onVideoChanged: (Boolean) -> Unit,
): (PlayerFullscreenOverlay, Boolean) -> Unit {
    var activeOverlay by rememberSaveable(episodeId) { mutableStateOf<PlayerFullscreenOverlay?>(null) }
    var previousImmersive by rememberSaveable(episodeId) { mutableStateOf(false) }
    var previousExpanded by rememberSaveable(episodeId) { mutableStateOf(false) }
    var previousDocked by rememberSaveable(episodeId) { mutableStateOf(false) }
    return { overlay, visible ->
        if (visible) {
            if (activeOverlay == null) {
                previousImmersive = navigation.immersive
                previousExpanded = presentation?.expanded == true
                previousDocked = navigation.playerDocked
            }
            activeOverlay = overlay
            onVideoChanged(overlay == PlayerFullscreenOverlay.Video)
            presentation?.expanded = true
            navigation.immersive = true
        } else if (activeOverlay == overlay) {
            onVideoChanged(false)
            activeOverlay = null
            navigation.immersive = previousImmersive
            navigation.playerDocked = previousDocked
            presentation?.expanded = previousExpanded
        }
    }
}
