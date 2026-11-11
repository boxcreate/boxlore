package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import cx.aswin.boxlore.feature.widgets.logic.LibraryArtworkBatches
import cx.aswin.boxlore.feature.widgets.logic.LibraryWidgetLogic
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object LibraryWidgetCoordinator {
    private val collectJob = AtomicReference<Job?>(null)

    @Volatile
    private var dependencies: LibraryWidgetDependencies? = null

    fun start(deps: LibraryWidgetDependencies) {
        synchronized(this) {
            val current = collectJob.get()
            if (current?.isActive == true && dependencies === deps) return
            dependencies = deps
            current?.cancel()

            val context = deps.context.applicationContext
            val store = LibraryWidgetSnapshotStore(context)
            val artworkLoader = WidgetArtworkLoader(context)

            collectJob.set(
                deps.scope.launch {
                    val cached = store.read()
                    if (cached != null) {
                        renderAll(context, cached)
                    }

                    combine(deps.library.subscriptions, deps.library.newEpisodes, deps.library.counts) { shows, episodes, counts ->
                        Triple(shows, episodes, counts)
                    }.collectLatest { (shows, episodes, counts) ->
                        val previous = store.read()
                        val withDiskCacheShows =
                            shows.map { row ->
                                row.copy(
                                    artworkCachePath =
                                    row.artworkCachePath.ifOnDisk()
                                        ?: artworkLoader.resolveCachedPath(row.artworkUrl),
                                )
                            }
                        val withDiskCacheEpisodes =
                            episodes.map { row ->
                                row.copy(
                                    artworkCachePath =
                                    row.artworkCachePath.ifOnDisk()
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
                                counts = counts,
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

    /**
     * Fetches covers the widgets do not have yet and shows them as they arrive. It runs inside the
     * collector's own block: a newer library cancels it and waits for it to stop before writing its own
     * snapshot, so an older snapshot can never be saved over a newer one.
     */
    private suspend fun loadMissingArtwork(
        context: Context,
        snapshot: LibraryWidgetSnapshot,
        artworkLoader: WidgetArtworkLoader,
        store: LibraryWidgetSnapshotStore,
    ) {
        LibraryArtworkBatches.load(snapshot, load = { url, fallbackUrls -> artworkLoader.load(url, fallbackUrls) }) { withCovers ->
            store.write(withCovers.copy(updatedAtMs = System.currentTimeMillis()))
            notifyRowsChanged(context.applicationContext)
        }
    }

    /** Tells the placed library widgets to redraw their rows; the frame around them stays as it is. */
    private fun notifyRowsChanged(context: Context) {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
        LibraryWidgetProviders.all.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider.receiverClass))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list_view)
        }
    }

    /** The system may clear the artwork cache, so a remembered path only counts while its file is there. */
    private fun String?.ifOnDisk(): String? = this?.takeIf { it.isNotBlank() && java.io.File(it).exists() }
}
