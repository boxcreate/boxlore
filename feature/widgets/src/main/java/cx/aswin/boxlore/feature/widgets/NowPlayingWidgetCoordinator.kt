package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import cx.aswin.boxlore.feature.widgets.logic.NowPlayingWidgetMapper
import cx.aswin.boxlore.feature.widgets.logic.WidgetUpdatePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object NowPlayingWidgetCoordinator {
    private var collectJob: Job? = null
    private var dependencies: NowPlayingWidgetDependencies? = null

    fun start(deps: NowPlayingWidgetDependencies) {
        if (collectJob?.isActive == true && dependencies === deps) return
        dependencies = deps
        collectJob?.cancel()

        val context = deps.context.applicationContext
        val store = NowPlayingWidgetSnapshotStore(context)
        val artworkLoader = WidgetArtworkLoader(context)

        collectJob =
            deps.scope.launch {
                deps.playback.restoreBeforeCollect()
                val cached = store.read()
                if (cached != null) {
                    renderAll(context, cached)
                }

                deps.playback.state.collectLatest { playback ->
                    val previous = store.read()
                    val cachedPath =
                        artworkLoader.resolveCachedPath(playback.artworkUrl)
                            ?: previous
                                ?.takeIf { it.episodeId == playback.episodeId }
                                ?.artworkCachePath
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
            }
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
        val url = snapshot.artworkUrl ?: return
        if (!snapshot.artworkCachePath.isNullOrBlank()) return

        val deps = dependencies ?: return
        deps.scope.launch {
            val path = artworkLoader.load(url) ?: return@launch
            val latest = store.read() ?: return@launch
            if (latest.episodeId != snapshot.episodeId || latest.artworkUrl != url) return@launch
            if (latest.artworkCachePath == path) return@launch

            val withArt = latest.copy(artworkCachePath = path)
            store.write(withArt)
            renderAll(context.applicationContext, withArt)
        }
    }
}
