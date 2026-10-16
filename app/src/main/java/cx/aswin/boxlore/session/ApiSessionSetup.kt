package cx.aswin.boxlore.session

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.appcheck.FirebaseAppCheck
import com.posthog.PostHog
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.session.AndroidKeystoreDeviceKey
import cx.aswin.boxlore.core.network.session.ApiSessionManager
import cx.aswin.boxlore.core.network.session.DataStoreApiSessionStore
import cx.aswin.boxlore.core.network.session.OkHttpSessionExchanger
import cx.aswin.boxlore.installBuildAppCheckProvider
import cx.aswin.boxlore.session.turnstile.TurnstileStepUpController
import cx.aswin.boxlore.util.isInstalledFromPlayStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Puts this build on API sessions. Called first thing in `Application.onCreate`, so the shared
 * exchange starts behind the splash and no request goes out before the manager exists.
 *
 * App Check is installed here but fetched only inside `/auth/session`; no API request waits for it,
 * and its background auto-refresh is off because nothing else in the app needs a fresh token.
 */
internal class ApiSessionSetup private constructor(
    val manager: ApiSessionManager,
    val turnstile: TurnstileStepUpController,
    val apiBaseUrl: String,
) {
    /** Background App Check retries run only in the foreground with a network. */
    fun observe(online: StateFlow<Boolean>, scope: CoroutineScope) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        manager.onForegroundChanged(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = manager.onForegroundChanged(true)

                override fun onStop(owner: LifecycleOwner) = manager.onForegroundChanged(false)
            },
        )
        scope.launch { online.collect(manager::onNetworkChanged) }
    }

    companion object {
        /** PostHog flag for the Turnstile step-up; off (or missing) keeps limited sessions on the AI fallback. */
        const val STEP_UP_FLAG = "api-turnstile-step-up"
        private const val TAG = "ApiSessionSetup"

        fun install(app: Application, scope: CoroutineScope): ApiSessionSetup? {
            NetworkModule.appVersion = BuildConfig.VERSION_NAME
            NetworkModule.appChannel = InstallChannel.of(BuildConfig.BOXLORE_DIRECT_UPDATES) { app.isInstalledFromPlayStore() }
            installAppCheck()
            val baseUrl = BuildConfig.BOXLORE_API_BASE_URL
            if (baseUrl.toHttpUrlOrNull() == null) {
                Log.e(TAG, "No valid API base URL; API sessions are off")
                return null
            }
            val manager = ApiSessionManager(
                apiBaseUrl = baseUrl,
                exchanger = OkHttpSessionExchanger(
                    apiBaseUrl = baseUrl,
                    client = NetworkModule.createSessionExchangeClient(),
                    appVersion = { NetworkModule.appVersion },
                    channel = { NetworkModule.appChannel },
                ),
                store = DataStoreApiSessionStore(app),
                deviceKey = AndroidKeystoreDeviceKey(),
                appCheck = FirebaseAppCheckTokenSource { FirebaseAppCheck.getInstance() },
                scope = scope,
                listener = ApiSessionAnalyticsListener(appCheckProvider = if (BuildConfig.DEBUG) "debug" else "play_integrity"),
            )
            val turnstile = TurnstileStepUpController()
            manager.turnstile = turnstile
            manager.stepUpEnabled = { runCatching { PostHog.isFeatureEnabled(STEP_UP_FLAG) }.getOrDefault(false) }
            NetworkModule.sessionManager = manager
            manager.start()
            return ApiSessionSetup(manager, turnstile, baseUrl)
        }

        /**
         * Debug builds use the debug provider (its secret must be registered in the Firebase
         * console; see `BOXLORE_APPCHECK_DEBUG_SECRET`); release builds attest with Play Integrity.
         */
        private fun installAppCheck() {
            runCatching {
                val appCheck = FirebaseAppCheck.getInstance()
                installBuildAppCheckProvider(appCheck)
                appCheck.setTokenAutoRefreshEnabled(false)
            }.onFailure { Log.e(TAG, "App Check setup failed", it) }
        }
    }
}
