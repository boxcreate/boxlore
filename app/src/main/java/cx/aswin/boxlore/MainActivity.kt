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

    private var consumedAuthLink: String? = null
    private var inFlightAuthLink: String? = null
    private var pendingCrossDeviceAuthLink by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayerIntent(intent)
        handleAuthIntent(intent)
        intentState.value = intent
        if (intent.data != null || !intent.getStringExtra("target_route").isNullOrBlank()) {
            warmStartIntent.value = intent
        }
    }

    private fun handleAuthIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        val linkString = uri.toString()
        val authRepo = (application as BoxLoreApplication).container.authRepository
        if (authRepo.isSignInWithEmailLink(linkString)) {
            // Nullify intent data immediately so the one-time link is not routed as a podcast deep link.
            intent.data = null
            setIntent(intent)
            if (consumedAuthLink == linkString || lastConsumedAuthLink == linkString || inFlightAuthLink == linkString) {
                return
            }

            val prefs = cx.aswin.boxlore.core.prefs.BoxcastPrefs(this)
            val pendingEmail = prefs.getPendingAuthEmail()
            if (!pendingEmail.isNullOrBlank()) {
                completeSignInWithEmailLink(pendingEmail, linkString)
            } else {
                pendingCrossDeviceAuthLink = linkString
            }
        }
    }

    private fun completeSignInWithEmailLink(email: String, linkString: String) {
        inFlightAuthLink = linkString
        pendingCrossDeviceAuthLink = null
        val authRepo = (application as BoxLoreApplication).container.authRepository
        lifecycleScope.launch {
            val result = authRepo.signInWithEmailLink(email, linkString)
            inFlightAuthLink = null
            if (result.isSuccess) {
                consumedAuthLink = linkString
                lastConsumedAuthLink = linkString
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Signed in as ${result.getOrNull()?.email ?: "user"}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                android.util.Log.e("MainActivity", "Magic link sign-in failed", result.exceptionOrNull())
                val prefs = cx.aswin.boxlore.core.prefs.BoxcastPrefs(this@MainActivity)
                if (prefs.getPendingAuthEmail().isNullOrBlank()) {
                    pendingCrossDeviceAuthLink = linkString
                }
                android.widget.Toast.makeText(
                    this@MainActivity,
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to sign in with link",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CONSUMED_AUTH_LINK, consumedAuthLink)
        outState.putString(KEY_PENDING_CROSS_DEVICE_AUTH_LINK, pendingCrossDeviceAuthLink)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        consumedAuthLink = savedInstanceState?.getString(KEY_CONSUMED_AUTH_LINK) ?: consumedAuthLink
        pendingCrossDeviceAuthLink = savedInstanceState?.getString(KEY_PENDING_CROSS_DEVICE_AUTH_LINK)
        handlePlayerIntent(intent)
        handleAuthIntent(intent)
        intentState.value = intent

        // Cold-start deep links / push routes must use the same handleDeepLink path as warm starts.
        if (intent?.data != null || !intent?.getStringExtra("target_route").isNullOrBlank()) {
            warmStartIntent.value = intent
        }

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

            pendingCrossDeviceAuthLink?.let { linkString ->
                cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme {
                    cx.aswin.boxlore.ui.CrossDeviceEmailDialog(
                        onDismiss = { pendingCrossDeviceAuthLink = null },
                        onConfirm = { email ->
                            completeSignInWithEmailLink(email, linkString)
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val KEY_CONSUMED_AUTH_LINK = "cx.aswin.boxlore.consumed_auth_link"
        private const val KEY_PENDING_CROSS_DEVICE_AUTH_LINK = "cx.aswin.boxlore.pending_cross_device_auth_link"
        private var lastConsumedAuthLink: String? = null
    }
}
