package cx.aswin.boxlore.debug

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import cx.aswin.boxlore.R

/** ADB-only notification preview; omitted from release builds. */
class TestNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.debug_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_custom)
                .setContentTitle(context.getString(R.string.debug_notification_title))
                .setContentText(context.getString(R.string.debug_notification_body))
                .setAutoCancel(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            notification.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        manager.notify(NOTIFICATION_ID, notification.build())
    }

    private companion object {
        const val ACTION = "cx.aswin.boxlore.DEBUG_TEST_NOTIFICATION"
        const val CHANNEL = "debug_notification_preview"
        const val NOTIFICATION_ID = 90001
    }
}
