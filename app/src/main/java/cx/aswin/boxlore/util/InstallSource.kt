package cx.aswin.boxlore.util

import android.content.Context
import android.util.Log

private const val PLAY_STORE_PACKAGE = "com.android.vending"
private const val TAG = "InstallSource"

/**
 * Which app Android says installed this copy ([installer]) and which app asked for the install
 * ([initiator]). Null when Android records none: an install from a computer has no installer and
 * `com.android.shell` as initiator.
 */
data class InstallOrigin(val installer: String?, val initiator: String?) {
    /** Whether the Play Store installed this copy. */
    val fromPlayStore: Boolean get() = installer == PLAY_STORE_PACKAGE
}

/** Reads [InstallOrigin] over binder: call it off the main thread. Null when Android can't answer. */
fun Context.installOrigin(): InstallOrigin? = try {
    val info = packageManager.getInstallSourceInfo(packageName)
    InstallOrigin(installer = info.installingPackageName, initiator = info.initiatingPackageName)
} catch (e: Exception) {
    Log.w(TAG, "Failed to resolve install source", e)
    null
}

/** True when this app install was delivered by the Google Play Store; false when it wasn't or Android can't say. */
fun Context.isInstalledFromPlayStore(): Boolean = installOrigin()?.fromPlayStore == true
