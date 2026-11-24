package cx.aswin.boxlore.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import cx.aswin.boxlore.core.designsystem.components.BoxloreMenuState
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
import kotlinx.coroutines.flow.drop

/**
 * A [BoxloreMenuState] that follows an open flag Home keeps above its grid ([open], changed through
 * [onOpenChange]): the menu opens when the flag is set, and closing it (a choice, outside tap, Back)
 * clears the flag. A section rebuilt when the window crosses the tablet size reopens its menu from
 * the flag.
 */
@Composable
internal fun rememberHoistedMenuState(open: Boolean, onOpenChange: (Boolean) -> Unit, key: Any? = null): BoxloreMenuState {
    val menu = rememberBoxloreMenuState(key)
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)
    LaunchedEffect(menu, open) { if (open) menu.open() else menu.dismiss() }
    LaunchedEffect(menu) { snapshotFlow { menu.expanded }.drop(1).collect { currentOnOpenChange(it) } }
    return menu
}
