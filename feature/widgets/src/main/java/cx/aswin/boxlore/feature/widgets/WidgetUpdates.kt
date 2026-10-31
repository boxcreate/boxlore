package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.util.Log
import android.widget.RemoteViews

private const val TAG = "WidgetUpdates"

/**
 * Pushes [views] to the launcher. From Android 17 (apps targeting API 37), an update whose images go
 * over the launcher's bitmap memory cap throws, and the system drops the views it held for the widget.
 * Widget updates run inside the player service, so this never throws: it sends [lighter] (the same
 * widget with fewer images) instead, and if that is rejected too the widget waits for the next update.
 */
internal fun AppWidgetManager.pushWidgetUpdate(
    appWidgetId: Int,
    views: RemoteViews,
    lighter: (() -> RemoteViews)? = null,
): Boolean = pushWidgetUpdate(appWidgetId, views, lighter, ::updateAppWidget)

internal fun <V> pushWidgetUpdate(
    appWidgetId: Int,
    views: V,
    lighter: (() -> V)?,
    update: (Int, V) -> Unit,
): Boolean {
    if (tryUpdate(appWidgetId, views, update)) return true
    val fallback = lighter?.invoke() ?: return false
    return tryUpdate(appWidgetId, fallback, update)
}

private fun <V> tryUpdate(appWidgetId: Int, views: V, update: (Int, V) -> Unit): Boolean = try {
    update(appWidgetId, views)
    true
} catch (error: IllegalArgumentException) {
    Log.w(TAG, "Widget $appWidgetId update rejected: ${error.message}")
    false
}
