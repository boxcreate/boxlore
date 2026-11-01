package cx.aswin.boxlore.ui.libraryimport

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal fun areAppNotificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

internal fun hasPostNotificationPermission(context: Context): Boolean =
    areAppNotificationsEnabled(context)

internal fun openAppNotificationSettings(context: Context) {
    val intent = Intent().apply {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            else -> {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
        }
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(fallback)
    }
}

/** After a backup restore: shows with alerts or auto-downloads need notifications, one tap to allow them. */
@Composable
internal fun ImportNotificationPermissionCard(
    onPermissionChanged: (Boolean) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    var promptAttempted by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            promptAttempted = true
            cx.aswin.boxlore.core.prefs.BoxcastPrefs(context).setHasRequestedNotificationPermission(true)
            AnalyticsHelper.trackNotificationPermissionDecided(granted, IMPORT_SURFACE)
            onPermissionChanged(areAppNotificationsEnabled(context))
        },
    )

    val canRequestRuntimePermission = Build.VERSION.SDK_INT >= 33 &&
        !promptAttempted &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("import_notifications"),
        shape = ImportCorner,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.import_notify_body),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    if (canRequestRuntimePermission) {
                        AnalyticsHelper.trackNotificationPermissionRequested(IMPORT_SURFACE)
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onOpenSettings()
                    }
                },
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(if (canRequestRuntimePermission) R.string.import_notify_allow else R.string.import_notify_settings),
                    fontWeight = GoogleSansWeight.semiBold,
                )
            }
        }
    }
}

/** `surface` on the permission events: the import card. */
private const val IMPORT_SURFACE = "library_import"
