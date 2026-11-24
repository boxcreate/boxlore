package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreSnackbarHost
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreBottomChromeReserved
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.feature.player.fitVisibleHeight

/**
 * The player's queue message: boxlore's shared snackbar at the foot of the part of the player that is
 * on screen ([visibleHeight]; the sheet runs past the screen's foot). The open player covers the app's
 * bottom bar and mini player, so only [bottomInset] (system navigation) is kept clear.
 */
@Composable
internal fun PlayerSnackbarHost(hostState: SnackbarHostState, visibleHeight: Dp, bottomInset: Dp) {
    Box(Modifier.fitVisibleHeight(visibleHeight).fillMaxSize()) {
        CompositionLocalProvider(
            LocalBoxloreBottomChromeReserved provides true,
            LocalBoxloreMiniPlayerVisible provides false,
        ) {
            BoxloreSnackbarHost(hostState, Modifier.align(Alignment.BottomCenter), extraBottom = bottomInset)
        }
    }
}
