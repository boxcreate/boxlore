package cx.aswin.boxlore.wear

import android.content.Context
import android.util.Log
import cx.aswin.boxlore.core.wearlink.GmsWearLinkTransport
import cx.aswin.boxlore.core.wearlink.WearLinkProtocol
import cx.aswin.boxlore.wear.data.ApiWatchCatalog
import cx.aswin.boxlore.wear.data.FileWatchDownloads
import cx.aswin.boxlore.wear.data.LiveWatchLibrary
import cx.aswin.boxlore.wear.data.NoWatchCatalog
import cx.aswin.boxlore.wear.data.RssWatchFeedReader
import cx.aswin.boxlore.wear.data.StoragePolicy
import cx.aswin.boxlore.wear.data.WatchFeedReader
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.data.WatchSettings
import cx.aswin.boxlore.wear.data.WatchStorageLimits
import cx.aswin.boxlore.wear.glance.BoxloreComplicationService
import cx.aswin.boxlore.wear.glance.BoxloreTileService
import cx.aswin.boxlore.wear.glance.GlanceRules
import cx.aswin.boxlore.wear.link.LinkedWatchPlayback
import cx.aswin.boxlore.wear.link.LiveWatchLink
import cx.aswin.boxlore.wear.link.PhoneOrders
import cx.aswin.boxlore.wear.link.PlayerGlance
import cx.aswin.boxlore.wear.link.WatchLinkRules
import cx.aswin.boxlore.wear.net.WatchServer
import cx.aswin.boxlore.wear.playback.LiveWatchPlayback
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Builds the watch's real object graph: boxlore's API, the saved library, downloads and the player. */
internal object LiveWatchContainer {
    private const val LINK_LOG_TAG = "WatchLink"

    fun create(context: Context): WatchContainer {
        val app = context.applicationContext
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val api = WatchServer.start(app, BuildConfig.BOXLORE_API_BASE_URL, BuildConfig.VERSION_NAME, background)
        val store = WatchLibraryStore.create(app, background)
        val settings = WatchSettings.create(app)
        val limits =
            WatchStorageLimits(
                limitBytes = { StoragePolicy.limitBytes(settings.storageLimitMb.value) },
                freeBytes = { app.filesDir.usableSpace },
            )
        val downloads =
            FileWatchDownloads(
                directory = File(app.filesDir, FileWatchDownloads.DIRECTORY_NAME),
                // Podcast hosts get a plain client: the API session never leaves boxlore's own API.
                client = OkHttpClient(),
                store = store,
                limits = limits,
                scope = background,
            )
        val transport = GmsWearLinkTransport(app, WearLinkProtocol.CAPABILITY_PHONE, background, WearLinkProtocol.CAPABILITY_WATCH)
        val link = LiveWatchLink(transport, store, settings, downloads, background, log = { Log.d(LINK_LOG_TAG, it) })
        val library =
            LiveWatchLibrary(
                catalog = api?.let(::ApiWatchCatalog) ?: NoWatchCatalog,
                feeds = api?.let(::RssWatchFeedReader) ?: WatchFeedReader { _, _ -> null },
                store = store,
                downloadManager = downloads,
                scope = main,
                phoneEpisodes = { showId, show -> link.episodes(showId)?.map { WatchLinkRules.watchEpisode(it, show) } },
                // Episodes in the phone's queue get a page on the watch like any other.
                namedByPhone = link.phoneNowPlaying.map { phone -> phone?.upNext.orEmpty().map { WatchLinkRules.watchEpisode(it) } },
            )
        val watchPlayer = LiveWatchPlayback(app, library, store, downloads, main)
        val playback = LinkedWatchPlayback(watchPlayer, link, store, main)
        link.player = { PlayerGlance(watchPlayer.loadedEpisodeId, watchPlayer.nowPlaying.value?.isPlaying == true) }
        link.start()
        limits.playingId = { watchPlayer.nowPlaying.value?.episode?.id }
        // The tile and the watch-face shortcut are redrawn when the episode to jump back into, or its time left, changes.
        background.launch {
            store.state.map { GlanceRules.key(GlanceRules.from(it)) }.distinctUntilChanged().collect {
                runCatching {
                    BoxloreTileService.requestUpdate(app)
                    BoxloreComplicationService.requestUpdate(app)
                }
            }
        }
        background.launch {
            watchPlayer.nowPlaying.map { (it?.isPlaying == true) to it?.episode?.id }.distinctUntilChanged().collect { (playing, id) ->
                link.publishPlaying(playing, id)
            }
        }
        // Runs once at start-up and again whenever the listener changes the limit.
        background.launch { settings.storageLimitMb.collect { downloads.enforceLimit() } }
        return WatchContainer(library = library, playback = playback, settings = settings, onPhoneCommand = PhoneOrders(store, downloads, playback::fromPhone)::take, glance = { GlanceRules.from(store.state.value) })
    }
}
