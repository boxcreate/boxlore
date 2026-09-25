package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ports.PodcastNotificationSyncPort
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.rss.RssPodcastRepository

/**
 * LWW (Last-Write-Wins) conflict resolver for podcast subscriptions.
 * Handles tombstones vs active subscriptions, settings merges, RSS feed ingestion,
 * and folder cleanups.
 */
class SubscriptionSyncResolver(
    private val podcastDao: PodcastDao,
    private val folderRepository: FolderRepository? = null,
    private val podcastRepository: PodcastRepository? = null,
    private val rssPodcastRepository: RssPodcastRepository? = null,
    private val notificationSyncPort: PodcastNotificationSyncPort? = null,
) {
    suspend fun resolveSubscription(remote: UserSubscriptionSyncDto, syncedAt: Long) {
        val local = podcastDao.getPodcast(remote.podcastId)
        val remoteSubTime = if (remote.subscribedAt > 0) remote.subscribedAt else remote.updatedAt
        val remoteUnsubTime = if (remote.unsubscribedAt > 0) remote.unsubscribedAt else remote.updatedAt

        if (local == null) {
            handleNewSubscription(remote, remoteSubTime, syncedAt)
        } else if (!remote.isSubscribed) {
            handleRemoteTombstone(local, remoteUnsubTime, syncedAt)
        } else {
            handleRemoteSubscription(local, remote, remoteSubTime, syncedAt)
        }
    }

    private suspend fun handleNewSubscription(
        remote: UserSubscriptionSyncDto,
        remoteSubTime: Long,
        syncedAt: Long,
    ) {
        if (!remote.isSubscribed) return

        val isRss = remote.podcastId.startsWith("rss:")
        if (isRss && tryIngestRssSubscription(remote, remoteSubTime, syncedAt)) {
            return
        }

        val stubEntity = PodcastEntity(
            podcastId = remote.podcastId,
            title = if (isRss) "RSS Feed" else "Loading...",
            author = "",
            imageUrl = "",
            description = null,
            isSubscribed = true,
            subscribedAt = remoteSubTime,
            unsubscribedAt = 0L,
            isDirty = false,
            syncedAt = syncedAt,
            sourceType = if (isRss) PodcastEntity.SOURCE_RSS else PodcastEntity.SOURCE_PODCAST_INDEX,
            feedUrl = remote.feedUrl,
            autoDownloadEnabled = remote.autoDownloadEnabled,
            notificationsEnabled = remote.notificationsEnabled,
            customGenre = remote.customGenre,
        )
        podcastDao.upsert(stubEntity)

        if (!isRss) {
            if (remote.notificationsEnabled) {
                notificationSyncPort?.setNotificationTopicSubscribed(remote.podcastId, true)
            }
            enrichPodcastIndexDetails(remote.podcastId)
        }
    }

    private suspend fun tryIngestRssSubscription(
        remote: UserSubscriptionSyncDto,
        remoteSubTime: Long,
        syncedAt: Long,
    ): Boolean {
        val rssRepo = rssPodcastRepository ?: return false
        val feedUrl = remote.feedUrl?.takeIf { it.isNotBlank() } ?: return false

        val result = runCatching { rssRepo.addSubscription(feedUrl) }.getOrNull() ?: return false
        val ingestedId = result.podcast.id
        val ingested = podcastDao.getPodcast(ingestedId) ?: podcastDao.getPodcast(remote.podcastId) ?: return true

        podcastDao.upsert(
            ingested.copy(
                subscribedAt = remoteSubTime,
                unsubscribedAt = 0L,
                isSubscribed = true,
                autoDownloadEnabled = remote.autoDownloadEnabled,
                notificationsEnabled = remote.notificationsEnabled,
                customGenre = remote.customGenre,
                customGenreIcon = if (remote.customGenre == null) null else ingested.customGenreIcon,
                isDirty = false,
                syncedAt = syncedAt,
            ),
        )
        return true
    }

    private suspend fun enrichPodcastIndexDetails(podcastId: String) {
        val podRepo = podcastRepository ?: return
        val details = runCatching { podRepo.getPodcastDetails(podcastId) }.getOrNull() ?: return
        val current = podcastDao.getPodcast(podcastId) ?: return

        podcastDao.upsert(
            current.copy(
                title = details.title.takeIf { it.isNotBlank() } ?: current.title,
                author = details.artist.takeIf { it.isNotBlank() } ?: current.author,
                imageUrl = details.imageUrl.takeIf { it.isNotBlank() } ?: current.imageUrl,
                description = details.description ?: current.description,
                genre = details.genre.takeIf { it.isNotBlank() } ?: current.genre,
                feedUrl = current.feedUrl ?: details.feedUrl,
            ),
        )
        if (current.notificationsEnabled) {
            notificationSyncPort?.setNotificationTopicSubscribed(podcastId, true)
        }
    }

    private suspend fun handleRemoteTombstone(
        local: PodcastEntity,
        remoteUnsubTime: Long,
        syncedAt: Long,
    ) {
        if (local.isSubscribed) {
            if (remoteUnsubTime > local.subscribedAt) {
                podcastDao.upsert(
                    local.copy(
                        isSubscribed = false,
                        unsubscribedAt = remoteUnsubTime,
                        isDirty = false,
                        syncedAt = syncedAt,
                        autoDownloadEnabled = false,
                        notificationsEnabled = false,
                        customGenre = null,
                        customGenreIcon = null,
                    ),
                )
                folderRepository?.removePodcastFromAllFolders(local.podcastId)
                if (local.isRss) {
                    podcastDao.deleteRssEpisodes(local.podcastId)
                } else if (local.notificationsEnabled) {
                    notificationSyncPort?.setNotificationTopicSubscribed(local.podcastId, false)
                }
            } else {
                podcastDao.upsert(local.copy(isDirty = true))
            }
        } else if (remoteUnsubTime > local.unsubscribedAt) {
            podcastDao.upsert(
                local.copy(
                    unsubscribedAt = remoteUnsubTime,
                    isDirty = false,
                    syncedAt = syncedAt,
                ),
            )
        }
    }

    private suspend fun handleRemoteSubscription(
        local: PodcastEntity,
        remote: UserSubscriptionSyncDto,
        remoteSubTime: Long,
        syncedAt: Long,
    ) {
        if (!local.isSubscribed) {
            handleLocalUnsubscribedState(local, remote, remoteSubTime, syncedAt)
        } else if (!local.isDirty) {
            handleLocalCleanSubscribedState(local, remote, remoteSubTime, syncedAt)
        }
    }

    private suspend fun handleLocalUnsubscribedState(
        local: PodcastEntity,
        remote: UserSubscriptionSyncDto,
        remoteSubTime: Long,
        syncedAt: Long,
    ) {
        if (remoteSubTime <= local.unsubscribedAt) {
            podcastDao.upsert(local.copy(isDirty = true))
            return
        }
        val updated = local.copy(
            isSubscribed = true,
            subscribedAt = remoteSubTime,
            unsubscribedAt = 0L,
            isDirty = false,
            syncedAt = syncedAt,
            autoDownloadEnabled = remote.autoDownloadEnabled,
            notificationsEnabled = remote.notificationsEnabled,
            customGenre = remote.customGenre,
            customGenreIcon = if (remote.customGenre == null) null else local.customGenreIcon,
            feedUrl = remote.feedUrl ?: local.feedUrl,
        )
        podcastDao.upsert(updated)
        if (!local.isRss) {
            if (remote.notificationsEnabled) {
                notificationSyncPort?.setNotificationTopicSubscribed(local.podcastId, true)
            } else if (local.notificationsEnabled) {
                notificationSyncPort?.setNotificationTopicSubscribed(local.podcastId, false)
            }
        }
    }

    private suspend fun handleLocalCleanSubscribedState(
        local: PodcastEntity,
        remote: UserSubscriptionSyncDto,
        remoteSubTime: Long,
        syncedAt: Long,
    ) {
        val genreIcon = if (remote.customGenre == null || remote.customGenre != local.customGenre) {
            null
        } else {
            local.customGenreIcon
        }
        val updated = local.copy(
            customGenre = remote.customGenre,
            customGenreIcon = genreIcon,
            autoDownloadEnabled = remote.autoDownloadEnabled,
            notificationsEnabled = remote.notificationsEnabled,
            feedUrl = remote.feedUrl ?: local.feedUrl,
            subscribedAt = if (remoteSubTime > local.subscribedAt) remoteSubTime else local.subscribedAt,
            isDirty = false,
            syncedAt = syncedAt,
        )
        podcastDao.upsert(updated)
        if (!local.isRss && remote.notificationsEnabled != local.notificationsEnabled) {
            notificationSyncPort?.setNotificationTopicSubscribed(local.podcastId, remote.notificationsEnabled)
        }
    }
}
