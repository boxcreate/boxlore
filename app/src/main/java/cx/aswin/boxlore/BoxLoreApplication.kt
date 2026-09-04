package cx.aswin.boxlore

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.cast.Cast
import androidx.media3.common.util.UnstableApi
import androidx.work.Configuration
import com.google.android.gms.cast.SessionState
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionTransferCallback
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import cx.aswin.boxlore.core.catalog.EngagementPromptCoordinator
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.playback.synchronizeCastSession
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.LearningEventLog
import cx.aswin.boxlore.surveys.BoxcastPostHogSurveysDelegate
import cx.aswin.boxlore.widgets.HomeScreenWidgetsInstaller
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BoxLoreApplication :
    Application(),
    Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var castSessionGeneration = 0L

    private val castSessionListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                handleCastSessionEvent(CastSessionEvent.STARTING)
            }

            override fun onSessionStarted(session: CastSession, sessionId: String,) {
                handleCastSessionEvent(CastSessionEvent.STARTED)
            }

            override fun onSessionStartFailed(session: CastSession, error: Int,) {
                handleCastSessionEvent(CastSessionEvent.START_FAILED)
            }

            override fun onSessionEnding(session: CastSession) {
                handleCastSessionEvent(CastSessionEvent.ENDING)
            }

            override fun onSessionEnded(session: CastSession, error: Int,) {
                handleCastSessionEvent(CastSessionEvent.ENDED)
            }

            override fun onSessionResuming(session: CastSession, sessionId: String,) {
                handleCastSessionEvent(CastSessionEvent.RESUMING)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean,) {
                handleCastSessionEvent(CastSessionEvent.RESUMED)
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int,) {
                handleCastSessionEvent(CastSessionEvent.RESUME_FAILED)
            }

            override fun onSessionSuspended(session: CastSession, reason: Int,) {
                // Keep the Cast route while the framework's reconnection service reattaches.
                handleCastSessionEvent(CastSessionEvent.SUSPENDED)
            }
        }

    private val castSessionTransferCallback =
        object : SessionTransferCallback() {
            override fun onTransferring(transferType: Int) {
                handleCastSessionAction(
                    CastSessionTransferPolicy.action(
                        isRemoteToLocal = transferType == SessionTransferCallback.TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL,
                        outcome = CastTransferOutcome.TRANSFERRING,
                    ),
                )
            }

            override fun onTransferred(transferType: Int, sessionState: SessionState,) {
                handleCastSessionAction(
                    CastSessionTransferPolicy.action(
                        isRemoteToLocal = transferType == SessionTransferCallback.TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL,
                        outcome = CastTransferOutcome.TRANSFERRED,
                    ),
                )
            }

            override fun onTransferFailed(transferType: Int, transferFailedReason: Int,) {
                handleCastSessionAction(
                    CastSessionTransferPolicy.action(
                        isRemoteToLocal = transferType == SessionTransferCallback.TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL,
                        outcome = CastTransferOutcome.FAILED,
                    ),
                )
            }
        }

    lateinit var container: AppContainer
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    /** Shared orchestrator for NPS and Play review proactive prompts. */
    lateinit var engagementPromptCoordinator: EngagementPromptCoordinator
        private set

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(LegacyWorkerFactory())
                .build()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Load the manifest-backed boxlore receiver before playback or Cast UI
        // creates remote resources, avoiding a selector initialization race.
        Cast.getSingletonInstance(this).initialize()

        // Single prefs instance shared with AppContainer (theme fast-cache + engagement).
        userPreferencesRepository = UserPreferencesRepository(this)
        runBlocking(Dispatchers.IO) {
            userPreferencesRepository.hydrateMissingDataStoreFromFastCache()
        }
        container =
            AppContainer(
                context = this,
                apiBaseUrl = BuildConfig.BOXLORE_API_BASE_URL,
                publicKey = BuildConfig.BOXLORE_PUBLIC_KEY,
                sharedUserPreferences = userPreferencesRepository,
                applicationScope = applicationScope,
            )
        setupCastSessionTracking()
        SharedAppDependenciesHolder.instance = container
        DownloadsDependenciesHolder.instance = container
        HomeScreenWidgetsInstaller.install(
            context = this,
            scope = applicationScope,
            playbackRepository = container.playbackRepository,
            subscriptionRepository = container.subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
            adaptiveScorer = container.adaptiveCandidateScorer,
        )
        engagementPromptCoordinator = EngagementPromptCoordinator(userPreferencesRepository)
        // Eagerly touch the container ranking façade so create/install runs its no-op
        // fallback if Room initialization fails — same startup behavior as before, without
        // a second RankingFeedbackRepository client diverging from the container.
        container.rankingFeedbackRepository
        applicationScope.launch {
            container.smartDownloadManager.reconcileScheduleWithPreferences()
        }

        // Live learner signal log: on by default in debug; release stays off unless the
        // user explicitly opts in via the debug-screen toggle (persisted true).
        LearningEventLog.configure(
            cx.aswin.boxlore.core.prefs
                .BoxcastPrefs(this)
                .resolveLearnerLogEnabled(isDebugBuild = BuildConfig.DEBUG),
        )

        val config =
            PostHogAndroidConfig(
                apiKey = BuildConfig.POSTHOG_API_KEY,
                host = BuildConfig.POSTHOG_HOST,
            ).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = false
                captureDeepLinks = false
                debug = BuildConfig.DEBUG
                // PostHog Surveys with a Material3 1.5-compatible delegate (the published
                // posthog-android-surveys-compose:0.1.0 module crashes against our M3 pin).
                surveys = true
                surveysConfig.surveysDelegate =
                    BoxcastPostHogSurveysDelegate(
                        context = this@BoxLoreApplication,
                        userPrefs = userPreferencesRepository,
                        engagementCoordinator = engagementPromptCoordinator,
                    )
            }
        PostHogAndroid.setup(this, config)

        // Non-fatal error sink: Crashlytics when available, Logcat fallback inside ErrorReporter.
        cx.aswin.boxlore.core.analytics.ErrorReporter.install { throwable, message ->
            try {
                val crashlytics =
                    com.google.firebase.crashlytics.FirebaseCrashlytics
                        .getInstance()
                if (message != null) {
                    crashlytics.log(message)
                }
                crashlytics.recordException(throwable)
            } catch (_: Exception) {
                android.util.Log.e("ErrorReporter", message ?: throwable.message, throwable)
            }
        }

        // Tag internal/test users so they can be filtered in PostHog settings
        if (BuildConfig.DEBUG) {
            PostHog.register("is_internal", true)
            PostHog.register("app_environment", "debug")
        } else {
            PostHog.register("is_internal", false)
            PostHog.register("app_environment", "production")
        }
        reportAdaptiveRankingStatus()

        setupAppCheck()

        // Setup active connectivity listener for offline tracking
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val initialNetwork = connectivityManager.activeNetwork
            val initialCapabilities = connectivityManager.getNetworkCapabilities(initialNetwork)
            var hasInternet = initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        hasInternet = true
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        val activeNetwork = connectivityManager.activeNetwork
                        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                        if (!isConnected) {
                            if (hasInternet) {
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                                    .trackOfflineModeEntered()
                            }
                            hasInternet = false
                        }
                    }
                },
            )
        } catch (e: Exception) {
            cx.aswin.boxlore.core.analytics.ErrorReporter.report(
                e,
                "Failed to register connectivity observer",
            )
        }
    }

    private fun setupCastSessionTracking() {
        runCatching {
            val castContext = CastContext.getSharedInstance(this)
            val sessionManager = castContext.sessionManager
            sessionManager.addSessionManagerListener(castSessionListener, CastSession::class.java)
            castContext.addSessionTransferCallback(castSessionTransferCallback)
            if (sessionManager.currentCastSession?.isConnected == true) {
                syncCastSession(isActive = true)
            } else {
                // Cast restores asynchronously after process recreation. Keep the route
                // undecided until the resume callbacks arrive instead of forcing it local.
                syncCastSession(isActive = null)
                deferCastSessionClear()
            }
        }.onFailure { exception ->
            android.util.Log.w("BoxLoreApplication", "Unable to observe Cast sessions", exception)
        }
    }

    private fun syncCastSession(isActive: Boolean?) {
        castSessionGeneration += 1
        if (::container.isInitialized) {
            container.playbackRepository.synchronizeCastSession(isActive)
        }
    }

    private fun handleCastSessionEvent(event: CastSessionEvent) {
        handleCastSessionAction(CastSessionLifecyclePolicy.action(event))
    }

    private fun handleCastSessionAction(action: CastSessionAction) {
        when (action) {
            CastSessionAction.KEEP_ACTIVE -> syncCastSession(isActive = true)
            CastSessionAction.CLEAR_NOW -> syncCastSession(isActive = false)
            CastSessionAction.DEFER_CLEAR -> deferCastSessionClear()
            CastSessionAction.NONE -> Unit
        }
    }

    private fun deferCastSessionClear() {
        val generation = castSessionGeneration + 1
        castSessionGeneration = generation
        applicationScope.launch {
            delay(CAST_SESSION_RECHECK_DELAY_MS)
            withContext(Dispatchers.Main.immediate) {
                if (generation != castSessionGeneration) return@withContext
                val isConnected =
                    runCatching {
                        CastContext
                            .getSharedInstance(this@BoxLoreApplication)
                            .sessionManager
                            .currentCastSession
                            ?.isConnected == true
                    }.getOrDefault(false)
                syncCastSession(isActive = isConnected)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reportAdaptiveRankingStatus() {
        applicationScope.launch {
            try {
                val statuses =
                    container.adaptiveRankingRepository
                        .aggregateTelemetry()
                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                    .trackAdaptiveRankingStatus(statuses)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                // Aggregate telemetry is optional and must never destabilize app startup.
                android.util.Log.w(
                    "BoxLoreApplication",
                    "Failed to report adaptive ranking status",
                    error,
                )
            }
        }
    }

    /**
     * Firebase App Check attests that requests come from the genuine app.
     * Debug builds use the debug provider (token must be registered in the
     * Firebase console); release builds attest via Play Integrity. Tokens are
     * attached to API calls as X-Firebase-AppCheck. Everything fails open:
     * requests still go out without the header if attestation is unavailable,
     * since the Worker is in log-only mode.
     */
    private fun setupAppCheck() {
        try {
            // Expose the build to the network layer so requests carry
            // X-App-Version and the proxy can slice App Check adoption by build.
            NetworkModule.appVersion = BuildConfig.VERSION_NAME
            val provider = if (BuildConfig.DEBUG) "debug" else "play_integrity"
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            } else {
                appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
            // Keep a valid token in the SDK's persistent cache at all times and
            // refresh it in the background before expiry, so the interceptor gets
            // an instant cache read instead of a live (and sometimes failing)
            // fetch. With the 24h token TTL this is ~1 mint/user/day.
            appCheck.setTokenAutoRefreshEnabled(true)
            // Pre-warm: start the token exchange at launch so it's cached before
            // the first API request, closing the cold-start gap. The result is
            // reported once per launch to PostHog for adoption/health tracking.
            appCheck
                .getAppCheckToken(false)
                .addOnSuccessListener {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .trackAppCheckStatus(true, provider)
                }.addOnFailureListener { e ->
                    android.util.Log.w("BoxCastApp", "App Check pre-warm failed: ${e.message}")
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .trackAppCheckStatus(false, provider)
                }
            NetworkModule.appCheckTokenProvider = {
                try {
                    // Called from OkHttp's background threads; returns the cached
                    // token instantly unless it needs a refresh
                    val task = FirebaseAppCheck.getInstance().getAppCheckToken(false)
                    Tasks.await(task, 5, TimeUnit.SECONDS).token
                } catch (e: Exception) {
                    android.util.Log.w("BoxCastApp", "App Check token unavailable: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxCastApp", "App Check setup failed", e)
        }
    }
}

internal enum class CastSessionEvent {
    STARTING,
    STARTED,
    START_FAILED,
    ENDING,
    ENDED,
    RESUMING,
    RESUMED,
    RESUME_FAILED,
    SUSPENDED,
}

internal enum class CastSessionAction {
    KEEP_ACTIVE,
    CLEAR_NOW,
    DEFER_CLEAR,
    NONE,
}

internal object CastSessionLifecyclePolicy {
    fun action(event: CastSessionEvent): CastSessionAction = when (event) {
        CastSessionEvent.STARTING,
        CastSessionEvent.STARTED,
        CastSessionEvent.RESUMING,
        CastSessionEvent.RESUMED,
        CastSessionEvent.SUSPENDED,
        -> CastSessionAction.KEEP_ACTIVE

        CastSessionEvent.START_FAILED,
        CastSessionEvent.ENDED,
        CastSessionEvent.RESUME_FAILED,
        -> CastSessionAction.DEFER_CLEAR

        CastSessionEvent.ENDING -> CastSessionAction.NONE
    }
}

internal enum class CastTransferOutcome {
    TRANSFERRING,
    TRANSFERRED,
    FAILED,
}

internal object CastSessionTransferPolicy {
    fun action(isRemoteToLocal: Boolean, outcome: CastTransferOutcome,): CastSessionAction {
        if (!isRemoteToLocal) return CastSessionAction.NONE
        return when (outcome) {
            CastTransferOutcome.TRANSFERRING -> CastSessionAction.KEEP_ACTIVE
            CastTransferOutcome.TRANSFERRED -> CastSessionAction.CLEAR_NOW
            CastTransferOutcome.FAILED -> CastSessionAction.DEFER_CLEAR
        }
    }
}

private const val CAST_SESSION_RECHECK_DELAY_MS = 15_000L
