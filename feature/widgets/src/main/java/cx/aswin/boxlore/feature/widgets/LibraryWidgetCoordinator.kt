package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import cx.aswin.boxlore.feature.widgets.logic.LibraryWidgetLogic
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
     * Fetches covers the widgets do not have yet and shows them as they arrive, a few at a time.
     * Waiting for the whole library first left a widget without covers whenever the app was closed
     * before the last one came in.
     */
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
                var current = snapshot
                var unpublished = 0

                fun publish() {
                    store.write(current.copy(updatedAtMs = System.currentTimeMillis()))
                    notifyRowsChanged(context.applicationContext)
                    unpublished = 0
                }

                snapshot.subscriptions.forEachIndexed { index, row ->
                    if (!row.needsArtwork()) return@forEachIndexed
                    val path = artworkLoader.load(row.artworkUrl, row.fallbackArtworkUrls) ?: return@forEachIndexed
                    current = current.copy(subscriptions = current.subscriptions.toMutableList().also { it[index] = row.copy(artworkCachePath = path) })
                    if (++unpublished >= PUBLISH_EVERY) publish()
                }
                snapshot.newEpisodes.forEachIndexed { index, row ->
                    if (!row.needsArtwork()) return@forEachIndexed
                    val path = artworkLoader.load(row.artworkUrl, row.fallbackArtworkUrls) ?: return@forEachIndexed
                    current = current.copy(newEpisodes = current.newEpisodes.toMutableList().also { it[index] = row.copy(artworkCachePath = path) })
                    if (++unpublished >= PUBLISH_EVERY) publish()
                }
                if (unpublished > 0) publish()
            },
        )
    }

    /** Tells the placed library widgets to redraw their rows; the frame around them stays as it is. */
    private fun notifyRowsChanged(context: Context) {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
        LibraryWidgetProviders.all.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider.receiverClass))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list_view)
        }
    }

    private fun WidgetShowRow.needsArtwork(): Boolean = artworkCachePath.isNullOrBlank() &&
        cx.aswin.boxlore.core.model.artworkUrls(artworkUrl, *fallbackArtworkUrls.toTypedArray()).isNotEmpty()

    private fun WidgetEpisodeRow.needsArtwork(): Boolean = artworkCachePath.isNullOrBlank() &&
        cx.aswin.boxlore.core.model.artworkUrls(artworkUrl, *fallbackArtworkUrls.toTypedArray()).isNotEmpty()

    /** The system may clear the artwork cache, so a remembered path only counts while its file is there. */
    private fun String?.ifOnDisk(): String? = this?.takeIf { it.isNotBlank() && java.io.File(it).exists() }

    private const val PUBLISH_EVERY = 4
}
