package cx.aswin.boxlore.updates

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.components.LocalUpdateAvailableAction
import kotlinx.coroutines.launch

val LocalPlayUpdateAction = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun AppUpdatesHost(updates: AppUpdates, activity: Activity, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val check by updates.checker.state.collectAsStateWithLifecycle()
    val updateAction: (() -> Unit)? = if (check.updateAvailable) ({ updates.open() }) else null
    CompositionLocalProvider(LocalUpdateAvailableAction provides updateAction) {
        AppUpdatesPanel(
            updates = updates,
            onInstall = { scope.launch { updates.install(activity) } },
            onPlayUpdate = LocalPlayUpdateAction.current,
        )
        content()
    }
}
