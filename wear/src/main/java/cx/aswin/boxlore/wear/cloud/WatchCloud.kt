package cx.aswin.boxlore.wear.cloud

import android.content.Context
import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.model.WatchShow
import kotlinx.coroutines.CoroutineScope

/** What the account needs from the rest of the watch app. */
internal class WatchCloudPorts(
    /** Asks the phone to sign this watch in; null when the phone is out of reach. */
    val askPhone: suspend () -> WatchSignInReply?,
    /** A followed show's name and artwork from the API; null when that failed. */
    val showOf: suspend (showId: String) -> WatchShow?,
    /** The episode in the watch's player. */
    val playingId: () -> String?,
)

/** Puts the watch's account and its cloud sync together. */
internal object WatchCloud {
    /** The account, with its sync running; [NoWatchAccount] for a build with no API. */
    fun start(context: Context, api: WatchAccountApi?, store: WatchLibraryStore, ports: WatchCloudPorts, scope: CoroutineScope): WatchAccount {
        if (api == null) return NoWatchAccount
        val cloud = RetrofitWatchCloudApi(api)
        val tokens = SharedPreferencesWatchAccountStore.create(context)
        var account: LiveWatchAccount? = null
        val sync = WatchCloudSync(cloud, tokens, store, ports.showOf, ports.playingId, scope, onSignedOut = { account?.forget() })
        val live = LiveWatchAccount(ports.askPhone, cloud, tokens, store, onSignedIn = sync::request, scope = scope)
        account = live
        sync.start()
        return live
    }
}
