package cx.aswin.boxlore.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
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
 * For a list's first item: with the remote anywhere in it, the list goes back to its very top, so a
 * header or top card is never left cut off above the screen.
 */
@Composable
fun ListTop(state: LazyListState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    Box(modifier.onFocusChanged { if (it.hasFocus) scope.launch { state.animateScrollToItem(0) } }) {
        content()
    }
}

/**
 * A row of buttons the remote always enters on [main] (Play, Subscribe), whichever button sits
 * nearest the item it comes from. Moving along the row is unchanged.
 */
fun Modifier.entersOn(main: FocusRequester): Modifier = focusProperties { onEnter = { main.requestFocus() } }.focusGroup()
