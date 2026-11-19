package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastDao

/**
 * The ID the follow a show page shows is stored under, as `SubscriptionRepository.isSubscribed` and
 * the page count it: [shownPodcastId] itself or its followed `rss:` feed, then the same for
 * [routePodcastId] (the show the page was opened as), then a number Podcast Index retired for
 * [shownPodcastId]. Null when none is followed. Playlists and other per-follow places use this ID.
 */
internal suspend fun PodcastDao.pageFollowId(renumbered: RenumberedShowFollows, routePodcastId: String, shownPodcastId: String): String? =
    ownFollowId(shownPodcastId)
        ?: routePodcastId.takeIf { it != shownPodcastId }?.let { ownFollowId(it) }
        ?: renumbered.followedFormerIdOf(shownPodcastId)

/** [podcastId] when its own row is followed, else its followed linked `rss:` feed's ID, else null. */
internal suspend fun PodcastDao.ownFollowId(podcastId: String): String? {
    if (getPodcast(podcastId)?.isSubscribed == true) return podcastId
    return getRssPodcastLinkedTo(podcastId)?.takeIf { it.isSubscribed }?.podcastId
}
