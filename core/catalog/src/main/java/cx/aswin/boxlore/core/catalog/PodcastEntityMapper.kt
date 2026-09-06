package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast

/**
 * Canonical [PodcastEntity] → [Podcast] mapper shared across the data layer (and by feature
 * modules such as `feature/info`) so every caller maps the same fields the same way.
 * [RssPodcastRepository] maintains an internal variant to respect module boundaries
 * without creating a reverse dependency on `:core:catalog`.
 */
fun PodcastEntity.toPodcast(): Podcast = Podcast(
    id = podcastId,
    title = title,
    artist = author,
    imageUrl = imageUrl,
    type = type,
    description = description,
    genre = genre ?: "Podcast",
    fallbackImageUrl = latestEpisode?.imageUrl,
    latestEpisode = latestEpisode,
    subscribedAt = subscribedAt,
    fundingUrl = fundingUrl,
    fundingMessage = fundingMessage,
    podcastGuid = podcastGuid,
    medium = medium,
    hasValue = hasValue,
    updateFrequency = updateFrequency,
    location = location,
    license = license,
    isLocked = isLocked,
    preferredSort = preferredSort,
    notificationsEnabled = notificationsEnabled,
    autoDownloadEnabled = autoDownloadEnabled,
    skipBeginningOverrideMs = skipBeginningOverrideMs,
    skipEndingOverrideMs = skipEndingOverrideMs,
    sourceType = sourceType,
    feedUrl = feedUrl,
    rssRefreshCapability = rssRefreshCapability,
    rssCatalogStale = rssCatalogStale,
    rssHasNewEpisodes = rssHasNewEpisodes,
    linkedPodcastIndexId = linkedPodcastIndexId,
    customGenre = customGenre?.takeIf { isSubscribed },
    customGenreIcon = customGenreIcon?.takeIf { isSubscribed },
)
