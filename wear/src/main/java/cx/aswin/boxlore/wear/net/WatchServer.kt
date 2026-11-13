package cx.aswin.boxlore.wear.net

import android.content.Context
import android.util.Log
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.session.AndroidKeystoreDeviceKey
import cx.aswin.boxlore.core.network.session.ApiSessionManager
import cx.aswin.boxlore.core.network.session.AppCheckResult
import cx.aswin.boxlore.core.network.session.AppCheckTokenSource
import cx.aswin.boxlore.core.network.session.DataStoreApiSessionStore
import cx.aswin.boxlore.core.network.session.OkHttpSessionExchanger
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The watch's own connection to boxlore's API. The watch is a separate install: it holds its own
 * session and device key, and never reuses the phone's.
 */
internal object WatchServer {
    private const val TAG = "WatchServer"

    /** Sent as the install channel, so the API can tell watch sessions from phone ones. */
    const val CHANNEL = "wear"

    /**
     * Starts this watch's API session and returns the API, or null when the build has no API
     * address. Call once per process: it installs the session on the shared network client.
     */
    fun start(context: Context, baseUrl: String, appVersion: String, scope: CoroutineScope): BoxLoreApiV2? {
        if (baseUrl.toHttpUrlOrNull() == null) {
            Log.e(TAG, "No valid API base URL; the watch cannot reach the API")
            return null
        }
        NetworkModule.appVersion = appVersion
        NetworkModule.appChannel = CHANNEL
        val manager =
            ApiSessionManager(
                apiBaseUrl = baseUrl,
                exchanger =
                    OkHttpSessionExchanger(
                        apiBaseUrl = baseUrl,
                        client = NetworkModule.createSessionExchangeClient(),
                        appVersion = { NetworkModule.appVersion },
                        channel = { NetworkModule.appChannel },
                    ),
                store = DataStoreApiSessionStore(context),
                deviceKey = AndroidKeystoreDeviceKey(),
                appCheck = NoAppCheck,
                scope = scope,
            )
        NetworkModule.sessionManager = manager
        manager.onForegroundChanged(true)
        manager.onNetworkChanged(true)
        manager.start()
        return NetworkModule.createBoxLoreApiV2(baseUrl, NetworkModule.createCachedClient(context))
    }

    /** The watch has no device attestation yet, so the API gives it a limited session. */
    private val NoAppCheck = AppCheckTokenSource { AppCheckResult.Failed("unavailable") }
}
