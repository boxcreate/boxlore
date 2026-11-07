package cx.aswin.boxlore.core.playback.service

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Media3's playback notification, with one guard. When a notification's artwork finishes loading,
 * Media3 (1.10.1) rebuilds the notification and starts the playback service in the foreground from
 * that late callback, without catching Android 12+'s ForegroundServiceStartNotAllowedException: only
 * its first update is guarded. In the background that crashed the app. Here the late callback runs
 * on the main thread ([runOnMain]), where Media3 handles it straight away, so a refused start is
 * caught and the notification goes to [onRefused] instead.
 *
 * [onRefused] must not throw: it runs inside the task posted to the main thread, where nothing
 * catches it. The service's handler wraps its work in `runCatching` for that reason.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class ForegroundSafeNotificationProvider(
    private val delegate: MediaNotification.Provider,
    private val runOnMain: (Runnable) -> Unit,
    private val onRefused: (MediaNotification) -> Unit,
) : MediaNotification.Provider {
    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification = delegate.createNotification(mediaSession, mediaButtonPreferences, actionFactory) { notification ->
        runOnMain(Runnable { deliver(onNotificationChangedCallback, notification) })
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo = delegate.notificationChannelInfo

    private fun deliver(callback: MediaNotification.Provider.Callback, notification: MediaNotification) {
        try {
            callback.onNotificationChanged(notification)
        } catch (e: IllegalStateException) {
            if (!isForegroundStartRefusal(e)) throw e
            Log.w(TAG, "Android refused to start playback in the foreground; showing its notification without it", e)
            onRefused(notification)
        }
    }

    private companion object {
        const val TAG = "ForegroundSafeNotification"
    }
}

/** Whether [error] is Android 12+ refusing to start a service in the foreground from the background. */
internal fun isForegroundStartRefusal(error: Throwable): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && error is ForegroundServiceStartNotAllowedException
