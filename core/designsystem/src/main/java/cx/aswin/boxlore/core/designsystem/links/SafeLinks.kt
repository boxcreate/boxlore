package cx.aswin.boxlore.core.designsystem.links

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import cx.aswin.boxlore.core.designsystem.R

/**
 * Starts the first of [intents] an app on this phone can handle, trying the next when none can (the
 * Play app, then the Play web page, say). Returns false, without throwing, when none can: a phone
 * without a browser, a store or a settings page a ROM removed must never crash the app.
 */
fun Context.startFirstAvailable(vararg intents: Intent): Boolean {
    val fromActivity = findActivity() != null
    for (intent in intents) {
        val launch = if (fromActivity) intent else Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launch)
            return true
        } catch (_: ActivityNotFoundException) {
            // Nothing handles this one: try the next.
        } catch (_: SecurityException) {
            // The only handler isn't open to other apps: try the next.
        }
    }
    return false
}

/** Opens [uri] in whatever app handles it, or says briefly that no app could. Returns whether one opened it. */
fun Context.openLink(uri: Uri): Boolean {
    val opened = startFirstAvailable(Intent(Intent.ACTION_VIEW, uri))
    if (!opened) Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show()
    return opened
}

/**
 * Compose's link handler for the whole app: Compose's own throws when no app can open a link, which
 * crashed every `openUri` caller on such a phone. This one opens links with [openLink].
 */
class SafeUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        context.openLink(Uri.parse(uri))
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
