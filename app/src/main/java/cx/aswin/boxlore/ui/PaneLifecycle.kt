package cx.aswin.boxlore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

private class PaneLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

/** Hidden retained destinations stop collecting and release Back without losing their route stack. */
@Composable
internal fun PaneLifecycle(visible: Boolean, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { PaneLifecycleOwner() }
    val currentVisible by rememberUpdatedState(visible)
    DisposableEffect(parent, owner) {
        val observer = LifecycleEventObserver { _, _ -> updatePaneLifecycle(owner, parent, currentVisible) }
        parent.lifecycle.addObserver(observer)
        updatePaneLifecycle(owner, parent, currentVisible)
        onDispose {
            parent.lifecycle.removeObserver(observer)
            owner.registry.currentState = Lifecycle.State.DESTROYED
        }
    }
    SideEffect { updatePaneLifecycle(owner, parent, visible) }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

private fun updatePaneLifecycle(owner: PaneLifecycleOwner, parent: LifecycleOwner, visible: Boolean) {
    val state = parent.lifecycle.currentState
    owner.registry.currentState = if (!visible && state.isAtLeast(Lifecycle.State.STARTED)) Lifecycle.State.CREATED else state
}
