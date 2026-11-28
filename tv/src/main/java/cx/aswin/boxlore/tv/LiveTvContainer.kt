package cx.aswin.boxlore.tv

import android.content.Context
import cx.aswin.boxlore.tv.cloud.LiveTvAccount
import cx.aswin.boxlore.tv.cloud.NoTvAccount
import cx.aswin.boxlore.tv.cloud.RetrofitTvCloudApi
import cx.aswin.boxlore.tv.cloud.TvAccountStore
import cx.aswin.boxlore.tv.cloud.TvCloudSync
import cx.aswin.boxlore.tv.cloud.TvPairing
import cx.aswin.boxlore.tv.data.ApiTvCatalog
import cx.aswin.boxlore.tv.data.LiveTvLibrary
import cx.aswin.boxlore.tv.data.NoTvCatalog
import cx.aswin.boxlore.tv.data.RssTvFeedReader
import cx.aswin.boxlore.tv.data.SignedInTvCatalog
import cx.aswin.boxlore.tv.data.TvLibraryStore
import cx.aswin.boxlore.tv.net.TvServer
import cx.aswin.boxlore.tv.playback.LiveTvPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The real TV: the listener's saved library, boxlore's API, the shows' own feeds, real audio and
 * sign-in. Nothing but sign-in reaches the API until the TV is signed in.
 */
internal object LiveTvContainer {
    fun create(context: Context): TvContainer {
        // Feeds are read and parsed off the main thread; the player lives on it.
        val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val main = MainScope()
        val tokens = TvAccountStore.create(context)
        val pairing = TvPairing(tokens)
        val apis = TvServer.start(context, BuildConfig.BOXLORE_API_BASE_URL, BuildConfig.VERSION_NAME, work, pairing)
        val signedIn = MutableStateFlow(tokens.token != null)
        val store = TvLibraryStore.create(context, work)
        val catalog = apis?.catalog?.let { api -> SignedInTvCatalog(ApiTvCatalog(api)) { tokens.token != null } } ?: NoTvCatalog
        val library = LiveTvLibrary(store, catalog, apis?.catalog?.let(::RssTvFeedReader), work, signedIn = signedIn)
        val playback = LiveTvPlayback(context, library, store, main)
        val account =
            apis?.let { live ->
                val cloud = RetrofitTvCloudApi(live.account)
                val sync = TvCloudSync(cloud, tokens, store, library::lookUpShow, { playback.loadedEpisodeId }, work)
                val changed = {
                    signedIn.value = tokens.token != null
                    live.sessions.onPairedAccountChanged()
                }
                LiveTvAccount(cloud, tokens, store, sync::request, work, onAccountChanged = changed).also { account ->
                    sync.onRefused = account::refused
                    pairing.onRefused = account::refused
                    account.giveUpUnrevoked()
                    sync.start()
                }
            } ?: NoTvAccount()
        return TvContainer(library, playback, account)
    }
}
