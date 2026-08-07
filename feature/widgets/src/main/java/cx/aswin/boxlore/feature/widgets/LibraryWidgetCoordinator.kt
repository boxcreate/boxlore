package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import cx.aswin.boxlore.feature.widgets.logic.LibraryWidgetLogic
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

object LibraryWidgetCoordinator {
    private val collectJob = AtomicReference<Job?>(null)
    private val artworkJob = AtomicReference<Job?>(null)

    @Volatile
    private var dependencies: LibraryWidgetDependencies? = null

    fun start(deps: LibraryWidgetDependencies) {
        synchronized(this) {
            val current = collectJob.get()
            if (current?.isActive == true && dependencies === deps) return
            dependencies = deps
            current?.cancel()
            artworkJob.getAndSet(null)?.cancel()

            val context = deps.context.applicationContext
            val store = LibraryWidgetSnapshotStore(context)
            val artworkLoader = WidgetArtworkLoader(context)

            collectJob.set(
                deps.scope.launch {
                    val cached = store.read()
                    if (cached != null) {
                        renderAll(context, cached)
                    }

                    combine(deps.library.subscriptions, deps.library.newEpisodes) { shows, episodes ->
                        shows to episodes
                    }.collectLatest { (shows, episodes) ->
                        val previous = store.read()
                        val withDiskCacheShows =
                            shows.map { row ->
                                row.copy(
                                    artworkCachePath =
                                        row.artworkCachePath
                                            ?: artworkLoader.resolveCachedPath(row.artworkUrl),
                                )
                            }
                        val withDiskCacheEpisodes =
                            episodes.map { row ->
                                row.copy(
                                    artworkCachePath =
                                        row.artworkCachePath
                                            ?: artworkLoader.resolveCachedPath(row.artworkUrl),
                                )
                            }
                        val mergedShows =
                            LibraryWidgetLogic.mergeArtworkPaths(
                                previous?.subscriptions.orEmpty(),
                                withDiskCacheShows,
                            )
                        val mergedEpisodes =
                            LibraryWidgetLogic.mergeEpisodeArtworkPaths(
                                previous?.newEpisodes.orEmpty(),
                                withDiskCacheEpisodes,
                            )
                        val snapshot =
                            LibraryWidgetSnapshot(
                                subscriptions = mergedShows,
                                newEpisodes = mergedEpisodes,
                                updatedAtMs = System.currentTimeMillis(),
                            )
                        store.write(snapshot)
                        renderAll(context, snapshot)
                        loadMissingArtwork(context, snapshot, artworkLoader, store)
                    }
                },
            )
        }
    }

    fun requestRefresh(context: Context) {
        val store = LibraryWidgetSnapshotStore(context)
        val snapshot = store.read() ?: return
        renderAll(context.applicationContext, snapshot)
    }

    private fun renderAll(
        context: Context,
        snapshot: LibraryWidgetSnapshot,
    ) {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
        LibraryWidgetProviders.all.forEach { provider ->
            val ids =
                manager.getAppWidgetIds(
                    ComponentName(context, provider.receiverClass),
                )
            if (ids.isEmpty()) return@forEach
            LibraryWidgetRenderer.updateAll(
                context = context,
                appWidgetManager = manager,
                appWidgetIds = ids,
                snapshot = snapshot,
                kind = provider.kind,
            )
        }
    }

    private fun loadMissingArtwork(
        context: Context,
        snapshot: LibraryWidgetSnapshot,
        artworkLoader: WidgetArtworkLoader,
        store: LibraryWidgetSnapshotStore,
    ) {
        val deps = dependencies ?: return
        artworkJob.getAndSet(null)?.cancel()
        artworkJob.set(
            deps.scope.launch {
                var shows = snapshot.subscriptions
                var episodes = snapshot.newEpisodes
                var changed = false

                shows =
                    shows.map { row ->
                        if (!row.artworkCachePath.isNullOrBlank() || row.artworkUrl.isNullOrBlank()) {
                            row
                        } else {
                            val path = artworkLoader.load(row.artworkUrl) ?: return@map row
                            changed = true
                            row.copy(artworkCachePath = path)
                        }
                    }
                episodes =
                    episodes.map { row ->
                        if (!row.artworkCachePath.isNullOrBlank() || row.artworkUrl.isNullOrBlank()) {
                            row
                        } else {
                            val path = artworkLoader.load(row.artworkUrl) ?: return@map row
                            changed = true
                            row.copy(artworkCachePath = path)
                        }
                    }

                if (!changed) return@launch
                val withArt =
                    LibraryWidgetSnapshot(
                        subscriptions = shows,
                        newEpisodes = episodes,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                store.write(withArt)
                renderAll(context.applicationContext, withArt)
            },
        )
    }
}
