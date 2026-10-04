package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

/** Do not restore playback or rank library rows for an unused widget family. */
internal fun hasInstalledWidgets(
    context: Context,
    providers: List<Class<out AppWidgetProvider>>,
): Boolean {
    val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
    return providers.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
}
