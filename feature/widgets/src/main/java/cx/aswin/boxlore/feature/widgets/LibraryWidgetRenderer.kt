package cx.aswin.boxlore.feature.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import cx.aswin.boxlore.feature.widgets.actions.WidgetActionIntents

object LibraryWidgetRenderer {
    fun updateAll(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        snapshot: LibraryWidgetSnapshot,
        kind: LibraryWidgetKind,
    ) {
        appWidgetIds.forEach { id ->
            updateOne(context, appWidgetManager, id, snapshot, kind)
        }
    }

    fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: LibraryWidgetSnapshot,
        kind: LibraryWidgetKind,
    ) {
        val views = build(context, appWidgetId, snapshot, kind)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
    }

    fun build(
        context: Context,
        appWidgetId: Int,
        snapshot: LibraryWidgetSnapshot,
        kind: LibraryWidgetKind,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.library_widget_list)
        val chrome = WidgetChrome.resolve(context)
        applyChrome(views, chrome)

        val headerRes =
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> R.string.subscriptions_widget_header
                LibraryWidgetKind.NEW_EPISODES -> R.string.new_episodes_widget_header
            }
        views.setTextViewText(R.id.widget_list_header, context.getString(headerRes))

        val openListUri =
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> SUBSCRIPTIONS_TAB_URI
                LibraryWidgetKind.NEW_EPISODES -> NEW_EPISODES_TAB_URI
            }
        val openList =
            WidgetActionIntents.openDeepLink(
                context,
                openListUri,
                requestCode(appWidgetId, HEADER_REQUEST),
            )
        views.setOnClickPendingIntent(R.id.widget_header_row, openList)
        views.setOnClickPendingIntent(R.id.widget_list_header, openList)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            WidgetActionIntents.openDeepLink(
                context,
                openListUri,
                requestCode(appWidgetId, ROOT_REQUEST),
            ),
        )

        val count =
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> snapshot.subscriptions.size
                LibraryWidgetKind.NEW_EPISODES -> snapshot.newEpisodes.size
            }

        views.setRemoteAdapter(
            R.id.widget_list_view,
            LibraryWidgetRemoteViewsService.adapterIntent(context, appWidgetId, kind),
        )
        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_container)
        val template = collectionClickTemplate(context, appWidgetId)
        if (template != null) {
            views.setPendingIntentTemplate(
                R.id.widget_list_view,
                template,
            )
        }

        if (count == 0) {
            bindEmpty(
                context,
                views,
                chrome,
                appWidgetId,
                when (kind) {
                    LibraryWidgetKind.SUBSCRIPTIONS -> R.string.subscriptions_widget_empty_title
                    LibraryWidgetKind.NEW_EPISODES -> R.string.new_episodes_widget_empty_title
                },
                when (kind) {
                    LibraryWidgetKind.SUBSCRIPTIONS -> R.string.subscriptions_widget_empty_cta
                    LibraryWidgetKind.NEW_EPISODES -> R.string.new_episodes_widget_empty_cta
                },
                openListUri,
            )
        } else {
            bindPopulated(context, views, chrome, appWidgetId, count, openListUri)
        }

        return views
    }

    private fun bindPopulated(
        context: Context,
        views: RemoteViews,
        chrome: WidgetChrome,
        appWidgetId: Int,
        count: Int,
        openListUri: String,
    ) {
        views.setViewVisibility(R.id.widget_count_chip, View.VISIBLE)
        views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
        views.setTextViewText(R.id.widget_count_chip_text, count.toString())
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_count_chip_text,
            WidgetPalette.onSecondaryContainer,
            chrome,
        )
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_count_chip_bg,
            WidgetPalette.secondaryContainer,
            chrome,
        )
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_footer_bg,
            WidgetPalette.secondaryContainer,
            chrome,
        )
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_footer_text,
            WidgetPalette.onSecondaryContainer,
            chrome,
        )
        val open =
            WidgetActionIntents.openDeepLink(
                context,
                openListUri,
                requestCode(appWidgetId, FOOTER_REQUEST),
            )
        views.setOnClickPendingIntent(R.id.widget_footer, open)
        views.setOnClickPendingIntent(R.id.widget_footer_text, open)
        views.setOnClickPendingIntent(R.id.widget_count_chip, open)
        views.setViewVisibility(R.id.widget_empty_container, View.GONE)
    }

    private fun collectionClickTemplate(
        context: Context,
        appWidgetId: Int,
    ): PendingIntent? {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
        return try {
            PendingIntent.getActivity(
                context,
                requestCode(appWidgetId, TEMPLATE_REQUEST),
                intent,
                flags,
            )
        } catch (e: SecurityException) {
            android.util.Log.w(
                "LibraryWidgetRenderer",
                "Failed to create collectionClickTemplate PendingIntent",
                e,
            )
            null
        }
    }

    private fun bindEmpty(
        context: Context,
        views: RemoteViews,
        chrome: WidgetChrome,
        appWidgetId: Int,
        titleRes: Int,
        ctaRes: Int,
        uri: String,
    ) {
        views.setViewVisibility(R.id.widget_footer, View.GONE)
        views.setViewVisibility(R.id.widget_count_chip, View.GONE)
        views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
        views.setTextViewText(R.id.widget_empty_title, context.getString(titleRes))
        views.setTextViewText(R.id.widget_empty_cta, context.getString(ctaRes))
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_empty_title,
            WidgetPalette.onSurface,
            chrome,
        )
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_empty_cta,
            WidgetPalette.onSecondaryContainer,
            chrome,
        )
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_empty_cta_bg,
            WidgetPalette.secondaryContainer,
            chrome,
        )
        val open =
            WidgetActionIntents.openDeepLink(
                context,
                uri,
                requestCode(appWidgetId, EMPTY_REQUEST),
            )
        views.setOnClickPendingIntent(R.id.widget_empty_container, open)
        views.setOnClickPendingIntent(R.id.widget_empty_cta_chip, open)
        views.setOnClickPendingIntent(R.id.widget_empty_cta, open)
    }

    private fun applyChrome(
        views: RemoteViews,
        chrome: WidgetChrome,
    ) {
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_surface_background,
            WidgetPalette.surface,
            chrome,
        )
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_list_header,
            WidgetPalette.onSurface,
            chrome,
        )
    }

    fun cancelAll(context: Context, appWidgetId: Int) {
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
        try {
            val templateIntent =
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage(context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            PendingIntent.getActivity(
                context,
                requestCode(appWidgetId, TEMPLATE_REQUEST),
                templateIntent,
                flags,
            )?.cancel()
        } catch (e: Exception) {
            // Ignore cancel failure
        }

        val uris = listOf(SUBSCRIPTIONS_TAB_URI, NEW_EPISODES_TAB_URI)
        val slots = listOf(HEADER_REQUEST, ROOT_REQUEST, FOOTER_REQUEST, EMPTY_REQUEST)
        for (uri in uris) {
            for (slot in slots) {
                cancelTabIntent(context, appWidgetId, uri, slot, flags)
            }
        }
    }

    private fun cancelTabIntent(
        context: Context,
        appWidgetId: Int,
        uri: String,
        slot: Int,
        flags: Int,
    ) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    setPackage(context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            PendingIntent.getActivity(
                context,
                requestCode(appWidgetId, slot),
                intent,
                flags,
            )?.cancel()
        } catch (e: Exception) {
            // Ignore cancel failure
        }
    }

    private fun requestCode(
        appWidgetId: Int,
        slot: Int,
    ): Int = (appWidgetId * 31 + slot) and 0x7fffffff

    private const val HEADER_REQUEST = 10
    private const val ROOT_REQUEST = 11
    private const val EMPTY_REQUEST = 12
    private const val FOOTER_REQUEST = 13
    private const val TEMPLATE_REQUEST = 14
    private const val SUBSCRIPTIONS_TAB_URI = "boxlore://library/subscriptions?tab=0"
    private const val NEW_EPISODES_TAB_URI = "boxlore://library/subscriptions?tab=1"
}
