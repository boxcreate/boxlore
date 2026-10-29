package cx.aswin.boxlore.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

private const val PLAY_STORE_PACKAGE = "com.android.vending"

/** The Play Store app's page for [packageName]; [playStoreWebUri] when the Play Store app is missing. */
internal fun playStoreAppUri(packageName: String): Uri = Uri.parse("market://details?id=$packageName")

internal fun playStoreWebUri(packageName: String): Uri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")

/**
 * Opens boxlore's Google Play page, so a Play build can still be updated when Play's in-app check
 * fails (for example a Play build installed outside the Play Store, which Play won't answer for).
 */
internal fun openPlayListing(context: Context) {
    val app = Intent(Intent.ACTION_VIEW, playStoreAppUri(context.packageName)).setPackage(PLAY_STORE_PACKAGE)
    try {
        context.startActivity(app)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, playStoreWebUri(context.packageName)))
    }
}
