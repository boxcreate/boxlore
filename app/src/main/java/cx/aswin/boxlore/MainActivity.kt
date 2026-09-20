package cx.aswin.boxlore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.posthog.PostHog
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.surveys.NpsSurveyTriggers
import cx.aswin.boxlore.ui.BoxLoreAppRoot
import cx.aswin.boxlore.ui.CoilImageLoaderSetup
import cx.aswin.boxlore.updates.PlayAppUpdateHelper
import kotlinx.coroutines.launch

/**
 * Activity shell: splash, edge-to-edge, Play updates, NPS triggers, Coil install,
 * and [setContent] → [BoxLoreAppRoot] (theme + nav host + overlays).
 *
 * No repository construction — deps come from [BoxLoreApplication.container].
 */
class MainActivity : ComponentActivity() {
    private val intentState = mutableStateOf<android.content.Intent?>(null)
    private val warmStartIntent = mutableStateOf<android.content.Intent?>(null)

    private var expandPlayerTrigger by mutableLongStateOf(0L)

    /** Analytics: deduplicate cold vs warm starts (retained for parity). */
    @Suppress("unused")
    private var isFirstResumeAfterLaunch = true

    private val surveyPrefs by lazy {
        (application as BoxLoreApplication).userPreferencesRepository
    }
    private val engagementCoordinator by lazy {
        (application as BoxLoreApplication).engagementPromptCoordinator
    }

    @Volatile
    private var playbackRepositoryRef: PlaybackRepository? = null

    private fun isCurrentlyPlaying(): Boolean = playbackRepositoryRef?.playerState?.value?.isPlaying == true

    private val updateLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode != RESULT_OK) {
                android.util.Log.e(
                    "AppUpdate",
                    "Update flow failed or cancelled. Result code: ${result.resultCode}",
                )
            }
        }

    private val playAppUpdateHelper by lazy {
        PlayAppUpdateHelper(this, updateLauncher)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
        warmStartIntent.value = intent
        handlePlayerIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        val linkString = uri.toString()
        val authRepo = (application as BoxLoreApplication).container.authRepository
        if (authRepo.isSignInWithEmailLink(linkString)) {
            val prefs = cx.aswin.boxlore.core.prefs.BoxcastPrefs(this)
            val pendingEmail = prefs.getPendingAuthEmail()
            if (!pendingEmail.isNullOrBlank()) {
                lifecycleScope.launch {
                    val result = authRepo.signInWithEmailLink(pendingEmail, linkString)
                    if (result.isSuccess) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Signed in as ${result.getOrNull()?.email ?: "user"}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        android.util.Log.e("MainActivity", "Magic link sign-in failed", result.exceptionOrNull())
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            result.exceptionOrNull()?.localizedMessage ?: "Failed to sign in with link",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun handlePlayerIntent(intent: android.content.Intent) {
        val shouldOpenPlayer = intent.getBooleanExtra("EXTRA_OPEN_PLAYER", false)
        if (shouldOpenPlayer) {
            expandPlayerTrigger = System.currentTimeMillis()
            intent.removeExtra("EXTRA_OPEN_PLAYER")
        }
        if (intent.getBooleanExtra("from_push", false)) {
            intent.removeExtra("from_push")
            AnalyticsHelper.trackNotificationTapped(
                notificationType = intent.getStringExtra("notification_type") ?: "unknown",
                podcastId = intent.getStringExtra("podcast_id"),
                episodeId = intent.getStringExtra("episode_id"),
                targetRoute =
                intent.getStringExtra("target_route")
                    ?: intent.data?.toString(),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isFirstResumeAfterLaunch = false
        PostHog.register(
            "local_time_of_day",
            java.util.Calendar
                .getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY),
        )
        NpsSurveyTriggers.check(
            surveyPrefs = surveyPrefs,
            engagementCoordinator = engagementCoordinator,
            isCurrentlyPlaying = ::isCurrentlyPlaying,
            scope = lifecycleScope,
        )
        playAppUpdateHelper.resumeInProgressUpdate()
    }

    override fun onStart() {
        super.onStart()
        playbackRepositoryRef?.setUiForeground(true)
    }

    override fun onStop() {
        playbackRepositoryRef?.setUiForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        intentState.value = intent
        // Cold-start deep links / push routes must use the same handleDeepLink path as warm starts.
        if (intent?.data != null || !intent?.getStringExtra("target_route").isNullOrBlank()) {
            warmStartIntent.value = intent
        }
        handlePlayerIntent(intent)
        handleAuthIntent(intent)

        try {
            enableEdgeToEdge()
        } catch (e: NoClassDefFoundError) {
            android.util.Log.w(
                "MainActivity",
                "enableEdgeToEdge() failed due to missing framework class, skipping",
                e,
            )
        }

        AnalyticsHelper.trackFirstLaunchIfNecessary(this)
        handlePlayerIntent(intent)
        playAppUpdateHelper.checkForUpdates()
        CoilImageLoaderSetup.install(applicationContext)

        setContent {
            BoxLoreAppRoot(
                activity = this@MainActivity,
                application = application as BoxLoreApplication,
                expandPlayerTrigger = expandPlayerTrigger,
                intentState = intentState,
                warmStartIntent = warmStartIntent,
                onPlaybackRepositoryReady = {
                    playbackRepositoryRef = it
                    it.setUiForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                },
            )
        }
    }
}
