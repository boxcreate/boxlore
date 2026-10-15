package cx.aswin.boxlore.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor

internal data class PlaybackKeyboardActions(val toggle: () -> Unit, val backward: () -> Unit, val forward: () -> Unit)

/** Transport keys apply only after focused controls and text editing decline them; arrows seek only while the shell itself holds focus. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TabletPlaybackKeyboard(enabled: Boolean, actions: PlaybackKeyboardActions, content: @Composable (Modifier) -> Unit) {
    var editingSessions by remember { mutableIntStateOf(0) }
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            editingSessions++
            try {
                nextHandler.startInputMethod(request)
            } finally {
                editingSessions--
            }
        }
    }
    val currentActions by rememberUpdatedState(actions)
    val focusRequester = remember { FocusRequester() }
    val state = remember { PlaybackKeyboardState() }
    val modifier = Modifier.onKeyEvent { event ->
        playbackKeyEvent(event, enabled && editingSessions == 0, currentActions, state)
    }.onFocusChanged {
        state.shellFocused = it.isFocused
        if (!it.hasFocus) state.spaceHeld = false
    }.focusRequester(focusRequester).focusable(enabled)
    LaunchedEffect(enabled) {
        state.spaceHeld = false
        if (enabled && editingSessions == 0) focusRequester.requestFocus()
    }
    // Stable wrapper across resize: replacing it would restart focused text-input sessions.
    InterceptPlatformTextInput(interceptor) { content(modifier) }
}

private class PlaybackKeyboardState(var spaceHeld: Boolean = false, var shellFocused: Boolean = false)

private fun playbackKeyEvent(event: KeyEvent, enabled: Boolean, actions: PlaybackKeyboardActions, state: PlaybackKeyboardState): Boolean {
    val modified = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
    if (!enabled || modified) {
        if (event.type == KeyEventType.KeyUp && event.key == Key.Spacebar) state.spaceHeld = false
        return false
    }
    return when (event.key) {
        Key.Spacebar -> {
            if (event.type == KeyEventType.KeyDown && !state.spaceHeld) actions.toggle()
            state.spaceHeld = event.type == KeyEventType.KeyDown
            true
        }
        // A focused descendant keeps arrows for focus movement, tabs and sliders.
        Key.DirectionLeft, Key.DirectionRight -> {
            if (!state.shellFocused) return false
            if (event.type == KeyEventType.KeyDown) seekWithKeyboard(event.key, actions)
            true
        }
        else -> false
    }
}

private fun seekWithKeyboard(key: Key, actions: PlaybackKeyboardActions) {
    if (key == Key.DirectionLeft) actions.backward() else actions.forward()
}
