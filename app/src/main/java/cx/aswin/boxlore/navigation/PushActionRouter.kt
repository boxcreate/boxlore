package cx.aswin.boxlore.navigation

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.MainActivity
import cx.aswin.boxlore.fcm.isBoxloreReleaseApk

/** Dispatches notification targets without treating external links as app destinations. */
internal fun dispatchPushAction(
    target: String?,
    handleDeepLink: (Intent) -> Boolean,
    navigate: (String) -> Unit,
    openExternal: (Uri) -> Unit,
    openUpdater: (download: Boolean) -> Unit,
): Boolean {
    val route = PushTargetRouteAllowlist.sanitize(target) ?: return false
    if (isBoxloreReleaseApk(route)) {
        openUpdater(true)
        return true
    }
    if (route in setOf("boxlore://updates", "boxlore://updates/download", "boxcast://updates", "boxcast://updates/download")) {
        openUpdater(route.endsWith("/download"))
        return true
    }
    if (!PushTargetRouteAllowlist.isAppOrWebUri(route)) {
        navigate(route)
        return true
    }
    val uri = Uri.parse(route)
    val web = uri.scheme == "https" || uri.scheme == "http"
    if (web && uri.host.isNullOrBlank()) return false
    if (handleDeepLink(Intent(Intent.ACTION_VIEW, uri))) return true
    if (web) {
        openExternal(uri)
        return true
    }
    val appRoute = uri.host.orEmpty() + uri.encodedPath.orEmpty() + (uri.encodedQuery?.let { "?$it" } ?: "")
    val allowedAppRoute = PushTargetRouteAllowlist.sanitize(appRoute)?.takeUnless(PushTargetRouteAllowlist::isAppOrWebUri) ?: return false
    navigate(allowedAppRoute)
    return true
}

/** Prevents unrecognized verified App Links from dispatching back to MainActivity. */
internal fun externalPushActionIntent(uri: Uri): Intent {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val claimed = uri.host == "aswin.cx" && listOf("/boxlore/share", "/boxcast/share", "/boxlore/auth").any { uri.path.orEmpty().startsWith(it) }
    if (!claimed) return intent
    return Intent.createChooser(intent, null).apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)))
    }
}
