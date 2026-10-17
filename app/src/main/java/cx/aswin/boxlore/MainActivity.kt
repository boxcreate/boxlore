package cx.aswin.boxlore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.posthog.PostHog
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.surveys.NpsSurveyTriggers
import cx.aswin.boxlore.ui.BoxLoreAppRoot
import cx.aswin.boxlore.updates.PlayAppUpdateHelper

/**
 * Activity shell: splash, edge-to-edge, Play updates, NPS triggers,
 * and [setContent] → [BoxLoreAppRoot] (theme + nav host + overlays).
 *
 * No repository construction — deps come from [BoxLoreApplication.container].
 */
class MainActivity : ComponentActivity() {
    internal val windowLayoutState = mutableStateOf(cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout.Unspecified)
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
    private val appUpdates get() = (application as BoxLoreApplication).container.appUpdates

    @Volatile private var consumedAuthLink: String? = null
    private var pendingCrossDeviceAuthLink by mutableStateOf<String?>(null)
    private val tabletAlphaPrefs by lazy { cx.aswin.boxlore.core.prefs.BoxcastPrefs(this) }
    internal var tabletAlphaNoticePending by mutableStateOf(false)
        private set
    private var resumePromptsAfterTabletNotice = false

    private fun restoreTabletAlphaNotice(savedInstanceState: Bundle?) {
        val bounds = windowManager.currentWindowMetrics.bounds
        val display = windowManager.maximumWindowMetrics.bounds
        val density = resources.displayMetrics.density
        tabletAlphaNoticePending = cx.aswin.boxlore.ui.shouldShowTabletAlphaNotice(
            cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout(bounds.width() / density, bounds.height() / density),
            minOf(display.width(), display.height()) / density,
            tabletAlphaPrefs::hasAcknowledgedTabletAlphaNotice,
            savedInstanceState?.takeIf { it.containsKey(KEY_TABLET_ALPHA_NOTICE_PENDING) }?.getBoolean(KEY_TABLET_ALPHA_NOTICE_PENDING),
        )
    }

    private fun acknowledgeTabletAlphaNotice() {
        tabletAlphaPrefs.acknowledgeTabletAlphaNotice()
        tabletAlphaNoticePending = false
        resumePromptsAfterTabletNotice = true
    }

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
        val emailLinkSignIn = (application as BoxLoreApplication).container.emailLinkSignIn
        if (emailLinkSignIn.isSignInLink(linkString)) {
            // Nullify intent data immediately so the one-time link is not routed as a podcast deep link.
            intent.data = null
            setIntent(intent)
            if (consumedAuthLink == linkString || lastConsumedAuthLink == linkString) {
                return
            }

            // The Account page shows the sign-in and the first sync instead of the app changing silently.
            emailLinkSignIn.requestAccountPage()
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
        pendingCrossDeviceAuthLink = null
        (application as BoxLoreApplication).container.emailLinkSignIn.signIn(email, linkString) {
            consumedAuthLink = linkString
            lastConsumedAuthLink = linkString
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
        val hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        cx.aswin.boxlore.core.analytics.PostHogStartup.runWhenReady {
            PostHog.register("local_time_of_day", hourOfDay)
        }
        if (!tabletAlphaNoticePending) resumeForegroundPrompts()
    }

    private fun resumeForegroundPrompts() {
        NpsSurveyTriggers.check(
            surveyPrefs = surveyPrefs,
            engagementCoordinator = engagementCoordinator,
            isCurrentlyPlaying = ::isCurrentlyPlaying,
            scope = lifecycleScope,
        )
        appUpdates.foreground()
        if (appUpdates.usesPlay) playAppUpdateHelper.resumeInProgressUpdate()
    }

    override fun onStart() {
        super.onStart()
        playbackRepositoryRef?.setUiForeground(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            (application as BoxLoreApplication).startupWorkGate.onUiStopped()
        }
        playbackRepositoryRef?.setUiForeground(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CONSUMED_AUTH_LINK, consumedAuthLink)
        outState.putString(KEY_PENDING_CROSS_DEVICE_AUTH_LINK, pendingCrossDeviceAuthLink)
        outState.putBoolean(KEY_TABLET_ALPHA_NOTICE_PENDING, tabletAlphaNoticePending)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as BoxLoreApplication).startupWorkGate.onUiCreated()
        installSplashScreen().holdBriefly(coldStart = savedInstanceState == null)
        super.onCreate(savedInstanceState)
        restoreTabletAlphaNotice(savedInstanceState)
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

        setContent {
            val windowLayout = cx.aswin.boxlore.ui.rememberBoxloreWindowLayout()
            androidx.compose.runtime.SideEffect { windowLayoutState.value = windowLayout }
            LaunchedEffect(tabletAlphaNoticePending) {
                withFrameNanos {}
                withFrameNanos {}
                if (tabletAlphaNoticePending) {
                    (application as BoxLoreApplication).startupWorkGate.markReady()
                } else {
                    appUpdates.foreground()
                    if (resumePromptsAfterTabletNotice && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        resumePromptsAfterTabletNotice = false
                        resumeForegroundPrompts()
                    }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                cx.aswin.boxlore.updates.LocalPlayUpdateAction provides { playAppUpdateHelper.startUpdate() },
                cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout provides windowLayout,
            ) {
            cx.aswin.boxlore.ui.TabletAlphaStartupGate(
                pending = tabletAlphaNoticePending,
                onAcknowledge = ::acknowledgeTabletAlphaNotice,
                noticeTheme = { notice ->
                    val preferences = (application as BoxLoreApplication).userPreferencesRepository
                    cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme(
                        darkTheme = cx.aswin.boxlore.core.designsystem.theme.darkThemeFromConfig(this@MainActivity, preferences.cachedThemeConfig),
                        dynamicColor = preferences.cachedUseDynamicColor,
                        themeBrand = preferences.cachedThemeBrand,
                        surfaceStyle = preferences.cachedSurfaceStyle,
                        fontRoundness = cx.aswin.boxlore.core.designsystem.theme.FontRoundness.axisValue(preferences.cachedFontRoundness),
                        content = notice,
                    )
                },
            ) {
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
        }
    }

    companion object {
        private const val KEY_CONSUMED_AUTH_LINK = "cx.aswin.boxlore.consumed_auth_link"
        private const val KEY_PENDING_CROSS_DEVICE_AUTH_LINK = "cx.aswin.boxlore.pending_cross_device_auth_link"
        private const val KEY_TABLET_ALPHA_NOTICE_PENDING = "cx.aswin.boxlore.tablet_alpha_notice_pending"

        @Volatile private var lastConsumedAuthLink: String? = null
    }
}

/** Keeps the splash up for [SplashHoldPolicy.MIN_VISIBLE_MILLIS] on a cold launch, then fades it out. */
private fun androidx.core.splashscreen.SplashScreen.holdBriefly(coldStart: Boolean) {
    val createdAt = android.os.SystemClock.uptimeMillis()
    setKeepOnScreenCondition {
        SplashHoldPolicy.keepOnScreen(createdAt, android.os.SystemClock.uptimeMillis(), coldStart)
    }
    setOnExitAnimationListener { provider ->
        provider.iconView.animate()
            .scaleX(SplashHoldPolicy.EXIT_ICON_SCALE)
            .scaleY(SplashHoldPolicy.EXIT_ICON_SCALE)
            .setDuration(SplashHoldPolicy.EXIT_FADE_MILLIS)
            .start()
        provider.view.animate()
            .alpha(0f)
            .setDuration(SplashHoldPolicy.EXIT_FADE_MILLIS)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { provider.remove() }
            .start()
    }
}
