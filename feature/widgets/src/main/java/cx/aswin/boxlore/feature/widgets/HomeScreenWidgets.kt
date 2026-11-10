package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.StringRes

/**
 * The widgets a listener can see and add to the home screen from inside the app. The home screen app
 * does the adding and confirms it with the listener itself; not every home screen app can.
 */
object HomeScreenWidgets {
    /** One widget, named as the widget picker names it, with the size its preview is drawn for. */
    class Entry internal constructor(
        val key: String,
        @param:StringRes val label: Int,
        @param:StringRes val description: Int,
        val previewWidthDp: Int,
        val previewHeightDp: Int,
        /** Whether this widget draws show covers the way the Your Shows cover setting says. */
        val usesShowsCoverStyle: Boolean,
        internal val receiver: Class<out AppWidgetProvider>,
    )

    val all: List<Entry> =
        listOf(
            entry("full", R.string.now_playing_widget_label, R.string.now_playing_widget_description),
            entry("compact", R.string.now_playing_bar_widget_label, R.string.now_playing_bar_widget_description),
            entry("round", R.string.playback_controls_widget_label, R.string.playback_controls_widget_description),
            entry("round_next", R.string.playback_next_controls_widget_label, R.string.playback_next_controls_widget_description),
            entry("shows", R.string.subscriptions_widget_label, R.string.subscriptions_widget_description, usesShowsCoverStyle = true),
            entry("new_episodes", R.string.new_episodes_widget_label, R.string.new_episodes_widget_description),
        )

    /** Whether the current home screen app adds a widget when an app asks it to. */
    fun canAdd(context: Context): Boolean = runCatching { manager(context)?.isRequestPinAppWidgetSupported == true }.getOrDefault(false)

    /**
     * Asks the home screen app to add the widget with this [key]. Returns false when the key is
     * unknown or the home screen app turned the request down; it never throws.
     */
    fun requestAdd(context: Context, key: String): Boolean {
        val entry = all.firstOrNull { it.key == key } ?: return false
        return runCatching {
            manager(context)?.requestPinAppWidget(ComponentName(context.applicationContext, entry.receiver), null, null) == true
        }.getOrDefault(false)
    }

    /**
     * The widget with this [key] as its own renderer draws it, with sample content and in the
     * listener's widget colours. Lay it out at its entry's preview size, then scale that picture to
     * fit: at that size every row is whole. It is a picture: show it where its buttons cannot be
     * tapped. Cuts and caches artwork, so call it off the main thread. Null when the key is unknown
     * or the preview cannot be drawn.
     */
    fun preview(context: Context, key: String): RemoteViews? {
        val preview = WidgetPickerPreviews.previews().firstOrNull { it.key == key } ?: return null
        return runCatching { preview.build(context.applicationContext) }.getOrNull()
    }

    private fun entry(
        key: String,
        @StringRes label: Int,
        @StringRes description: Int,
        usesShowsCoverStyle: Boolean = false,
    ): Entry {
        val preview = WidgetPickerPreviews.previews().first { it.key == key }
        return Entry(key, label, description, preview.size.widthDp, preview.size.heightDp, usesShowsCoverStyle, preview.receiver)
    }

    private fun manager(context: Context): AppWidgetManager? = context.applicationContext.getSystemService(AppWidgetManager::class.java)
}
