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
data class InstallOrigin(val installer: String?, val initiator: String?)

/** Reads [InstallOrigin] over binder: call it off the main thread. Unknown when Android can't answer. */
fun Context.installOrigin(): InstallOrigin = try {
    val info = packageManager.getInstallSourceInfo(packageName)
    InstallOrigin(installer = info.installingPackageName, initiator = info.initiatingPackageName)
} catch (e: Exception) {
    Log.w(TAG, "Failed to resolve install source", e)
    InstallOrigin(installer = null, initiator = null)
}

/** True when this app install was delivered by the Google Play Store. */
fun Context.isInstalledFromPlayStore(): Boolean = installOrigin().installer == PLAY_STORE_PACKAGE
