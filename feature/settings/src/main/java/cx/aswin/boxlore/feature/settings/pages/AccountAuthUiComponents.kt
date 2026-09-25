package cx.aswin.boxlore.feature.settings.pages

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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun showAccountToast(context: Context, message: String) {
    runCatching {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

internal object AccountVerificationStorage {
    private const val PREF_AWAITING_EMAIL_VERIFICATION = "awaiting_email_verification"

    fun getPref(context: Context): Boolean = runCatching {
        context.getSharedPreferences("boxlore_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_AWAITING_EMAIL_VERIFICATION, false)
    }.getOrDefault(false)

    fun setPref(context: Context, awaiting: Boolean) {
        runCatching {
            context.getSharedPreferences("boxlore_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_AWAITING_EMAIL_VERIFICATION, awaiting)
                .apply()
        }
    }
}

internal fun openGmailOrEmailApp(context: Context, email: String = "") {
    val trimmed = email.trim().lowercase()
    val isGmail = trimmed.endsWith("@gmail.com") || trimmed.endsWith("@googlemail.com")

    if (isGmail) {
        if (launchGmailAppOrWeb(context)) return
    } else {
        if (launchDefaultEmailApp(context) || launchDomainWebmail(context, trimmed)) return
    }

    if (!launchFallbackEmail(context)) {
        showAccountToast(context, "Could not open email app")
    }
}

private fun launchGmailAppOrWeb(context: Context): Boolean {
    val gmail = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
    if (gmail != null && tryLaunchIntent(context, gmail)) return true
    return tryLaunchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")))
}

private fun launchDefaultEmailApp(context: Context): Boolean {
    val emailIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_EMAIL)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return tryLaunchIntent(context, emailIntent)
}

private fun launchDomainWebmail(context: Context, email: String): Boolean {
    val domainWebmail = resolveWebmailUrl(email) ?: return false
    return tryLaunchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(domainWebmail)))
}

private fun launchFallbackEmail(context: Context): Boolean {
    val mailtoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryLaunchIntent(context, mailtoIntent)) {
        return true
    }
    val anyGmail = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
    return anyGmail != null && tryLaunchIntent(context, anyGmail)
}

private fun tryLaunchIntent(context: Context, intent: Intent?): Boolean {
    if (intent == null) return false
    return runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private fun resolveWebmailUrl(email: String): String? = when {
    email.endsWith("@outlook.com") || email.endsWith("@hotmail.com") || email.endsWith("@live.com") ->
        "https://outlook.live.com"
    email.endsWith("@yahoo.com") ->
        "https://mail.yahoo.com"
    email.endsWith("@proton.me") || email.endsWith("@protonmail.com") ->
        "https://mail.proton.me"
    email.endsWith("@icloud.com") ->
        "https://www.icloud.com/mail"
    else -> null
}

@Composable
internal fun AuthDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "or",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
internal fun AuthErrorBanner(message: String) {
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun AccountPrivacyCard(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

internal const val BOXLORE_PRIVACY_POLICY_URL = "https://aswin.cx/boxlore/privacy/"

@Composable
internal fun PrivacyPolicyNotice(
    modifier: Modifier = Modifier,
    prefix: String = "By continuing, you agree to our ",
    suffix: String = ".",
    privacyUrl: String = BOXLORE_PRIVACY_POLICY_URL,
) {
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val annotatedText = buildAnnotatedString {
        append(prefix)
        val link = LinkAnnotation.Url(
            url = privacyUrl,
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = GoogleSansWeight.semiBold,
                ),
            ),
        ) {
            val opened = runCatching {
                uriHandler.openUri(privacyUrl)
                true
            }.getOrDefault(false)

            if (!opened) {
                val fallbackOpened = runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)

                if (!fallbackOpened) {
                    showAccountToast(context, "Could not open browser")
                }
            }
        }
        withLink(link) {
            append("Privacy Policy")
        }
        append(suffix)
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
