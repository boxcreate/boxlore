package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import cx.aswin.boxlore.feature.widgets.logic.NowPlayingWidgetMapper
import cx.aswin.boxlore.feature.widgets.logic.WidgetProgressPoller
import cx.aswin.boxlore.feature.widgets.logic.WidgetUpdatePolicy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object NowPlayingWidgetCoordinator {
    private val collectJob = AtomicReference<Job?>(null)
    private val artworkJob = AtomicReference<Job?>(null)
    private val progressJob = AtomicReference<Job?>(null)

    /** How often a playing widget asks the player where it is. Repaints are rarer: see [WidgetUpdatePolicy]. */
    internal const val PROGRESS_POLL_MS = 20_000L

    @Volatile
    private var artworkLoadingUrl: String? = null

    @Volatile
    private var dependencies: NowPlayingWidgetDependencies? = null

    fun start(deps: NowPlayingWidgetDependencies) {
        synchronized(this) {
            val current = collectJob.get()
            if (current?.isActive == true && dependencies === deps) return
            dependencies = deps
            current?.cancel()
            artworkJob.getAndSet(null)?.cancel()
            artworkLoadingUrl = null

            val context = deps.context.applicationContext
            progressJob.getAndSet(pollProgressWhilePlaying(context, deps))?.cancel()
            val store = NowPlayingWidgetSnapshotStore(context)
            val artworkLoader = WidgetArtworkLoader(context)

            collectJob.set(
                deps.scope.launch {
                    deps.playback.restoreBeforeCollect()
                    val cached = store.read()
                    if (cached != null) {
                        renderAll(context, cached)
                    }

                    deps.playback.state.collectLatest { playback ->
                        val previous = store.read()
                        // The system may clear the artwork cache, so a remembered path only counts while its file is there.
                        val cachedPath =
                            artworkLoader.resolveCachedPath(playback.artworkUrl)
                                ?: previous
                                    ?.takeIf { it.episodeId == playback.episodeId }
                                    ?.artworkCachePath
                                    ?.takeIf { java.io.File(it).exists() }
                        val snapshot =
                            NowPlayingWidgetMapper.fromPlayback(
                                state = playback,
                                artworkCachePath = cachedPath,
                            )
                        if (WidgetUpdatePolicy.shouldRender(previous, snapshot)) {
                            store.write(snapshot)
                            renderAll(context, snapshot)
                        }
                        loadArtworkAfterRender(context, snapshot, artworkLoader, store)
                    }
                },
            )
        }
    }

    /**
     * The app stops tracking the position once it leaves the screen, which is when widgets are looked
     * at. While an episode plays and a playback widget is placed, ask the player for it now and then
     * so the progress ring and the time left keep moving.
     */
    private fun pollProgressWhilePlaying(
        context: Context,
        deps: NowPlayingWidgetDependencies,
    ): Job = deps.scope.launch {
        WidgetProgressPoller.run(
            playing = deps.playback.state.map { it.isPlaying && it.hasEpisode },
            intervalMs = PROGRESS_POLL_MS,
            hasWidgets = { hasInstalledWidgets(context, WidgetProviders.all.map { it.receiverClass }) },
            refresh = { deps.playback.refreshPosition() },
        )
    }

    fun requestRefresh(context: Context) {
        val store = NowPlayingWidgetSnapshotStore(context)
        val snapshot = store.read() ?: return
        renderAll(context.applicationContext, snapshot)
    }

    private fun renderAll(
        context: Context,
        snapshot: NowPlayingWidgetSnapshot,
    ) {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
        WidgetProviders.all.forEach { provider ->
            val ids =
                manager.getAppWidgetIds(
                    ComponentName(context, provider.receiverClass),
                )
            if (ids.isEmpty()) return@forEach
            NowPlayingWidgetRenderer.updateAll(
                context = context,
                appWidgetManager = manager,
                appWidgetIds = ids,
                snapshot = snapshot,
                variant = provider.variant,
            )
        }
    }

    private fun loadArtworkAfterRender(
        context: Context,
        snapshot: NowPlayingWidgetSnapshot,
        artworkLoader: WidgetArtworkLoader,
        store: NowPlayingWidgetSnapshotStore,
    ) {
        if (!snapshot.hasEpisode) return
        val url = snapshot.artworkUrl
        if (cx.aswin.boxlore.core.model.artworkUrls(url, *snapshot.fallbackArtworkUrls.toTypedArray()).isEmpty()) return
        if (!snapshot.artworkCachePath.isNullOrBlank()) return

        val inFlight = artworkJob.get()
        if (inFlight?.isActive == true && artworkLoadingUrl == url) return

        artworkJob.getAndSet(null)?.cancel()
        artworkLoadingUrl = url
        val deps = dependencies ?: return
        artworkJob.set(
            deps.scope.launch {
                try {
                    val path = artworkLoader.load(url, snapshot.fallbackArtworkUrls) ?: return@launch
                    val latest = store.read() ?: return@launch
                    if (latest.episodeId != snapshot.episodeId || latest.artworkUrl != url) return@launch
                    if (latest.artworkCachePath == path) return@launch

                    val withArt = latest.copy(artworkCachePath = path)
                    store.write(withArt)
                    renderAll(context.applicationContext, withArt)
                } finally {
                    if (artworkLoadingUrl == url) {
                        artworkLoadingUrl = null
                    }
                }
            },
        )
    }
}
