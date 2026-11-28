package cx.aswin.boxlore.wear.net

import android.content.Context
import android.util.Log
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.network.session.AndroidKeystoreDeviceKey
import cx.aswin.boxlore.core.network.session.ApiSessionManager
import cx.aswin.boxlore.core.network.session.AppCheckResult
import cx.aswin.boxlore.core.network.session.AppCheckTokenSource
import cx.aswin.boxlore.core.network.session.DataStoreApiSessionStore
import cx.aswin.boxlore.core.network.session.OkHttpPairedSessionTrader
import cx.aswin.boxlore.core.network.session.OkHttpSessionExchanger
import cx.aswin.boxlore.core.network.session.PairedSessionAccount
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The API as the watch uses it: the catalog routes, the account's, and the session behind them. */
internal class WatchApis(val catalog: BoxLoreApiV2, val account: WatchAccountApi, val sessions: ApiSessionManager)

/**
 * The watch's own connection to boxlore's API. The watch is a separate install: it holds its own
 * session and device key, and never reuses the phone's. It uses the API only once signed in, with
 * a paired session traded for its account token; before that only redeeming the phone's code goes
 * out, with a limited session (see [ApiSessionManager]).
 */
internal object WatchServer {
    private const val TAG = "WatchServer"

    /** Sent as the install channel, so the API can tell watch sessions from phone ones. */
    const val CHANNEL = "wear"

    /**
     * Starts this watch's API session for [account] and returns its APIs, or null when the build
     * has no API address. Call once per process: it installs the session on the shared network client.
     */
    fun start(context: Context, baseUrl: String, appVersion: String, scope: CoroutineScope, account: PairedSessionAccount): WatchApis? {
        if (baseUrl.toHttpUrlOrNull() == null) {
            Log.e(TAG, "No valid API base URL; the watch cannot reach the API")
            return null
        }
        NetworkModule.appVersion = appVersion
        NetworkModule.appChannel = CHANNEL
        val exchangeClient = NetworkModule.createSessionExchangeClient()
        val manager =
            ApiSessionManager(
                apiBaseUrl = baseUrl,
                exchanger =
                    OkHttpSessionExchanger(
                        apiBaseUrl = baseUrl,
                        client = exchangeClient,
                        appVersion = { NetworkModule.appVersion },
                        channel = { NetworkModule.appChannel },
                    ),
                store = DataStoreApiSessionStore(context),
                deviceKey = AndroidKeystoreDeviceKey(),
                appCheck = NoAppCheck,
                scope = scope,
                pairedTrader = OkHttpPairedSessionTrader(baseUrl, exchangeClient, { NetworkModule.appVersion }, { NetworkModule.appChannel }),
            )
        manager.pairedAccount = account
        NetworkModule.sessionManager = manager
        manager.onForegroundChanged(true)
        manager.onNetworkChanged(true)
        manager.start()
        val client = NetworkModule.createCachedClient(context)
        return WatchApis(NetworkModule.createBoxLoreApiV2(baseUrl, client), NetworkModule.createWatchAccountApi(baseUrl, client), manager)
    }

    /** The watch has no device attestation: a limited session until it signs in, then a paired one. */
    private val NoAppCheck = AppCheckTokenSource { AppCheckResult.Failed("unavailable") }
}
