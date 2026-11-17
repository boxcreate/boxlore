package cx.aswin.boxlore.core.catalog.sync

import android.util.Log
import cx.aswin.boxlore.core.catalog.FeedAddressChange
import cx.aswin.boxlore.core.catalog.FeedAddressNotSavedException
import cx.aswin.boxlore.core.catalog.FeedAddressUpdate
import cx.aswin.boxlore.core.catalog.FeedAddressUpdater
import cx.aswin.boxlore.core.catalog.FeedUrlSources
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.PacedPodcastLookups
import cx.aswin.boxlore.core.catalog.PodcastDetailsAnswer
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ports.PodcastNotificationSyncPort
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
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
 *
 * A new Podcast Index follow is saved as a placeholder row ([STUB_TITLE], no artwork) and filled in
 * from `GET /podcast` through [detailsLookups] only while the route budget has room, so a pull of a
 * large library never runs into the API's rate limit; [healStubRows] fills in the rest later.
 */
class SubscriptionSyncResolver(
    private val podcastDao: PodcastDao,
    private val folderRepository: FolderRepository? = null,
    private val podcastRepository: PodcastRepository? = null,
    private val rssPodcastRepository: RssPodcastRepository? = null,
    private val notificationSyncPort: PodcastNotificationSyncPort? = null,
    private val feedAddresses: FeedAddressUpdater? = null,
    private val detailsLookups: PacedPodcastLookups? = podcastRepository?.let(::PacedPodcastLookups),
) {
    /** Placeholder rows the server has no show for, asked once per process. */
    private val unknownStubs: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    suspend fun resolveSubscription(remote: UserSubscriptionSyncDto, syncedAt: Long) {
        val local = podcastDao.getPodcast(remote.podcastId)
        val remoteSubTime = if (remote.subscribedAt > 0) remote.subscribedAt else remote.updatedAt
        val remoteUnsubTime = if (remote.unsubscribedAt > 0) remote.unsubscribedAt else remote.updatedAt

        if (local == null) {
            handleNewSubscription(remote, remoteSubTime, syncedAt)
        } else if (!remote.isSubscribed) {
            handleRemoteTombstone(local, remoteUnsubTime, syncedAt)
        } else if (!local.isSubscribed) {
            handleLocalUnsubscribedState(local, remote, remoteSubTime, syncedAt)
        } else if (!local.isDirty) {
            handleLocalCleanSubscribedState(local, remote, remoteSubTime, syncedAt)
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
            title = if (isRss) "RSS Feed" else STUB_TITLE,
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
            // Filled in now only when the route has room; otherwise healStubRows does it later.
            val answer = detailsLookups?.whenRoom(remote.podcastId)
            if (answer is PodcastDetailsAnswer.Found) applyDetails(remote.podcastId, answer.podcast)
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

    /**
     * Fills in followed Podcast Index rows a pull left as placeholders ([STUB_TITLE]), one paced
     * `GET /podcast` each (waiting for room in the route budget). A 429 is waited out
     * ([MAX_RATE_LIMITED] times per run). A failed answer for one row skips that row for this run
     * only, so one show the server keeps failing for can't hold up the rows after it;
     * [MAX_UNAVAILABLE_IN_A_ROW] failed answers in a row (no connection, the server down) stop the
     * run. Rows left are placeholders still, so the next run picks them up. Returns how many were filled.
     */
    suspend fun healStubRows(): Int {
        val lookups = detailsLookups ?: return 0
        val run = StubHealRun(lookups)
        val stubs = podcastDao.getSubscribedPodcastsList().filter { isStub(it) && it.podcastId !in unknownStubs }
        // Stops at the first row whose answer ends the run.
        stubs.all { row ->
            when (val answer = run.answerFor(row.podcastId)) {
                null -> false
                is PodcastDetailsAnswer.Found -> {
                    if (applyDetails(row.podcastId, answer.podcast)) run.healed++
                    true
                }
                PodcastDetailsAnswer.NotFound -> {
                    unknownStubs += row.podcastId
                    true
                }
                else -> true
            }
        }
        return run.healed
    }

    /** One [healStubRows] run: waits out a few 429s, and skips a row the server fails for unless several do in a row. */
    private class StubHealRun(private val lookups: PacedPodcastLookups) {
        var healed = 0
        private var rateLimited = 0
        private var unavailableInARow = 0

        /** [podcastId]'s answer; null when the run should stop (too many 429s, or failures for several rows in a row). */
        suspend fun answerFor(podcastId: String): PodcastDetailsAnswer? {
            var answer = lookups.waiting(podcastId)
            while (answer is PodcastDetailsAnswer.RateLimited && ++rateLimited <= MAX_RATE_LIMITED) answer = lookups.waiting(podcastId)
            if (answer == PodcastDetailsAnswer.Unavailable) return answer.takeIf { ++unavailableInARow < MAX_UNAVAILABLE_IN_A_ROW }
            unavailableInARow = 0
            return answer.takeUnless { it is PodcastDetailsAnswer.RateLimited }
        }
    }

    /** Writes [details] into [podcastId]'s row; false when the row is gone. */
    private suspend fun applyDetails(podcastId: String, details: Podcast): Boolean {
        val current = podcastDao.getPodcast(podcastId) ?: return false

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
        return true
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
            } else if (!local.isDirty) {
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

    private suspend fun handleLocalUnsubscribedState(
        local: PodcastEntity,
        remote: UserSubscriptionSyncDto,
        remoteSubTime: Long,
        syncedAt: Long,
    ) {
        if (remoteSubTime <= local.unsubscribedAt) {
            if (!local.isDirty) podcastDao.upsert(local.copy(isDirty = true))
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
        // The linked-watch merge passes syncedAt 0 and always writes its row.
        if (syncedAt == 0L || needsWrite(local, updated)) upsertMerged(updated)
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
        // The pull fails before its time is saved, so the next sync pulls this address again.
        if (result == FeedAddressUpdate.NOT_SAVED) throw FeedAddressNotSavedException(local.podcastId)
        if (result != FeedAddressUpdate.UPDATED) Log.d(TAG, "Pulled address for ${local.podcastId} not saved: $result")
    }

    companion object {
        /** The title of a followed Podcast Index show's row until its details arrive. Stable: other readers compare with it. */
        const val STUB_TITLE = "Loading..."

        private const val TAG = "SubscriptionSyncResolver"

        /** 429s a [healStubRows] run waits out before it leaves the rest for the next run. */
        private const val MAX_RATE_LIMITED = 3

        /** Failed answers for this many rows in a row stop a [healStubRows] run: likely no connection, or the server is down. */
        private const val MAX_UNAVAILABLE_IN_A_ROW = 3

        /** A subscribed Podcast Index row still waiting for its details. */
        fun isStub(row: PodcastEntity): Boolean = row.isSubscribed && !row.isRss && !row.podcastId.startsWith("rss:") && row.title == STUB_TITLE

        /**
         * Whether a pulled clean row must be written: something besides its sync time changed, or it
         * was never stamped. Readers only ask whether `syncedAt` is 0, so an unchanged clean row keeps
         * its earlier sync time and the pull writes nothing.
         */
        private fun needsWrite(local: PodcastEntity, updated: PodcastEntity): Boolean = updated.copy(syncedAt = local.syncedAt) != local || local.syncedAt == 0L
    }
}
