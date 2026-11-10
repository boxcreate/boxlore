package cx.aswin.boxlore.feature.widgets.actions

import android.content.Context
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetCoordinator
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetDependencies
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetDependenciesHolder
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetSnapshot
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetSnapshotStore
import cx.aswin.boxlore.feature.widgets.WidgetControl
import cx.aswin.boxlore.feature.widgets.WidgetPlaybackSource
import cx.aswin.boxlore.feature.widgets.logic.NowPlayingWidgetMapper
import cx.aswin.boxlore.feature.widgets.logic.WidgetOptimisticAction

object WidgetActionHandler {
    suspend fun handle(control: WidgetControl) {
        val deps =
            runCatching { NowPlayingWidgetDependenciesHolder.require() }
                .getOrNull()
                ?: return

        val store = NowPlayingWidgetSnapshotStore(deps.context)
        val current = store.read()
        var wroteOptimistic = false
        if (current != null && control != WidgetControl.OPEN_APP) {
            val optimistic = WidgetOptimisticAction.apply(withLivePosition(current, deps), control)
            // Redrawn straight away from the expected result, so the widget never waits for the player.
            store.write(optimistic)
            NowPlayingWidgetCoordinator.requestRefresh(deps.context)
            wroteOptimistic = true
        }

        try {
            if (control != WidgetControl.OPEN_APP) {
                deps.playback.restoreBeforeAction()
            }
            perform(control, deps.playback)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (wroteOptimistic) {
                refreshFromAuthoritativePlayback(deps.context, store, deps)
            }
        }
    }

    private suspend fun perform(control: WidgetControl, playback: WidgetPlaybackSource) {
        when (control) {
            WidgetControl.TOGGLE -> playback.togglePlayPause()
            WidgetControl.PREVIOUS -> playback.previous()
            WidgetControl.NEXT -> playback.next()
            WidgetControl.SKIP_BACK -> playback.skipBackward()
            WidgetControl.SKIP_FORWARD -> playback.skipForward()
            WidgetControl.OPEN_APP -> return
        }
        // Replace the optimistic position with the player's real one, also while the app is in the background.
        playback.refreshPosition()
    }

    /**
     * The saved snapshot only moves when the widget repaints, so a seek is previewed from the position
     * the player last published instead.
     */
    private fun withLivePosition(
        snapshot: NowPlayingWidgetSnapshot,
        deps: NowPlayingWidgetDependencies,
    ): NowPlayingWidgetSnapshot {
        val live = deps.playback.state.value
        if (live.episodeId != snapshot.episodeId) return snapshot
        return snapshot.copy(positionMs = live.positionMs, durationMs = live.durationMs)
    }

    private fun refreshFromAuthoritativePlayback(
        context: Context,
        store: NowPlayingWidgetSnapshotStore,
        deps: NowPlayingWidgetDependencies,
    ) {
        val playback = deps.playback.state.value
        val previous = store.read()
        val cachedPath =
            previous
                ?.takeIf { it.episodeId == playback.episodeId && it.artworkUrl == playback.artworkUrl }
                ?.artworkCachePath
        store.write(
            NowPlayingWidgetMapper.fromPlayback(
                state = playback,
                artworkCachePath = cachedPath,
            ),
        )
        NowPlayingWidgetCoordinator.requestRefresh(context)
    }
}
