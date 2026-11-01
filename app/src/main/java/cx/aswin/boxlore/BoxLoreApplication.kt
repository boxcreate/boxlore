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
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import cx.aswin.boxlore.analytics.PostHogWiring
import cx.aswin.boxlore.core.catalog.EngagementPromptCoordinator
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import cx.aswin.boxlore.core.network.FirebaseAuthAuthenticator
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.LearningEventLog
import cx.aswin.boxlore.surveys.BoxcastPostHogSurveysDelegate
import cx.aswin.boxlore.sync.CloudSyncWorker
import cx.aswin.boxlore.ui.StartupWorkGate
import cx.aswin.boxlore.widgets.HomeScreenWidgetsInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BoxLoreApplication :
    Application(),
    Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val startupWorkGate = StartupWorkGate()

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

    /** This install's API session and its Turnstile step-up; null only without a valid API base URL. */
    internal var apiSessions: cx.aswin.boxlore.session.ApiSessionSetup? = null
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
        // Before anything can make an API request: start the shared session exchange behind the splash.
        apiSessions = cx.aswin.boxlore.session.ApiSessionSetup.install(this, applicationScope)
        cx.aswin.boxlore.ui.CoilImageLoaderSetup.install(this)

        // Load the manifest-backed boxlore receiver before playback or Cast UI
        // creates remote resources, avoiding a selector initialization race.
        Cast.getSingletonInstance(this).initialize()

        // Single prefs instance shared with AppContainer (theme fast-cache + engagement).
        userPreferencesRepository = UserPreferencesRepository(this)
        applicationScope.launch {
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
        apiSessions?.observe(container.connectivityObserver.isOnlineFlow, applicationScope)
        setupCastSessionTracking()
        SharedAppDependenciesHolder.instance = container
        DownloadsDependenciesHolder.instance = container
        // recipe_id beside episode_id on analytics events, looked up off the main thread (own-IDs P4 PR 18).
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.installEpisodeRecipeIdLookup(container.episodeRecipeIdLookup)
        // Episode share links name an ID every build can open, registered first (own-IDs P4 PR 13).
        cx.aswin.boxlore.core.designsystem.share.ShareManager.episodeShareTargets =
            cx.aswin.boxlore.core.model.EpisodeShareTargetSource { container.episodeSharePreparer.targetFor(it) }
        NetworkModule.authenticator =
            FirebaseAuthAuthenticator(container.authRepository)
        cx.aswin.boxlore.lifecycle.AutoDownloadLifecycle(this, container, applicationScope).start()
        HomeScreenWidgetsInstaller.install(
            context = this,
            scope = applicationScope,
            playbackRepository = { container.playbackRepository },
            subscriptionRepository = { container.subscriptionRepository },
            userPreferencesRepository = userPreferencesRepository,
            adaptiveScorer = { container.adaptiveCandidateScorer },
        )
        engagementPromptCoordinator = EngagementPromptCoordinator(userPreferencesRepository)
        scheduleOptionalStartup()

        // Live learner signal log: on by default in debug; release stays off unless the
        // user explicitly opts in via the debug-screen toggle (persisted true).
        LearningEventLog.configure(
            cx.aswin.boxlore.core.prefs
                .BoxcastPrefs(this)
                .resolveLearnerLogEnabled(isDebugBuild = BuildConfig.DEBUG),
        )

        startPostHog()

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

        reportAdaptiveRankingStatus()

        // After queued launch transactions, distinguish a worker/widget process from
        // an Activity launch. UI launches release the gate from committed content.
        android.os.Looper.myQueue().addIdleHandler {
            startupWorkGate.onMainQueueIdle()
            false
        }

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

    private fun scheduleOptionalStartup() {
        applicationScope.launch {
            startupWorkGate.runWhenReady {
                // Before sync starts, so repaired history rows upload with their real covers.
                repairPlaybackArtworkLinks()
                val coordinator = container.cloudSyncTriggerCoordinator
                withContext(Dispatchers.Main.immediate) { coordinator.start() }
                CloudSyncWorker.schedulePeriodicSync(this@BoxLoreApplication)
            }
        }
        applicationScope.launch {
            startupWorkGate.runWhenReady {
                container.smartDownloadManager.reconcileScheduleWithPreferences()
            }
        }
        applicationScope.launch {
            startupWorkGate.runWhenReady { scheduleUpgradeRepair() }
        }
    }

    /**
     * Enqueues the one-time repair of saved feed addresses on every launch while it has work left,
     * headless launches included; it reads stored state, so a restore starts it again.
     * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
     * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
     */
    private suspend fun scheduleUpgradeRepair() {
        runCatching {
            cx.aswin.boxlore.sync.UpgradeRepairWorker.scheduleIfNeeded(this, container.upgradeAddressRepair)
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.w("BoxLoreApplication", "Feed address repair scheduling failed", error)
        }
    }

    /**
     * Older builds saved the player's Android Auto artwork links into history and the queue; they
     * die with their covers and left Home on the placeholder. Each launch swaps any left for real
     * covers; once none remain it changes nothing.
     */
    private suspend fun repairPlaybackArtworkLinks() {
        runCatching {
            val database = container.database
            val history = database.listeningHistoryDao().repairPlaybackArtworkLinks()
            val queue = database.queueDao().repairPlaybackArtworkLinksForSync()
            if (history + queue > 0) android.util.Log.i("BoxLoreApplication", "Repaired player-only artwork links: history=$history queue=$queue")
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.w("BoxLoreApplication", "Artwork link repair failed", error)
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
            // Never constructs playback here; the state is applied when playback is created.
            container.synchronizeCastSession(isActive)
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

    /** PostHog setup: event levels first, then the SDK off the main thread. */
    private fun startPostHog() {
        // Event levels: this install's sample bucket and the last remote switch, before any capture.
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.initPolicy(this, BuildConfig.VERSION_NAME)
        val environment = PostHogWiring.environment(debug = BuildConfig.DEBUG, isolatedTests = BuildConfig.BOXLORE_ISOLATED_TESTS)
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
                onFeatureFlags =
                    PostHogWiring.onFlagsLoaded(
                        // LEGACY(own-ids): staged feed-first rollout; remove at S7 with rememberRolloutAnswers.
                        { rememberRolloutAnswers() },
                        { cx.aswin.boxlore.core.analytics.AnalyticsHelper.refreshAnalyticsPolicy() },
                    )
                // Drops events this install doesn't send and tags every event, SDK lifecycle ones included.
                addBeforeSend(cx.aswin.boxlore.core.analytics.AnalyticsBeforeSend(environment))
            }
        // Off the main thread: setup reads package info over binder (startup ANR). Calls made
        // before it finishes are queued by PostHogStartup and replayed in order.
        cx.aswin.boxlore.core.analytics.PostHogStartup.start {
            PostHogAndroid.setup(this, config)
            // Registered before queued calls replay so early events keep the internal tag.
            environment.forEach { (key, value) -> PostHog.register(key, value) }
        }
        // A listen the last run left unfinished because Android ended the app (queued until setup ends).
        cx.aswin.boxlore.core.playback.ListenInterruptions
            .reportLeftover(this)
    }

    /**
     * Saves each loaded `feed_first` and v2 kill-switch answer for the next process; this process
     * keeps the `feed_first` value its first read fixed. PostHog calls this after the setup
     * preload, `identify` and `reset` (not after explicit `reloadFeatureFlags`). PostHog is set up
     * whenever it calls this, so the flags are read directly: `PostHogStartup.isReady` can still be
     * false here while setup returns. Added in P4 (own episode IDs, plan section 11, Rollback).
     *
     * LEGACY(own-ids): staged feed-first rollout. Remove at sunset step S7 (MIGRATION.md §6) once
     * `feed_first` has been at 100% for 30 days.
     */
    private fun rememberRolloutAnswers() {
        container.rolloutFlagMemory.onFlagsLoaded { flag -> PostHog.getFeatureFlag(flag, null, false) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reportAdaptiveRankingStatus() {
        applicationScope.launch {
            startupWorkGate.awaitReady()
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
