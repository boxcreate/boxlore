package cx.aswin.boxlore.core.playback.service

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import androidx.media3.session.MediaSession
import cx.aswin.boxlore.core.analytics.PlaybackListenValues
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseControllerMatcher

/**
 * What asked to play (`controlled_by` on a listen): boxlore's own screens, the car, the media
 * notification or system media controls, or anything else (headset buttons, a watch, the Assistant).
 * Android Auto counts as a controller here, never as where the sound goes.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PlaybackControllerKind {
    fun of(session: MediaSession, controller: MediaSession.ControllerInfo, ownPackage: String,): String =
        classify(
            isCar = AutoBrowseControllerMatcher.isAndroidAuto(session, controller),
            isNotification = runCatching { session.isMediaNotificationController(controller) }.getOrDefault(false),
            packageName = controller.packageName,
            ownPackage = ownPackage,
        )

    fun classify(isCar: Boolean, isNotification: Boolean, packageName: String, ownPackage: String,): String = when {
        isCar -> PlaybackListenValues.CONTROL_CAR
        isNotification || packageName == SYSTEM_UI_PACKAGE -> PlaybackListenValues.CONTROL_NOTIFICATION
        packageName == ownPackage -> PlaybackListenValues.CONTROL_APP
        else -> PlaybackListenValues.CONTROL_OTHER
    }

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}

/**
 * Whether the phone is really in a car. Android Auto's app also connects in the background without
 * a car (to list media apps), so a connection alone doesn't say so.
 *
 * Three signals count: Android Automotive (the car itself), the system's car mode, and Android
 * Auto's own connection state. Current Android Auto no longer puts the phone in car mode while it
 * projects to a car's screen, so car mode alone misses those drives.
 */
internal object CarConnectionState {
    private const val CONNECTION_AUTHORITY = "androidx.car.app.connection"
    private const val CONNECTION_COLUMN = "CarConnectionState"
    private const val CONNECTION_PROJECTION = 2

    fun isInCar(context: Context): Boolean = runCatching {
        isInCar(
            isCarMode = context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR,
            isAutomotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
            isProjecting = isProjecting(context),
        )
    }.getOrDefault(false)

    fun isInCar(isCarMode: Boolean, isAutomotive: Boolean, isProjecting: Boolean = false,): Boolean = isCarMode || isAutomotive || isProjecting

    /** What Android Auto reports through the car connection provider: true only while it projects to a car's screen. */
    private fun isProjecting(context: Context): Boolean = runCatching {
        val uri = Uri.Builder().scheme("content").authority(CONNECTION_AUTHORITY).build()
        context.contentResolver.query(uri, arrayOf(CONNECTION_COLUMN), null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(CONNECTION_COLUMN)) == CONNECTION_PROJECTION
        } ?: false
    }.getOrDefault(false)
}
