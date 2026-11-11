package cx.aswin.boxlore.feature.widgets

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Hears from the home screen app that a widget asked for through [HomeScreenWidgets.requestAdd] is
 * now on the home screen, and passes its key on. Not exported: only the system sends it, through the
 * callback made here.
 */
class WidgetAddedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADDED) return
        intent.getStringExtra(EXTRA_KEY)?.let(HomeScreenWidgets::onAdded)
    }

    internal companion object {
        const val ACTION_ADDED = "cx.aswin.boxlore.feature.widgets.action.WIDGET_ADDED"
        const val EXTRA_KEY = "widget_key"

        /** The callback the home screen app sends once it has added the widget with this [key]; null if it cannot be made. */
        fun callback(context: Context, key: String): PendingIntent? = runCatching {
            val intent = Intent(context, WidgetAddedReceiver::class.java).setAction(ACTION_ADDED).putExtra(EXTRA_KEY, key)
            // One callback per widget, replaced by the next request for the same widget.
            PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }.getOrNull()
    }
}
