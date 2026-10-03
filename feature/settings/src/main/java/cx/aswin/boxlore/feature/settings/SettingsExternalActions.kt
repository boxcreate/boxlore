package cx.aswin.boxlore.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import cx.aswin.boxlore.core.analytics.AnalyticsHelper

internal fun copyDeletionId(
    context: Context,
    deletionId: String,
) {
    AnalyticsHelper.trackSettingsInteraction("delete_id_copied")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Anonymous analytics ID", deletionId))
    Toast.makeText(context, "Analytics ID copied", Toast.LENGTH_SHORT).show()
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
            putExtra(Intent.EXTRA_SUBJECT, "Analytics data deletion request")
            putExtra(
                Intent.EXTRA_TEXT,
                "Please delete PostHog analytics data associated with this distinct ID: $deletionId",
            )
        }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "No email app is available", Toast.LENGTH_SHORT).show()
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
