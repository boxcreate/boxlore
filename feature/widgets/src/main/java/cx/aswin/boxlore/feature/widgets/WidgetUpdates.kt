package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.util.Log
import android.widget.RemoteViews

private const val TAG = "WidgetUpdates"

/**
 * Pushes [views] to the launcher. From Android 17 (apps targeting API 37), an update whose images go
 * over the launcher's bitmap memory cap throws instead of being logged. Widget updates run inside the
 * player service, so the widget keeps what it showed rather than taking playback down with it.
 */
internal fun AppWidgetManager.pushWidgetUpdate(appWidgetId: Int, views: RemoteViews): Boolean =
    pushWidgetUpdate(appWidgetId, views, ::updateAppWidget)

internal fun <V> pushWidgetUpdate(appWidgetId: Int, views: V, update: (Int, V) -> Unit): Boolean = try {
    update(appWidgetId, views)
    true
} catch (error: IllegalArgumentException) {
    Log.w(TAG, "Widget $appWidgetId kept its last update: ${error.message}")
    false
}
