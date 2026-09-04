package cx.aswin.boxlore.feature.widgets

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Narrow playback port wired from `:app` — never construct [cx.aswin.boxlore.core.playback.PlaybackRepository] here. */
interface WidgetPlaybackSource {
    val state: StateFlow<WidgetPlaybackState>

    /** Called once before the coordinator collects playback state (cold widget / process start). */
    suspend fun restoreBeforeCollect()

    /** Called before handling transport actions when the controller may be disconnected. */
    suspend fun restoreBeforeAction()

    suspend fun togglePlayPause()

    suspend fun previous()

    suspend fun next()

    suspend fun skipForward()

    suspend fun skipBackward()
}

interface NowPlayingWidgetDependencies {
    val context: Context
    val scope: CoroutineScope
    val playback: WidgetPlaybackSource
}

object NowPlayingWidgetDependenciesHolder {
    @Volatile
    var instance: NowPlayingWidgetDependencies? = null
        internal set

    fun require(): NowPlayingWidgetDependencies = instance
        ?: error(
            "NowPlayingWidgetDependencies not installed. " +
                "Call configureNowPlayingWidget from Application after AppContainer is ready.",
        )
}

/** Installs dependencies and starts the widget coordinator. Call from `:app` only. */
fun configureNowPlayingWidget(dependencies: NowPlayingWidgetDependencies) {
    NowPlayingWidgetDependenciesHolder.instance = dependencies
    WidgetThemeSync.install(dependencies.context)
    NowPlayingWidgetCoordinator.start(dependencies)
}
