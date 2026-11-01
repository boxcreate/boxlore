package cx.aswin.boxlore.feature.settings.feedback

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * The "turn on notifications" ask after sending feedback. Same rules as the show page and import
 * card: ask Android only when it will still show its prompt, otherwise open the app's notification
 * settings.
 */
internal fun areAppNotificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Whether Android will still show its notification prompt (pure; tested). */
internal fun canAskForNotifications(sdkInt: Int, granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): Boolean {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU || granted) return false
    return !askedBefore || shouldShowRationale
}

internal fun canAskForNotifications(context: Context, askedBefore: Boolean): Boolean {
    val sdk = Build.VERSION.SDK_INT
    val granted = sdk >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val activity = context.findActivity()
    val rationale = activity != null && sdk >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    return canAskForNotifications(sdk, granted, askedBefore, rationale)
}

internal fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
