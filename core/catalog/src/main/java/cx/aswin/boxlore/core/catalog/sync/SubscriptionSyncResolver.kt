package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
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

        val isRss = remote.podcastId.startsWith("rss:") || remote.feedUrl != null
        val rssRepo = rssPodcastRepository
        val feedUrl = remote.feedUrl
        if (isRss && !feedUrl.isNullOrBlank() && rssRepo != null) {
            val result = runCatching { rssRepo.addSubscription(feedUrl) }.getOrNull()
            if (result != null) {
                val ingested = podcastDao.getPodcast(remote.podcastId)
                if (ingested != null) {
                    podcastDao.upsert(
                        ingested.copy(
                            subscribedAt = remoteSubTime,
                            unsubscribedAt = 0L,
                            isSubscribed = true,
                            autoDownloadEnabled = remote.autoDownloadEnabled,
                            notificationsEnabled = remote.notificationsEnabled,
                            customGenre = remote.customGenre,
                            isDirty = false,
                            syncedAt = syncedAt,
                        ),
                    )
                }
                return
            }
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
            sourceType = if (isRss) "rss" else "podcast_index",
            feedUrl = remote.feedUrl,
            autoDownloadEnabled = remote.autoDownloadEnabled,
            notificationsEnabled = remote.notificationsEnabled,
            customGenre = remote.customGenre,
        )
        podcastDao.upsert(stubEntity)

        val podRepo = podcastRepository
        if (!isRss && podRepo != null) {
            runCatching { podRepo.getPodcastDetails(remote.podcastId) }
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
            if (remoteSubTime > local.unsubscribedAt) {
                val updated = local.copy(
                    isSubscribed = true,
                    subscribedAt = remoteSubTime,
                    unsubscribedAt = 0L,
                    isDirty = false,
                    syncedAt = syncedAt,
                    autoDownloadEnabled = remote.autoDownloadEnabled,
                    notificationsEnabled = remote.notificationsEnabled,
                    customGenre = remote.customGenre,
                    feedUrl = remote.feedUrl ?: local.feedUrl,
                )
                podcastDao.upsert(updated)
            } else {
                podcastDao.upsert(local.copy(isDirty = true))
            }
        } else if (remote.updatedAt > local.syncedAt && !local.isDirty) {
            val updated = local.copy(
                customGenre = remote.customGenre,
                autoDownloadEnabled = remote.autoDownloadEnabled,
                notificationsEnabled = remote.notificationsEnabled,
                feedUrl = remote.feedUrl ?: local.feedUrl,
                subscribedAt = if (remoteSubTime > local.subscribedAt) remoteSubTime else local.subscribedAt,
                isDirty = false,
                syncedAt = syncedAt,
            )
            podcastDao.upsert(updated)
        }
    }
}
