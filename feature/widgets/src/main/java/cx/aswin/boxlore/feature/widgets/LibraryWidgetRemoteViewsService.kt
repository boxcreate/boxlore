package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Supplies scrollable rows for Subscriptions / New Episodes [ListView] widgets.
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
        ): Intent =
            Intent(context, LibraryWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_KIND, kind.name)
                // Unique data URI so each widget instance gets its own adapter.
                data = Uri.parse("boxlore://library_widget/${kind.name}/$appWidgetId")
            }
    }
}

internal class LibraryWidgetRemoteViewsFactory(
    private val context: Context,
    private val kind: LibraryWidgetKind,
) : RemoteViewsService.RemoteViewsFactory {
    private var shows: List<WidgetShowRow> = emptyList()
    private var episodes: List<WidgetEpisodeRow> = emptyList()
    private var chrome: WidgetChrome = WidgetChrome.resolve(context)

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val snapshot = LibraryWidgetSnapshotStore(context).read() ?: LibraryWidgetSnapshot()
        shows = snapshot.subscriptions
        episodes = snapshot.newEpisodes
        chrome = WidgetChrome.resolve(context)
    }

    override fun onDestroy() {
        shows = emptyList()
        episodes = emptyList()
    }

    override fun getCount(): Int =
        when (kind) {
            LibraryWidgetKind.SUBSCRIPTIONS -> shows.size
            LibraryWidgetKind.NEW_EPISODES -> episodes.size
        }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.library_widget_list_item)
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_row_bg,
            WidgetPalette.secondaryContainer,
            chrome,
        )

        val deepLink: String
        when (kind) {
            LibraryWidgetKind.SUBSCRIPTIONS -> {
                val row = shows.getOrNull(position) ?: return views
                views.setTextViewText(R.id.widget_row_title, row.title)
                views.setTextViewText(R.id.widget_row_subtitle, row.subtitle)
                WidgetRemoteViewsColors.setTextColor(
                    views,
                    R.id.widget_row_title,
                    WidgetPalette.onSecondaryContainer,
                    chrome,
                )
                WidgetRemoteViewsColors.setTextColor(
                    views,
                    R.id.widget_row_subtitle,
                    WidgetPalette.onSurfaceVariant,
                    chrome,
                )
                views.setViewVisibility(
                    R.id.widget_row_new_dot,
                    if (row.isNew) View.VISIBLE else View.GONE,
                )
                if (row.isNew) {
                    WidgetRemoteViewsColors.setColorFilter(
                        views,
                        R.id.widget_row_new_dot,
                        WidgetPalette.primary,
                        chrome,
                    )
                }
                bindArt(views, row.artworkCachePath)
                deepLink = row.deepLinkUri
            }
            LibraryWidgetKind.NEW_EPISODES -> {
                val row = episodes.getOrNull(position) ?: return views
                views.setTextViewText(R.id.widget_row_title, row.episodeTitle)
                views.setTextViewText(R.id.widget_row_subtitle, row.podcastTitle)
                WidgetRemoteViewsColors.setTextColor(
                    views,
                    R.id.widget_row_title,
                    WidgetPalette.onSecondaryContainer,
                    chrome,
                )
                WidgetRemoteViewsColors.setTextColor(
                    views,
                    R.id.widget_row_subtitle,
                    WidgetPalette.onSurfaceVariant,
                    chrome,
                )
                views.setViewVisibility(R.id.widget_row_new_dot, View.GONE)
                bindArt(views, row.artworkCachePath)
                deepLink = row.deepLinkUri
            }
        }

        val fillIn =
            Intent().apply {
                data = Uri.parse(deepLink)
            }
        views.setOnClickFillInIntent(R.id.widget_row_root, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        when (kind) {
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
    ) {
        val bitmap = cachePath?.let { decodeArt(it) }
        if (bitmap == null) {
            views.setImageViewResource(R.id.widget_row_art, R.drawable.widget_art_placeholder)
        } else {
            views.setImageViewBitmap(R.id.widget_row_art, bitmap)
        }
    }

    private fun decodeArt(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val target = 80
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        val source =
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return null
        val ratio = source.width.toFloat() / source.height
        val crop =
            if (ratio > 1f) {
                val cropWidth = source.height
                val left = (source.width - cropWidth) / 2
                Rect(left, 0, left + cropWidth, source.height)
            } else {
                val cropHeight = source.width
                val top = (source.height - cropHeight) / 2
                Rect(0, top, source.width, top + cropHeight)
            }
        return Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888).also { out ->
            Canvas(out).drawBitmap(source, crop, Rect(0, 0, target, target), null)
            source.recycle()
        }
    }
}
