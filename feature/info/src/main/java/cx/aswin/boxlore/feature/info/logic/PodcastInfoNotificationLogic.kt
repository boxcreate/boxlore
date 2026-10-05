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

/** Capture the disclosed URL before deferring either toolbar action to confirmation. */
internal fun requestPodcastNotificationAction(
    podcast: Podcast,
    onDisclosureRequired: (() -> Unit) -> Unit,
    onProceed: (Boolean, String?) -> Unit,
) {
    val feedUrl = podcast.feedUrl
    if (requiresRssNotificationDisclosure(podcast)) {
        onDisclosureRequired { onProceed(true, feedUrl) }
    } else {
        onProceed(false, null)
    }
}
