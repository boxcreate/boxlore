package cx.aswin.boxlore.feature.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import cx.aswin.boxlore.feature.widgets.actions.WidgetActionIntents

/**
 * Draws the frame of the two library widgets: the tinted block, the count badge, the header and the
 * resting state. [LibraryWidgetRemoteViewsFactory] supplies the rows and tiles.
 */
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
        if (appWidgetManager.pushWidgetUpdate(appWidgetId, views)) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
        }
    }

    /** The roles one library widget is painted with. */
    internal class Palette(
        @ColorRes val container: Int,
        @ColorRes val onContainer: Int,
        @ColorRes val accent: Int,
        @ColorRes val onAccent: Int,
        @ColorRes val button: Int,
        @ColorRes val onButton: Int,
    )

    internal fun palette(kind: LibraryWidgetKind): Palette = when (kind) {
        // New Episodes is the seafoam block with white pills; Your Shows stays neutral so covers stand out.
        LibraryWidgetKind.NEW_EPISODES ->
            Palette(
                container = WidgetPalette.tertiaryContainer,
                onContainer = WidgetPalette.onTertiaryContainer,
                accent = WidgetPalette.tertiary,
                onAccent = WidgetPalette.onTertiary,
                button = WidgetPalette.surface,
                onButton = WidgetPalette.onSurface,
            )
        LibraryWidgetKind.SUBSCRIPTIONS ->
            Palette(
                container = WidgetPalette.surface,
                onContainer = WidgetPalette.onSurface,
                accent = WidgetPalette.primary,
                onAccent = WidgetPalette.onPrimary,
                button = WidgetPalette.primaryContainer,
                onButton = WidgetPalette.onPrimaryContainer,
            )
    }

    fun build(
        context: Context,
        appWidgetId: Int,
        snapshot: LibraryWidgetSnapshot,
        kind: LibraryWidgetKind,
        collection: RemoteViews.RemoteCollectionItems? = null,
    ): RemoteViews {
        val layout =
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> R.layout.library_widget_grid
                LibraryWidgetKind.NEW_EPISODES -> R.layout.library_widget_list
            }
        val views = RemoteViews(context.packageName, layout)
        val chrome = WidgetChrome.resolve(context)
        val palette = palette(kind)
        chrome.setColorFilter(views, R.id.widget_surface_background, palette.container)
        chrome.setTextColor(views, R.id.widget_list_header, palette.onContainer)

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

        val rows =
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> snapshot.subscriptions.size
                LibraryWidgetKind.NEW_EPISODES -> snapshot.newEpisodes.size
            }
        if (collection != null) {
            // A picker preview carries its rows with it; a placed widget binds the list service.
            views.setRemoteAdapter(R.id.widget_list_view, collection)
        } else {
            views.setRemoteAdapter(
                R.id.widget_list_view,
                LibraryWidgetRemoteViewsService.adapterIntent(context, appWidgetId, kind),
            )
        }
        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_container)
        val template = collectionClickTemplate(context, appWidgetId)
        if (template != null) {
            views.setPendingIntentTemplate(
                R.id.widget_list_view,
                template,
            )
        }

        if (rows == 0) {
            bindEmpty(context, views, chrome, appWidgetId, kind, palette, openListUri)
        } else {
            bindPopulated(context, views, chrome, appWidgetId, snapshot.count(kind), palette, openListUri)
        }
        return views
    }

    @Suppress("LongParameterList")
    private fun bindPopulated(
        context: Context,
        views: RemoteViews,
        chrome: WidgetChrome,
        appWidgetId: Int,
        count: Int,
        palette: Palette,
        openListUri: String,
    ) {
        views.setViewVisibility(R.id.widget_empty_container, View.GONE)
        views.setViewVisibility(R.id.widget_count_chip, View.VISIBLE)
        views.setTextViewText(R.id.widget_count_chip_text, count.toString())
        views.setTextViewTextSize(
            R.id.widget_count_chip_text,
            TypedValue.COMPLEX_UNIT_SP,
            if (count >= THREE_DIGITS) COUNT_SP_LONG else COUNT_SP,
        )
        chrome.setColorFilter(views, R.id.widget_count_chip_bg, palette.accent)
        chrome.setTextColor(views, R.id.widget_count_chip_text, palette.onAccent)

        // The arrow replaces the old "View all" footer, so the whole body is content.
        views.setViewVisibility(R.id.widget_open_list, View.VISIBLE)
        chrome.setColorFilter(views, R.id.widget_open_list_background, palette.button)
        chrome.setColorFilter(views, R.id.widget_open_list_icon, palette.onButton)
        val open =
            WidgetActionIntents.openDeepLink(
                context,
                openListUri,
                requestCode(appWidgetId, FOOTER_REQUEST),
            )
        views.setOnClickPendingIntent(R.id.widget_open_list, open)
        views.setOnClickPendingIntent(R.id.widget_count_chip, open)
    }

    internal fun collectionClickTemplate(
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

    @Suppress("LongParameterList")
    private fun bindEmpty(
        context: Context,
        views: RemoteViews,
        chrome: WidgetChrome,
        appWidgetId: Int,
        kind: LibraryWidgetKind,
        palette: Palette,
        uri: String,
    ) {
        views.setViewVisibility(R.id.widget_count_chip, View.GONE)
        views.setViewVisibility(R.id.widget_open_list, View.GONE)
        views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
        val titleRes =
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> R.string.subscriptions_widget_empty_title
                LibraryWidgetKind.NEW_EPISODES -> R.string.new_episodes_widget_empty_title
            }
        val ctaRes =
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> R.string.subscriptions_widget_empty_cta
                LibraryWidgetKind.NEW_EPISODES -> R.string.new_episodes_widget_empty_cta
            }
        views.setTextViewText(R.id.widget_empty_title, context.getString(titleRes))
        views.setTextViewText(R.id.widget_empty_cta, context.getString(ctaRes))
        views.setImageViewResource(
            R.id.widget_empty_icon,
            when (kind) {
                LibraryWidgetKind.SUBSCRIPTIONS -> cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_app_mark
                LibraryWidgetKind.NEW_EPISODES -> R.drawable.ic_widget_caught_up
            },
        )
        chrome.setColorFilter(views, R.id.widget_empty_mark_background, palette.accent)
        chrome.setColorFilter(views, R.id.widget_empty_icon, palette.onAccent)
        chrome.setTextColor(views, R.id.widget_empty_title, palette.onContainer)
        chrome.setColorFilter(views, R.id.widget_empty_cta_bg, palette.button)
        chrome.setTextColor(views, R.id.widget_empty_cta, palette.onButton)
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

    fun cancelAll(context: Context, appWidgetId: Int) {
        val tabFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
        val templateFlags =
            PendingIntent.FLAG_NO_CREATE or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
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
                templateFlags,
            )?.cancel()
        } catch (e: Exception) {
            // Ignore cancel failure
        }

        val uris = listOf(SUBSCRIPTIONS_TAB_URI, NEW_EPISODES_TAB_URI)
        val slots = listOf(HEADER_REQUEST, ROOT_REQUEST, FOOTER_REQUEST, EMPTY_REQUEST)
        for (uri in uris) {
            for (slot in slots) {
                cancelTabIntent(context, appWidgetId, uri, slot, tabFlags)
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

    internal fun requestCode(
        appWidgetId: Int,
        slot: Int,
    ): Int = (appWidgetId * 31 + slot) and 0x7fffffff

    internal const val HEADER_REQUEST = 10
    internal const val ROOT_REQUEST = 11
    internal const val EMPTY_REQUEST = 12

    /** The header arrow. It keeps the slot of the footer it replaced, so old pending intents still cancel. */
    internal const val FOOTER_REQUEST = 13
    internal const val TEMPLATE_REQUEST = 14

    private const val SUBSCRIPTIONS_TAB_URI = "boxlore://library/subscriptions?tab=0"
    private const val NEW_EPISODES_TAB_URI = "boxlore://library/subscriptions?tab=1"
    private const val THREE_DIGITS = 100
    private const val COUNT_SP = 19f
    private const val COUNT_SP_LONG = 15f
}
