package cx.aswin.boxlore.feature.widgets.actions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import cx.aswin.boxlore.feature.widgets.WidgetControl

object WidgetActionIntents {
    const val ACTION_WIDGET_CONTROL = "cx.aswin.boxlore.feature.widgets.ACTION_CONTROL"
    const val EXTRA_APP_WIDGET_ID = "extra_app_widget_id"
    const val EXTRA_CONTROL = "extra_control"

    fun broadcast(
        context: Context,
        appWidgetId: Int,
        control: WidgetControl,
    ): PendingIntent {
        val intent =
            Intent(context, WidgetControlReceiver::class.java).apply {
                action = ACTION_WIDGET_CONTROL
                putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
                putExtra(EXTRA_CONTROL, control.name)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode(appWidgetId, control),
            intent,
            pendingIntentFlags(),
        )
    }

    fun openApp(context: Context): PendingIntent {
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("boxlore://home"))
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            launch,
            pendingIntentFlags(),
        )
    }

    fun openDeepLink(
        context: Context,
        uri: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            pendingIntentFlags(),
        )
    }

    fun parseControl(intent: Intent): WidgetControl? =
        intent.getStringExtra(EXTRA_CONTROL)?.let { runCatching { WidgetControl.valueOf(it) }.getOrNull() }

    private fun requestCode(
        appWidgetId: Int,
        control: WidgetControl,
    ): Int = appWidgetId * 10 + control.ordinal

    private fun pendingIntentFlags(): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            base or PendingIntent.FLAG_IMMUTABLE
        } else {
            base
        }
    }

    private const val OPEN_APP_REQUEST_CODE = 50_001
}
