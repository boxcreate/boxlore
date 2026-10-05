package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast

internal fun requiresRssNotificationDisclosure(podcast: Podcast): Boolean =
    !podcast.notificationsEnabled && (podcast.isRss || podcast.id.startsWith("rss:"))

/** The ViewModel must display the repository's actual result, including refused RSS activation. */
internal suspend fun togglePodcastNotifications(
    podcast: Podcast,
    disclosureAccepted: Boolean,
    disclosedFeedUrl: String?,
    setNotifications: suspend (Podcast, Boolean, Boolean, String?) -> Boolean,
): Podcast {
    val enabled = setNotifications(podcast, !podcast.notificationsEnabled, disclosureAccepted, disclosedFeedUrl)
    return podcast.copy(notificationsEnabled = enabled)
}

internal suspend fun enablePodcastNotificationsAndAutoDownload(
    podcast: Podcast,
    setNotifications: suspend (Podcast, Boolean) -> Boolean,
    setAutoDownload: suspend (String, Boolean) -> Unit,
): Podcast {
    val enabled = setNotifications(podcast, true)
    setAutoDownload(podcast.id, true)
    return podcast.copy(notificationsEnabled = enabled, autoDownloadEnabled = true)
}
