package cx.aswin.boxlore.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.wear.glance.GlanceRules
import cx.aswin.boxlore.wear.glance.ResumeTap
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.ui.WatchApp
import cx.aswin.boxlore.wear.ui.WatchRoutes
import cx.aswin.boxlore.wear.ui.theme.WatchTheme

class MainActivity : ComponentActivity() {
    /** The screen a launch asked for, such as the player from a tile; cleared once shown. */
    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WatchApplication).container
        if (savedInstanceState == null) {
            requestedRoute = resumeIfAsked(intent) ?: WatchRoutes.fromLaunch(intent)
        }
        setContent {
            WatchTheme {
                WatchApp(
                    container = container,
                    requestedRoute = requestedRoute,
                    onRouteShown = { requestedRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (resumeIfAsked(intent) ?: WatchRoutes.fromLaunch(intent))?.let { requestedRoute = it }
    }

    /**
     * A tap on Resume in the tile or on the watch face carries on with the episode it named.
     * Returns the screen to open, or null when the launch did not ask to resume.
     */
    private fun resumeIfAsked(intent: Intent?): String? {
        val episodeId = intent?.getStringExtra(WatchRoutes.EXTRA_RESUME)?.takeIf { it.isNotEmpty() } ?: return null
        val playback = (application as WatchApplication).container.playback
        val playingHere = playback.nowPlaying.value?.takeIf { it.isPlaying && it.place == PlaybackPlace.Watch }?.episode?.id
        return when (GlanceRules.onResume(playingHere, episodeId, playback.phoneIsPlaying)) {
            ResumeTap.ShowPlayer -> WatchRoutes.PLAYER
            ResumeTap.AskWhere -> WatchRoutes.playWhere(episodeId)
            ResumeTap.PlayHere -> {
                playback.play(episodeId)
                WatchRoutes.PLAYER
            }
        }
    }
}
