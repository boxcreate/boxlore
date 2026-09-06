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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
            AnalyticsHelper.trackNotificationPermissionDecided(granted)
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
        modifier = Modifier.fillMaxWidth(),
        shape = ImportCorner,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enable notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "This backup includes shows with alerts or auto-downloads. " +
                    "Allow notifications so they can keep working in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    if (canRequestRuntimePermission) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onOpenSettings()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (canRequestRuntimePermission) {
                        "Enable notifications"
                    } else {
                        "Enable in settings"
                    },
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = GoogleSansWeight.semiBold,
                )
            }
        }
    }
}
