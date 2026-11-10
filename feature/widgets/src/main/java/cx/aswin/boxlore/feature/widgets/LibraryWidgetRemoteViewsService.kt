package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import cx.aswin.boxlore.core.prefs.WidgetShowsArtwork
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Supplies the scrolling New Episodes rows and Your Shows tiles.
 * Reads the latest [LibraryWidgetSnapshot] from prefs (written by the coordinator).
 */
class LibraryWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val kind =
            intent
                .getStringExtra(EXTRA_KIND)
                ?.let { runCatching { LibraryWidgetKind.valueOf(it) }.getOrNull() }
                ?: LibraryWidgetKind.SUBSCRIPTIONS
        return LibraryWidgetRemoteViewsFactory(applicationContext, kind)
    }

    companion object {
        const val EXTRA_KIND = "library_widget_kind"

        fun adapterIntent(
            context: Context,
            appWidgetId: Int,
            kind: LibraryWidgetKind,
        ): Intent = Intent(context, LibraryWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EXTRA_KIND, kind.name)
            // Unique data URI so each widget instance gets its own adapter.
            data = Uri.parse("boxlore://library_widget/${kind.name}/$appWidgetId")
        }
    }
}

/** The rows last saved for the library widgets, or none before the first save. */
private fun savedSnapshot(context: Context): LibraryWidgetSnapshot = LibraryWidgetSnapshotStore(context).read() ?: LibraryWidgetSnapshot()

internal class LibraryWidgetRemoteViewsFactory(
    private val context: Context,
    private val kind: LibraryWidgetKind,
    /** Rows to show instead of the saved snapshot: the sample content of a picker preview. */
    private val preset: LibraryWidgetSnapshot? = null,
) : RemoteViewsService.RemoteViewsFactory {
    private var shows: List<WidgetShowRow> = emptyList()
    private var episodes: List<WidgetEpisodeRow> = emptyList()
    private var chrome: WidgetChrome = WidgetChrome.resolve(context)
    private var mixedShapes: Boolean = WidgetShowsArtwork.usesShapes(WidgetShowsArtwork.cached(context))
    private val artworkLoader by lazy { WidgetArtworkLoader(context) }

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val snapshot = preset ?: savedSnapshot(context)
        shows = snapshot.subscriptions
        episodes = snapshot.newEpisodes
        chrome = WidgetChrome.resolve(context)
        mixedShapes = WidgetShowsArtwork.usesShapes(WidgetShowsArtwork.cached(context))
    }

    override fun onDestroy() {
        shows = emptyList()
        episodes = emptyList()
    }

    override fun getCount(): Int = when (kind) {
        LibraryWidgetKind.SUBSCRIPTIONS -> shows.size
        LibraryWidgetKind.NEW_EPISODES -> episodes.size
    }

    override fun getViewAt(position: Int): RemoteViews = when (kind) {
        LibraryWidgetKind.SUBSCRIPTIONS -> showTile(position)
        LibraryWidgetKind.NEW_EPISODES -> episodeRow(position)
    }

    /** One show: its cover, whole or cut into this tile's shape, with a dot when it has a new episode. */
    private fun showTile(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.library_widget_list_item)
        val row = shows.getOrNull(position) ?: return views
        val shape = tileShape(mixedShapes, position)
        val sizePx = px(TILE_ART_DP)
        bindArt(views, artworkPath(row.artworkCachePath, row.artworkUrl, row.fallbackArtworkUrls), sizePx, shape)
        // The placeholder is cut to the same shape, so a tile keeps its outline when it has no cover.
        views.setImageViewBitmap(R.id.widget_row_art_placeholder, WidgetBitmapCache.mask(shape, sizePx))
        views.setContentDescription(R.id.widget_row_root, row.title)

        val dot = if (row.isNew) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.widget_row_new_dot_ring, dot)
        views.setViewVisibility(R.id.widget_row_new_dot, dot)
        if (row.isNew) {
            // The ring is the widget's own colour, so the dot reads as cut out of the cover.
            chrome.setColorFilter(views, R.id.widget_row_new_dot_ring, LibraryWidgetRenderer.palette(kind).container)
            chrome.setColorFilter(views, R.id.widget_row_new_dot, WidgetPalette.tertiary)
        }
        views.setOnClickFillInIntent(R.id.widget_row_root, Intent().apply { data = row.deepLinkUri.toUri() })
        return views
    }

    /** One latest episode: a pill with round artwork, the episode and its show. */
    private fun episodeRow(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.library_widget_episode_list_item)
        val row = episodes.getOrNull(position) ?: return views
        chrome.setColorFilter(views, R.id.widget_row_bg, WidgetPalette.surface)
        views.setTextViewText(R.id.widget_row_title, row.episodeTitle)
        views.setTextViewText(R.id.widget_row_subtitle, row.podcastTitle)
        chrome.setTextColor(views, R.id.widget_row_title, WidgetPalette.onSurface)
        chrome.setTextColor(views, R.id.widget_row_subtitle, WidgetPalette.onSurfaceVariant)
        bindArt(views, artworkPath(row.artworkCachePath, row.artworkUrl, row.fallbackArtworkUrls), px(ROW_ART_DP), WidgetArtShape.CIRCLE)
        views.setOnClickFillInIntent(R.id.widget_row_root, Intent().apply { data = row.deepLinkUri.toUri() })
        return views
    }

    /**
     * What a row or tile looks like while its cover is fetched: the placeholder it would show without
     * one, in place of the launcher's plain "Loading" text.
     */
    override fun getLoadingView(): RemoteViews = when (kind) {
        LibraryWidgetKind.SUBSCRIPTIONS ->
            RemoteViews(context.packageName, R.layout.library_widget_list_item).also { views ->
                bindArt(views, null, px(TILE_ART_DP), WidgetArtShape.ROUNDED)
                views.setImageViewBitmap(R.id.widget_row_art_placeholder, WidgetBitmapCache.mask(WidgetArtShape.ROUNDED, px(TILE_ART_DP)))
            }
        LibraryWidgetKind.NEW_EPISODES ->
            RemoteViews(context.packageName, R.layout.library_widget_episode_list_item).also { views ->
                chrome.setColorFilter(views, R.id.widget_row_bg, WidgetPalette.surface)
                bindArt(views, null, px(ROW_ART_DP), WidgetArtShape.CIRCLE)
            }
    }

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = when (kind) {
        LibraryWidgetKind.SUBSCRIPTIONS ->
            shows
                .getOrNull(position)
                ?.podcastId
                ?.hashCode()
                ?.toLong()
                ?: position.toLong()
        LibraryWidgetKind.NEW_EPISODES ->
            episodes
                .getOrNull(position)
                ?.episodeId
                ?.hashCode()
                ?.toLong()
                ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    private fun bindArt(
        views: RemoteViews,
        cachePath: String?,
        sizePx: Int,
        shape: WidgetArtShape,
    ) {
        val bitmap = cachePath?.let { WidgetBitmapCache.artwork(it, sizePx, shape) }
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_row_art, bitmap)
            views.setViewVisibility(R.id.widget_row_art, View.VISIBLE)
            views.setViewVisibility(R.id.widget_row_art_placeholder, View.GONE)
            views.setViewVisibility(R.id.widget_row_art_mark, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_row_art, View.GONE)
        views.setViewVisibility(R.id.widget_row_art_placeholder, View.VISIBLE)
        views.setViewVisibility(R.id.widget_row_art_mark, View.VISIBLE)
        chrome.setColorFilter(views, R.id.widget_row_art_placeholder, WidgetPalette.secondaryContainer)
        chrome.setColorFilter(views, R.id.widget_row_art_mark, WidgetPalette.onSecondaryContainer)
    }

    /**
     * Where this row's cover is on disk, fetching it first if the widget has never had it. The app only
     * fetches covers while it is running; a widget placed on the home screen cannot wait for that.
     * The launcher calls this off the main thread and shows the loading tile meanwhile.
     */
    private fun artworkPath(cachePath: String?, url: String?, fallbackUrls: List<String>): String? {
        cachePath?.takeIf { it.isNotBlank() && File(it).exists() }?.let { return it }
        // Sample rows of a picker preview have no address to fetch from.
        if (preset != null || cx.aswin.boxlore.core.model.artworkUrls(url, *fallbackUrls.toTypedArray()).isEmpty()) return null
        artworkLoader.resolveCachedPath(url)?.let { return it }
        return runCatching {
            runBlocking { withTimeoutOrNull(ARTWORK_TIMEOUT_MS) { artworkLoader.load(url, fallbackUrls) } }
        }.getOrNull()
    }

    private fun px(dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt().coerceIn(1, MAX_ART_PX)

    internal companion object {
        /** Whole covers as rounded squares, or the mixed shapes when the listener chose them. */
        fun tileShape(mixedShapes: Boolean, position: Int): WidgetArtShape = if (mixedShapes) WidgetShapes.mixedShapeAt(position) else WidgetArtShape.ROUNDED

        private const val TILE_ART_DP = 84
        private const val ROW_ART_DP = 46
        private const val MAX_ART_PX = 288
        private const val ARTWORK_TIMEOUT_MS = 6_000L
    }
}
