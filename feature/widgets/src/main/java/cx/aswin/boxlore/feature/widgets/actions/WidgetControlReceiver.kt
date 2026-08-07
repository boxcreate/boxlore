package cx.aswin.boxlore.feature.widgets.actions

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One explicit action endpoint shared by every picker-visible widget family. */
class WidgetControlReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != WidgetActionIntents.ACTION_WIDGET_CONTROL) return
        val control = WidgetActionIntents.parseControl(intent) ?: return
        val appWidgetId =
            intent.getIntExtra(
                WidgetActionIntents.EXTRA_APP_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                WidgetActionHandler.handle(control)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
