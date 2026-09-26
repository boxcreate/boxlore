package cx.aswin.boxlore.core.playback.service.auto

import androidx.media3.session.MediaSession

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object AutoBrowseControllerMatcher {
    fun isAndroidAuto(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean {
        val isMedia3Auto =
            runCatching {
                session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)
            }.getOrDefault(false)

        return isMedia3Auto ||
            controller.packageName == "com.google.android.projection.gearhead" ||
            controller.packageName == "com.google.android.apps.automotive.media" ||
            controller.packageName == "com.android.car.media"
    }
}
