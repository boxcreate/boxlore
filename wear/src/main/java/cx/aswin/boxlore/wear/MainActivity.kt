package cx.aswin.boxlore.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import cx.aswin.boxlore.wear.glance.GlanceRules
import cx.aswin.boxlore.wear.glance.ResumeTap
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.ui.WatchApp
import cx.aswin.boxlore.wear.ui.WatchRoutes
import cx.aswin.boxlore.wear.ui.theme.WatchTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** The screen a launch asked for, such as the player from a tile; cleared once shown. */
    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WatchApplication).container
        if (savedInstanceState == null) {
            open(intent)
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
        open(intent)
    }

    /** Opens the screen a launch asked for; a tap on Resume in the tile or on the watch face carries on with its episode. */
    private fun open(intent: Intent?) {
        val episodeId = intent?.getStringExtra(WatchRoutes.EXTRA_RESUME)?.takeIf { it.isNotEmpty() }
        if (episodeId == null) {
            WatchRoutes.fromLaunch(intent)?.let { requestedRoute = it }
            return
        }
        val container = (application as WatchApplication).container
        lifecycleScope.launch {
            // On a cold start the watch has not yet read what the phone last said. Deciding now would
            // start the episode here while the phone plays, with no choice of device and both playing.
            container.phoneKnown()
            requestedRoute = resume(episodeId)
        }
    }

    /** Carries on with [episodeId] and returns the screen to open for it. */
    private fun resume(episodeId: String): String {
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
