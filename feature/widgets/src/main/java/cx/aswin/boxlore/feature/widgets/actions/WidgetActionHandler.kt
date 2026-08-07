package cx.aswin.boxlore.feature.widgets.actions

import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetCoordinator
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetDependenciesHolder
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetSnapshotStore
import cx.aswin.boxlore.feature.widgets.WidgetControl
import cx.aswin.boxlore.feature.widgets.logic.WidgetOptimisticAction

object WidgetActionHandler {
    suspend fun handle(control: WidgetControl) {
        val deps =
            runCatching { NowPlayingWidgetDependenciesHolder.require() }
                .getOrNull()
                ?: return

        val store = NowPlayingWidgetSnapshotStore(deps.context)
        val current = store.read()
        if (current != null && control != WidgetControl.OPEN_APP) {
            val optimistic = WidgetOptimisticAction.apply(current, control)
            store.write(optimistic)
            NowPlayingWidgetCoordinator.requestRefresh(deps.context)
        }

        if (control != WidgetControl.OPEN_APP) {
            deps.playback.restoreBeforeAction()
        }
        when (control) {
            WidgetControl.TOGGLE -> deps.playback.togglePlayPause()
            WidgetControl.PREVIOUS -> deps.playback.previous()
            WidgetControl.NEXT -> deps.playback.next()
            WidgetControl.SKIP_BACK -> deps.playback.skipBackward()
            WidgetControl.SKIP_FORWARD -> deps.playback.skipForward()
            WidgetControl.OPEN_APP -> Unit
        }
    }
}
