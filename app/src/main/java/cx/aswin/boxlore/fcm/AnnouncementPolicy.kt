package cx.aswin.boxlore.fcm

import android.content.Context
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import cx.aswin.boxlore.ui.announcement.AnnouncementLayout
import cx.aswin.boxlore.ui.announcement.resolveAnnouncementLayout
import cx.aswin.boxlore.util.isInstalledFromPlayStore
import java.net.URI

internal fun ParsedFcmNotification.toAnnouncement() = Announcement(
    title, body, route, imageUrl, actionLabel, showActionInApp, System.currentTimeMillis(), category,
    presentation, tone, imageStyle, releaseAlert, includePlay, testOnly, releaseVersionCode, releaseUrl,
)

internal fun isReleaseAnnouncement(announcement: Announcement): Boolean = announcement.releaseAlert ?: (resolveAnnouncementLayout(announcement.category) == AnnouncementLayout.WhatsNew)

/** Only the repository's public release pages can be presented as View on GitHub. */
internal fun releaseAnnouncementUrl(announcement: Announcement): String {
    val explicit = announcement.releaseUrl?.takeIf { value ->
        runCatching {
            val uri = URI(value)
            uri.scheme == "https" &&
                uri.host == "github.com" &&
                uri.port == -1 &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                (uri.path == "/boxcreate/boxlore/releases/latest" || uri.path.startsWith("/boxcreate/boxlore/releases/tag/"))
        }.getOrDefault(false)
    }
    if (explicit != null) return explicit
    val tag = Regex("https://github\\.com/boxcreate/boxlore/releases/download/([^/?#]+)/[^?#]+\\.apk").matchEntire(announcement.route.orEmpty())?.groupValues?.get(1)
    return if (tag != null) "https://github.com/boxcreate/boxlore/releases/tag/$tag" else "https://github.com/boxcreate/boxlore/releases/latest"
}

/** Only release announcements are channel-filtered. Episode and ordinary alerts remain independent. */
internal fun announcementAllowed(announcement: Announcement, play: Boolean, testBuild: Boolean, installedVersion: Long, isolated: Boolean = false): Boolean {
    if (isolated && !announcement.testOnly) return false
    if (announcement.testOnly && !testBuild) return false
    val release = isReleaseAnnouncement(announcement)
    if (!release) return true
    if (play && !announcement.includePlay) return false
    return announcement.releaseVersionCode == 0L || announcement.releaseVersionCode > installedVersion
}

fun Context.shouldSuppressAnnouncement(announcement: Announcement): Boolean = !announcementAllowed(
    announcement,
    play = !BuildConfig.BOXLORE_DIRECT_UPDATES || isInstalledFromPlayStore(),
    testBuild = BuildConfig.DEBUG || BuildConfig.BOXLORE_ISOLATED_TESTS,
    installedVersion = BuildConfig.VERSION_CODE.toLong(),
    isolated = BuildConfig.BOXLORE_ISOLATED_TESTS,
)

internal fun isUpdaterAnnouncementAction(announcement: Announcement, route: String): Boolean {
    if (route == "boxlore://updates" || route == "boxlore://updates/download") return true
    if (!isReleaseAnnouncement(announcement)) return false
    return isBoxloreReleaseApk(route)
}

internal fun isBoxloreReleaseApk(route: String): Boolean = runCatching {
    val uri = URI(route)
    val trustedHost = uri.scheme == "https" && uri.host == "github.com"
    val trustedAuthority = uri.port == -1 && uri.userInfo == null
    trustedHost && trustedAuthority && uri.path.startsWith("/boxcreate/boxlore/releases/download/") && uri.path.endsWith(".apk")
}.getOrDefault(false)

internal fun pushAnnouncementTarget(notification: ParsedFcmNotification): String? {
    val route = notification.route ?: return null
    return if (isReleaseAnnouncement(notification.toAnnouncement()) && isBoxloreReleaseApk(route)) {
        "boxlore://updates/download"
    } else {
        route
    }
}

internal fun shouldDismissAnnouncementForAction(announcement: Announcement, route: String): Boolean = !isReleaseAnnouncement(announcement) || isUpdaterAnnouncementAction(announcement, route)
