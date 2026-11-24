package cx.aswin.boxlore.tv

import android.content.Context
import cx.aswin.boxlore.tv.cloud.LiveTvAccount
import cx.aswin.boxlore.tv.cloud.NoTvAccount
import cx.aswin.boxlore.tv.cloud.RetrofitTvCloudApi
import cx.aswin.boxlore.tv.cloud.TvAccountStore
import cx.aswin.boxlore.tv.cloud.TvCloudSync
import cx.aswin.boxlore.tv.data.ApiTvCatalog
import cx.aswin.boxlore.tv.data.LiveTvLibrary
import cx.aswin.boxlore.tv.data.NoTvCatalog
import cx.aswin.boxlore.tv.data.RssTvFeedReader
import cx.aswin.boxlore.tv.data.TvLibraryStore
import cx.aswin.boxlore.tv.net.TvServer
import cx.aswin.boxlore.tv.playback.LiveTvPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob

/** The real TV: the listener's saved library, boxlore's API, the shows' own feeds, real audio and sign-in. */
internal object LiveTvContainer {
    fun create(context: Context): TvContainer {
        // Feeds are read and parsed off the main thread; the player lives on it.
        val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val main = MainScope()
        val apis = TvServer.start(context, BuildConfig.BOXLORE_API_BASE_URL, BuildConfig.VERSION_NAME, work)
        val store = TvLibraryStore.create(context, work)
        val library = LiveTvLibrary(store, apis?.catalog?.let(::ApiTvCatalog) ?: NoTvCatalog, apis?.catalog?.let(::RssTvFeedReader), work)
        val playback = LiveTvPlayback(context, library, store, main)
        val account =
            apis?.account?.let { accountApi ->
                val cloud = RetrofitTvCloudApi(accountApi)
                val tokens = TvAccountStore.create(context)
                val sync = TvCloudSync(cloud, tokens, store, library::lookUpShow, { playback.loadedEpisodeId }, work)
                LiveTvAccount(cloud, tokens, store, sync::request, work).also { live ->
                    sync.onRefused = live::refused
                    sync.start()
                }
            } ?: NoTvAccount()
        return TvContainer(library, playback, account)
    }
}
