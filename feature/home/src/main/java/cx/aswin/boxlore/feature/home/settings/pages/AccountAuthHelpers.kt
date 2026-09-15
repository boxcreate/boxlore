package cx.aswin.boxlore.feature.home.settings.pages

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun cleanAccountError(raw: String?, isSignUp: Boolean): String {
    if (raw == null) return "An unexpected error occurred"
    return when {
        raw.contains("user-not-found", ignoreCase = true) || raw.contains("no user", ignoreCase = true) ->
            "No account found with this email. Try signing up instead."
        raw.contains("wrong-password", ignoreCase = true) || raw.contains("invalid-credential", ignoreCase = true) ->
            "Incorrect password or credentials. Please try again."
        raw.contains("email-already-in-use", ignoreCase = true) || raw.contains("already registered", ignoreCase = true) ->
            "This email is already registered. Try signing in instead."
        raw.contains("weak-password", ignoreCase = true) ->
            "Password is too weak. Please use at least 6 characters."
        raw.contains("invalid-email", ignoreCase = true) ->
            "Please enter a valid email address."
        raw.contains("network", ignoreCase = true) ->
            "Network error. Check your connection and try again."
        else -> raw
    }
}

internal fun openGmailOrEmailApp(context: Context) {
    try {
        val gmailIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
        if (gmailIntent != null) {
            gmailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(gmailIntent)
        } else {
            val emailIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_EMAIL)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (emailIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(emailIntent)
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open email app", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun AccountPrivacyCard(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Privacy & Data Protection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Your account is used strictly to sync your library, queue, and playback history across devices. It is completely isolated and never linked to app usage or analytics data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Authentication is handled directly by Firebase (Google). Your email address is never stored on our servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}
