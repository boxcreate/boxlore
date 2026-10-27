package cx.aswin.boxlore.core.catalog.sync

import android.util.Log
import cx.aswin.boxlore.core.catalog.FeedAddressChange
import cx.aswin.boxlore.core.catalog.FeedAddressUpdate
import cx.aswin.boxlore.core.catalog.FeedAddressUpdater
import cx.aswin.boxlore.core.catalog.FeedUrlSources
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
 *
 * A pulled address for a Podcast Index show goes through [feedAddresses], the one address writer
 * (P4 PR 14): validators reset, notifications registered again with acknowledgement, the row left
 * clean. A pull never puts back the address a confirmed correction on this phone replaced, and a
 * merge never writes an older copy's address over one saved meanwhile. Without it (older tests) the address is written with the row as before. `rss:` shows keep
 * their own path: their address is their identity.
 */
class SubscriptionSyncResolver(
    private val podcastDao: PodcastDao,
    private val folderRepository: FolderRepository? = null,
    private val podcastRepository: PodcastRepository? = null,
    private val rssPodcastRepository: RssPodcastRepository? = null,
    private val notificationSyncPort: PodcastNotificationSyncPort? = null,
    private val feedAddresses: FeedAddressUpdater? = null,
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

    /** Remote settings cannot supply consent for publicly tracked RSS feeds. */
    private suspend fun acceptedNotificationSetting(remote: UserSubscriptionSyncDto): Boolean {
        if (!remote.podcastId.startsWith("rss:")) return remote.notificationsEnabled
        val local = podcastDao.getPodcast(remote.podcastId) ?: return false
        return remote.notificationsEnabled && local.notificationsEnabled && (remote.feedUrl == null || remote.feedUrl == local.feedUrl)
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
            notificationsEnabled = acceptedNotificationSetting(remote),
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

        val result = runCatching { rssRepo.restoreSubscription(feedUrl, remote.podcastId) }.getOrNull() ?: return false
        val ingestedId = result.podcast.id
        val ingested = podcastDao.getPodcast(ingestedId) ?: podcastDao.getPodcast(remote.podcastId) ?: return true

        podcastDao.upsert(
            ingested.copy(
                subscribedAt = remoteSubTime,
                unsubscribedAt = 0L,
                isSubscribed = true,
                autoDownloadEnabled = remote.autoDownloadEnabled,
                notificationsEnabled = acceptedNotificationSetting(remote),
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

        val routed = routesAddress(current)
        val enriched =
            current.copy(
                title = details.title.takeIf { it.isNotBlank() } ?: current.title,
                author = details.artist.takeIf { it.isNotBlank() } ?: current.author,
                imageUrl = details.imageUrl.takeIf { it.isNotBlank() } ?: current.imageUrl,
                description = details.description ?: current.description,
                genre = details.genre.takeIf { it.isNotBlank() } ?: current.genre,
                feedUrl = if (routed) current.feedUrl else current.feedUrl ?: details.feedUrl,
            )
        upsertMerged(enriched)
        if (routed) fillMissingAddress(current, details.feedUrl)
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
                }
                if (local.notificationsEnabled) {
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
            notificationsEnabled = acceptedNotificationSetting(remote),
            customGenre = remote.customGenre,
            customGenreIcon = if (remote.customGenre == null) null else local.customGenreIcon,
            feedUrl = rowFeedUrl(local, remote),
        )
        upsertMerged(updated)
        applyPulledAddress(local, remote)
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
            notificationsEnabled = acceptedNotificationSetting(remote),
            feedUrl = rowFeedUrl(local, remote),
            subscribedAt = if (remoteSubTime > local.subscribedAt) remoteSubTime else local.subscribedAt,
            isDirty = false,
            syncedAt = syncedAt,
        )
        upsertMerged(updated)
        applyPulledAddress(local, remote)
        if (updated.notificationsEnabled != local.notificationsEnabled) {
            notificationSyncPort?.setNotificationTopicSubscribed(local.podcastId, updated.notificationsEnabled)
        }
    }

    /** The address the merged row is written with: the stored one when [feedAddresses] applies the pulled one. */
    private fun rowFeedUrl(local: PodcastEntity, remote: UserSubscriptionSyncDto): String? =
        if (routesAddress(local)) local.feedUrl else remote.feedUrl ?: local.feedUrl

    private fun routesAddress(local: PodcastEntity): Boolean = feedAddresses != null && !local.isRss && !local.podcastId.startsWith("rss:")

    /**
     * Writes a merged row built from a copy read earlier. With [feedAddresses], an address another
     * writer saved meanwhile (the daily check, a feed move) and its pending push are kept instead
     * of the copy's ([PodcastDao.upsertKeepingFeedAddress]).
     */
    private suspend fun upsertMerged(row: PodcastEntity) {
        if (routesAddress(row)) podcastDao.upsertKeepingFeedAddress(row) else podcastDao.upsert(row)
    }

    /** A fill of a missing address from the show's details, through the one address writer (P4 PR 14). */
    private suspend fun fillMissingAddress(current: PodcastEntity, detailsFeedUrl: String?) {
        if (!current.feedUrl.isNullOrBlank()) return
        val found = detailsFeedUrl?.takeIf(String::isNotBlank) ?: return
        feedAddresses?.update(FeedAddressChange(podcastId = current.podcastId, feedUrl = found, source = FeedUrlSources.PODCAST_LOOKUP, fromCloud = true))
    }

    /** A pulled address that differs from the stored one, saved through the one address writer (P4 PR 14). */
    private suspend fun applyPulledAddress(local: PodcastEntity, remote: UserSubscriptionSyncDto) {
        val writer = feedAddresses?.takeIf { routesAddress(local) } ?: return
        val pulled = remote.feedUrl?.takeIf { it.isNotBlank() && it != local.feedUrl } ?: return
        val change =
            FeedAddressChange(
                podcastId = local.podcastId,
                feedUrl = pulled,
                source = FeedUrlSources.CLOUD_SYNC,
                fromCloud = true,
                onlyReplacing = local.feedUrl,
            )
        // KEPT_CORRECTION: the pull would undo a confirmed correction; it stays and is pushed back.
        val result = writer.update(change)
        if (result != FeedAddressUpdate.UPDATED) Log.d(TAG, "Pulled address for ${local.podcastId} not saved: $result")
    }

    private companion object {
        const val TAG = "SubscriptionSyncResolver"
    }
}
