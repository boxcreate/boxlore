package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.model.WatchShow
import cx.aswin.boxlore.wear.net.WatchApis
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
    /**
     * The account in [tokens], with its sync running; [NoWatchAccount] for a build with no API. A
     * paired-session trade the API refuses ([pairing]) signs the watch out, as a refused sync does.
     */
    fun start(apis: WatchApis?, tokens: WatchAccountStore, pairing: WatchPairing, store: WatchLibraryStore, ports: WatchCloudPorts, scope: CoroutineScope): WatchAccount {
        if (apis == null) return NoWatchAccount
        val cloud = RetrofitWatchCloudApi(apis.account)
        var account: LiveWatchAccount? = null
        val sync = WatchCloudSync(cloud, tokens, store, ports.showOf, ports.playingId, scope, onSignedOut = { refused -> account?.forget(refused) })
        val live = LiveWatchAccount(ports.askPhone, cloud, tokens, store, onSignedIn = sync::request, scope = scope, onAccountChanged = apis.sessions::onPairedAccountChanged)
        account = live
        pairing.onRefused = { refused -> live.forget(refused) }
        live.giveUpUnrevoked()
        sync.start()
        return live
    }
}
