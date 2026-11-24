package cx.aswin.boxlore.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * Shows the whole of [content] when the remote moves into it, not only the item it lands on, so a
 * row comes back with its title. Lists still scroll only as far as that takes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RevealOnFocus(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Box(modifier.bringIntoViewRequester(requester).onFocusChanged { if (it.hasFocus) scope.launch { requester.bringIntoView() } }) {
        content()
    }
}

/**
 * For a list's first item: whenever the remote is anywhere in it, the whole item shows from the
 * very top of the list, so a header or top card is never left cut off above the screen. It asks to
 * show the item and as much room again above it, which only the top of the list can give; being
 * one request, it cannot race the scroll that keeps the focused button itself in view. It asks on
 * the way in and again on every arrow key inside, so Up on the top card, with nothing above it to
 * move to, still brings the rest of the card into view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListTop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val reveal = { scope.launch { requester.bringIntoView(Rect(0f, -size.height.toFloat(), size.width.toFloat(), size.height.toFloat())) } }
    Box(
        modifier.bringIntoViewRequester(requester)
            .onSizeChanged { size = it }
            .onFocusChanged { if (it.hasFocus) reveal() }
            .onKeyEvent { event ->
                // Never handled here: the remote still moves as usual, and the card shows whole after it.
                if (event.type == KeyEventType.KeyDown && TopItemKeys.reveals(event.key)) reveal()
                false
            },
    ) {
        content()
    }
}

/**
 * A row of buttons the remote always enters on [main] (Play, Subscribe), whichever button sits
 * nearest the item it comes from. Moving along the row is unchanged.
 */
fun Modifier.entersOn(main: FocusRequester): Modifier = focusProperties { onEnter = { main.requestFocus() } }.focusGroup()

/** The arrow keys that keep a list's first item fully shown while the remote is in it. */
internal object TopItemKeys {
    fun reveals(key: Key): Boolean = key == Key.DirectionUp || key == Key.DirectionLeft || key == Key.DirectionRight
}
