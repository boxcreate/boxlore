package cx.aswin.boxlore.ui.announcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.playback.CarSignals
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.util.isInstalledFromPlayStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shows an install that didn't come from Google Play, once, how to put boxlore on the car's screen.
 * Android Auto hides such installs in a car until its Unknown sources setting is on
 * ([the guide][GUIDE_URL]).
 *
 * - [Trigger.APP_OPENED]: boxlore opened on the phone while it shows on a car's screen. The full-screen
 *   alert appears straight away.
 * - [Trigger.DRIVE_STARTED]: the player was running when a drive started. A notification opens the
 *   guide, and the alert waits for the next time boxlore opens.
 *
 * Never for a Play Store install, and never once Android Auto has opened boxlore's lists during a
 * drive ([carScreensOpened]), which means boxlore is already on the car's screen. The alert reuses the
 * announcement slot, so it waits rather than replacing an announcement that hasn't been seen.
 */
internal class AndroidAutoTip(
    private val store: Store,
    private val isPlayInstall: () -> Boolean,
    private val isInCar: () -> Boolean,
    private val hasWaitingAnnouncement: suspend () -> Boolean,
    private val saveAlert: suspend () -> Unit,
    private val postNotification: () -> Boolean,
) {
    enum class Trigger { APP_OPENED, DRIVE_STARTED }

    /** What the tip remembers for this install. */
    interface Store {
        fun shown(): Boolean

        fun markShown()

        fun seenInCar(): Boolean

        fun markSeenInCar()
    }

    private val lock = Mutex()

    /** Shows the tip if this install needs it now. Call off the main thread. */
    suspend fun maybeShow(trigger: Trigger) = lock.withLock {
        if (store.shown() || store.seenInCar()) return@withLock
        if (isPlayInstall() || !isInCar()) return@withLock
        val alertSaved = !hasWaitingAnnouncement()
        if (alertSaved) saveAlert()
        val notified = trigger == Trigger.DRIVE_STARTED && postNotification()
        // Nothing reached the listener: a later drive or launch tries again.
        if (alertSaved || notified) store.markShown()
    }

    /** Android Auto opened boxlore's lists during a drive: boxlore is on the car's screen. */
    fun carScreensOpened() = store.markSeenInCar()

    companion object {
        const val GUIDE_URL = "https://github.com/boxcreate/boxlore/blob/master/docs/ANDROID_AUTO.md"

        /** The announcement category that picks the tip layout ([AnnouncementLayout.Tip]). */
        const val CATEGORY = "TIP"

        private const val PREFS = "boxlore_android_auto_tip"
        private const val KEY_SHOWN = "shown"
        private const val KEY_SEEN_IN_CAR = "seen_in_car"
        private const val CHANNEL_ID = "boxlore_announcements_v2"
        private const val NOTIFICATION_ID = 7_301
        private const val TAG = "AndroidAutoTip"

        fun create(context: Context, userPreferences: UserPreferencesRepository): AndroidAutoTip {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AndroidAutoTip(
                store =
                    object : Store {
                        override fun shown() = prefs.getBoolean(KEY_SHOWN, false)

                        override fun markShown() = prefs.edit().putBoolean(KEY_SHOWN, true).apply()

                        override fun seenInCar() = prefs.getBoolean(KEY_SEEN_IN_CAR, false)

                        override fun markSeenInCar() = prefs.edit().putBoolean(KEY_SEEN_IN_CAR, true).apply()
                    },
                isPlayInstall = { app.isInstalledFromPlayStore() },
                isInCar = { CarSignals.isInCar(app) },
                hasWaitingAnnouncement = { userPreferences.activeAnnouncementStream.first() != null },
                saveAlert = { userPreferences.setAnnouncement(alert(app)) },
                postNotification = { postNotification(app) },
            )
        }

        private fun alert(context: Context) = UserPreferencesRepository.Announcement(
            title = context.getString(R.string.android_auto_tip_title),
            body = context.getString(R.string.android_auto_tip_body),
            route = GUIDE_URL,
            imageUrl = null,
            actionLabel = context.getString(R.string.android_auto_tip_action),
            showActionInApp = true,
            timestamp = System.currentTimeMillis(),
            category = CATEGORY,
            presentation = "fullscreen",
        )

        /** Posts the notification that opens the guide; false when notifications are off or posting fails. */
        private fun postNotification(context: Context): Boolean = try {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager == null || !manager.areNotificationsEnabled()) {
                false
            } else {
                ensureChannel(context, manager)
                val open =
                    PendingIntent.getActivity(
                        context,
                        NOTIFICATION_ID,
                        Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                val notification =
                    NotificationCompat
                        .Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_custom)
                        .setContentTitle(context.getString(R.string.android_auto_tip_notification_title))
                        .setContentText(context.getString(R.string.android_auto_tip_notification_text))
                        .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.android_auto_tip_notification_text)))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                manager.notify(NOTIFICATION_ID, notification)
                true
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to post the Android Auto tip", e)
            false
        }

        /** The Announcements channel, created as boxlore's push announcements create it when they haven't yet. */
        private fun ensureChannel(context: Context, manager: NotificationManager) {
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val sound = Uri.parse("android.resource://${context.packageName}/raw/boxlore_announcement_chime")
            val attributes =
                AudioAttributes
                    .Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Announcements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "boxlore news and updates"
                    setSound(sound, attributes)
                },
            )
        }
    }
}
