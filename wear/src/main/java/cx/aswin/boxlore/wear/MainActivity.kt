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
import cx.aswin.boxlore.wear.glance.ResumeRequest
import cx.aswin.boxlore.wear.glance.ResumeTap
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.ui.WatchApp
import cx.aswin.boxlore.wear.ui.WatchRoutes
import cx.aswin.boxlore.wear.ui.theme.WatchTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** The screen a launch asked for, such as the player from a tile; cleared once shown. */
    private var requestedRoute by mutableStateOf<String?>(null)

    /** The Resume tap that is still waiting for the phone's word, and the work doing the waiting. */
    private val resumeRequest = ResumeRequest()
    private var resuming: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WatchApplication).container
        if (savedInstanceState == null) {
            open(intent)
        } else {
            // The screen was rebuilt while a Resume tap was still waiting: carry on with it.
            savedInstanceState.getString(KEY_PENDING_RESUME)?.let(::resumeLater)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_RESUME, resumeRequest.pending)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    /** Opens the screen a launch asked for; a tap on Resume in the tile or on the watch face carries on with its episode. */
    private fun open(intent: Intent?) {
        // A newer launch wins over a Resume tap that is still waiting.
        resuming?.cancel()
        resumeRequest.drop()
        val episodeId = intent?.getStringExtra(WatchRoutes.EXTRA_RESUME)?.takeIf { it.isNotEmpty() }
        if (episodeId == null) {
            WatchRoutes.fromLaunch(intent)?.let { requestedRoute = it }
            return
        }
        resumeLater(episodeId)
    }

    private fun resumeLater(episodeId: String) {
        val container = (application as WatchApplication).container
        resumeRequest.ask(episodeId)
        resuming =
            lifecycleScope.launch {
                // On a cold start the watch has not yet read what the phone last said. Deciding now would
                // start the episode here while the phone plays, with no choice of device and both playing.
                container.phoneKnown()
                if (resumeRequest.take(episodeId)) requestedRoute = resume(episodeId)
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

    private companion object {
        const val KEY_PENDING_RESUME = "pending_resume"
    }
}
