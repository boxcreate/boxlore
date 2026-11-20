package cx.aswin.boxlore.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import cx.aswin.boxlore.core.analytics.AnalyticsHelper

internal fun copyDeletionId(
    context: Context,
    deletionId: String,
) {
    AnalyticsHelper.trackSettingsInteraction("delete_id_copied")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_privacy_clipboard_label), deletionId))
    Toast.makeText(context, R.string.settings_privacy_id_copied, Toast.LENGTH_SHORT).show()
}

internal fun requestAnalyticsDeletionByEmail(
    context: Context,
    deletionId: String,
) {
    AnalyticsHelper.trackSettingsInteraction("delete_email_clicked")
    val intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@aswin.cx"))
            // The request goes to the boxlore team, who read it in English, so it is not translated.
            putExtra(Intent.EXTRA_SUBJECT, "Analytics data deletion request") // i18n-ignore
            putExtra(
                Intent.EXTRA_TEXT,
                "Please delete PostHog analytics data associated with this distinct ID: $deletionId",
            )
        }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, R.string.settings_no_email_app, Toast.LENGTH_SHORT).show()
        }
}

internal fun visitPodcastIndexHomepage(context: Context) {
    AnalyticsHelper.trackSettingsInteraction("podcast_index_homepage_clicked")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://podcastindex.org")))
    }
}

internal fun openChangelog(context: Context) {
    AnalyticsHelper.trackSettingsInteraction("changelog_clicked")
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/boxcreate/boxlore/blob/master/CHANGELOG.md"),
            ),
        )
    }
}

/**
 * Opens the phone's permissions for boxlore, where some phones keep the switch that lets an app add
 * to the home screen. Phones with their own permission editor open it directly; the rest open the
 * app's system settings page.
 */
internal fun openAppPermissionSettings(context: Context) {
    AnalyticsHelper.trackSettingsInteraction("app_permission_settings_opened")
    val vendorEditor = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", context.packageName)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(vendorEditor) }
        .recoverCatching { context.startActivity(appDetails) }
}
