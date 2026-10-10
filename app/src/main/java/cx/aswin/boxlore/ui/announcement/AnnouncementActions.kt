package cx.aswin.boxlore.ui.announcement

import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import cx.aswin.boxlore.fcm.isReleaseAnnouncement
import cx.aswin.boxlore.fcm.releaseAnnouncementUrl

internal enum class AnnouncementActionKind { UPDATE, GITHUB, CUSTOM }
internal data class AnnouncementAction(val kind: AnnouncementActionKind, val route: String, val label: String? = null)

/** Release buttons always use the native updater and a validated GitHub page. */
internal fun announcementActions(value: Announcement): List<AnnouncementAction> {
    if (isReleaseAnnouncement(value)) {
        return listOf(
            AnnouncementAction(AnnouncementActionKind.UPDATE, "boxlore://updates/download"),
            AnnouncementAction(AnnouncementActionKind.GITHUB, releaseAnnouncementUrl(value)),
        )
    }
    val route = value.route?.takeIf { value.showActionInApp && it.isNotBlank() } ?: return emptyList()
    return listOf(AnnouncementAction(AnnouncementActionKind.CUSTOM, route, value.actionLabel?.takeIf { it.isNotBlank() }))
}

/** Retain the previous HTTPS-only image policy without accepting file/content URLs. */
internal fun announcementImageUrl(value: String?): String? {
    val uri = value?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return null
    return value.takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null }
}
