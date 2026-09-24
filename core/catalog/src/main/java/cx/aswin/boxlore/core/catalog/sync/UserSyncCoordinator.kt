package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestrator for Boxlore Realtime Cloud Sync.
 * Collects dirty deltas with strict batch bounds, pushes state to the backend,
 * performs multi-column optimistic concurrency flag clearing, and pulls
 * remote deltas through Last-Write-Wins (LWW) conflict resolvers.
 */
@Suppress("LongParameterList")
open class UserSyncCoordinator(
    private val boxLoreApi: BoxLoreApi? = null,
    private val publicKey: String = "",
    private val authUserIdProvider: () -> String? = { null },
    private val tokenProvider: suspend () -> String? = { null },
    private val podcastDao: PodcastDao? = null,
    private val listeningHistoryDao: ListeningHistoryDao? = null,
    private val queueSyncPort: QueueSyncPort? = null,
    private val subscriptionSyncResolver: SubscriptionSyncResolver? = null,
    private val historySyncResolver: HistorySyncResolver? = null,
    private val queueSyncResolver: QueueSyncResolver? = null,
    private val boxcastPrefs: BoxcastPrefs? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()

    private val requireBoxLoreApi get() = checkNotNull(boxLoreApi) { "boxLoreApi required" }
    private val requirePodcastDao get() = checkNotNull(podcastDao) { "podcastDao required" }
    private val requireListeningHistoryDao get() = checkNotNull(listeningHistoryDao) { "listeningHistoryDao required" }
    private val requireQueueSyncPort get() = checkNotNull(queueSyncPort) { "queueSyncPort required" }
    private val requireSubscriptionSyncResolver get() = checkNotNull(subscriptionSyncResolver) { "subscriptionSyncResolver required" }
    private val requireHistorySyncResolver get() = checkNotNull(historySyncResolver) { "historySyncResolver required" }
    private val requireQueueSyncResolver get() = checkNotNull(queueSyncResolver) { "queueSyncResolver required" }
    private val requireBoxcastPrefs get() = checkNotNull(boxcastPrefs) { "boxcastPrefs required" }

    open suspend fun syncNow(): SyncResult = syncMutex.withLock {
        val userId = authUserIdProvider()
        if (userId.isNullOrBlank()) {
            return SyncResult.SkippedNoAuth
        }

        try {
            val token = tokenProvider()
                ?: return SyncResult.Failure(IllegalStateException("No auth token available"))

            val lastSyncedUser = requireBoxcastPrefs.getLastSyncedUserId()
            if (lastSyncedUser != null && lastSyncedUser != userId) {
                // Account mismatch detected! Purge previous user data to prevent cross-contamination
                purgeLocalDataForAccountSwitch()
                requireBoxcastPrefs.setLastSyncTimestamp(0L)
                requireBoxcastPrefs.setLastSyncedUserId(userId)
                val pullResult = executePull(since = 0L, token)
                return pullResult.fold(
                    onSuccess = { res ->
                        SyncResult.Success(
                            pushedSubscriptions = 0,
                            pushedHistory = 0,
                            pushedQueue = false,
                            pulledSubscriptions = res.subscriptions.size,
                            pulledHistory = res.history.size,
                            pulledQueue = res.queue != null,
                            syncedAt = res.syncedAt,
                        )
                    },
                    onFailure = { err ->
                        SyncResult.Failure(err)
                    },
                )
            }

            // 1. Push local dirty deltas
            var totalPushedSubs = 0
            var totalPushedHist = 0
            var pushedQueue = false
            var latestSyncedAt = 0L

            val pushResult = executePush(token)
            pushResult.onSuccess { summary ->
                totalPushedSubs = summary.pushedSubscriptions
                totalPushedHist = summary.pushedHistory
                pushedQueue = summary.pushedQueue
                latestSyncedAt = summary.syncedAt
            }.onFailure { err ->
                return SyncResult.Failure(err)
            }

            // 2. Pull remote deltas since lastSyncTimestamp
            val since = requireBoxcastPrefs.getLastSyncTimestamp()
            val pullResult = executePull(since, token)
            var pulledSubs = 0
            var pulledHist = 0
            var pulledQueue = false

            pullResult.onSuccess { pullResponse ->
                pulledSubs = pullResponse.subscriptions.size
                pulledHist = pullResponse.history.size
                pulledQueue = pullResponse.queue != null
                latestSyncedAt = maxOf(latestSyncedAt, pullResponse.syncedAt)
            }.onFailure { err ->
                return SyncResult.Failure(err, partialSyncedAt = latestSyncedAt.takeIf { it > 0 })
            }

            requireBoxcastPrefs.setLastSyncedUserId(userId)

            SyncResult.Success(
                pushedSubscriptions = totalPushedSubs,
                pushedHistory = totalPushedHist,
                pushedQueue = pushedQueue,
                pulledSubscriptions = pulledSubs,
                pulledHistory = pulledHist,
                pulledQueue = pulledQueue,
                syncedAt = latestSyncedAt,
            )
        } catch (e: Exception) {
            SyncResult.Failure(e)
        }
    }

    private suspend fun purgeLocalDataForAccountSwitch() = withContext(ioDispatcher) {
        requireListeningHistoryDao.deleteAll()
        requirePodcastDao.clearAllSubscriptionsForAccountSwitch()
        requireQueueSyncPort.applyRemoteQueueState(
            items = emptyList(),
            metadata = QueueMetadataEntity(
                id = 1,
                queueSequence = 0L,
                queueUpdatedAt = 0L,
                lastModifiedDeviceId = null,
                recentRemovedEpisodeIds = null,
                isDirty = false,
                syncedAt = 0L,
            ),
        )
    }

    open suspend fun executePush(token: String? = null): Result<PushBatchSummary> = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider()
                ?: error("No auth token available")

            val isFirstSyncForDevice = requireBoxcastPrefs.getLastSyncTimestamp() == 0L
            if (isFirstSyncForDevice) {
                requirePodcastDao.markAllSubscribedPodcastsDirty()
                requireListeningHistoryDao.markAllHistoryDirty()
                if (requireQueueSyncPort.getQueueSnapshot().isNotEmpty()) {
                    requireQueueSyncPort.markQueueDirty()
                }
            } else {
                requirePodcastDao.markAllUnsyncedSubscribedPodcastsDirty()
                requireListeningHistoryDao.markAllUnsyncedHistoryDirty()
                val initialQueueMeta = requireQueueSyncPort.getQueueMetadata()
                if (initialQueueMeta?.syncedAt == 0L && requireQueueSyncPort.getQueueSnapshot().isNotEmpty()) {
                    requireQueueSyncPort.markQueueDirty()
                }
            }

            val dirtyPodcasts = requirePodcastDao.getDirtyPodcasts().take(MAX_SUBSCRIPTION_BATCH_SIZE)
            val dirtyHistory = requireListeningHistoryDao.getDirtyListeningHistory().take(MAX_HISTORY_BATCH_SIZE)
            val queueMeta = requireQueueSyncPort.getQueueMetadata()
            val queueDirty = queueMeta?.isDirty == true
            val queueItems = if (queueDirty) requireQueueSyncPort.getQueueSnapshot() else emptyList()

            if (dirtyPodcasts.isEmpty() && dirtyHistory.isEmpty() && !queueDirty) {
                return@runCatching PushBatchSummary(0, 0, false, requireBoxcastPrefs.getLastSyncTimestamp())
            }

            val now = System.currentTimeMillis()
            val subDtos = buildSubscriptionDtos(dirtyPodcasts, now)
            val histDtos = buildHistoryDtos(dirtyHistory, now)
            val queueDto = buildQueueDto(queueDirty, queueMeta, queueItems)

            val pushReq = SyncPushRequest(
                subscriptions = subDtos,
                history = histDtos,
                queue = queueDto,
                clientTimestamp = now,
            )

            val call = requireBoxLoreApi.syncPush(
                publicKey = publicKey,
                authorization = "Bearer $resolvedToken",
                request = pushReq,
            )
            val response = call.execute()
            if (!response.isSuccessful || response.body() == null) {
                error("Sync push failed with HTTP ${response.code()}: ${response.errorBody()?.string()}")
            }

            val syncedAt = response.body()!!.syncedAt
            clearOptimisticConcurrencyFlags(dirtyPodcasts, dirtyHistory, queueMeta, syncedAt)

            PushBatchSummary(
                pushedSubscriptions = subDtos.size,
                pushedHistory = histDtos.size,
                pushedQueue = queueDto != null,
                syncedAt = syncedAt,
            )
        }
    }

    private fun buildSubscriptionDtos(
        dirtyPodcasts: List<PodcastEntity>,
        now: Long,
    ): List<UserSubscriptionSyncDto> = dirtyPodcasts.map { p ->
        UserSubscriptionSyncDto(
            podcastId = p.podcastId,
            isSubscribed = p.isSubscribed,
            subscribedAt = p.subscribedAt,
            unsubscribedAt = p.unsubscribedAt,
            customGenre = p.customGenre,
            autoDownloadEnabled = p.autoDownloadEnabled,
            notificationsEnabled = p.notificationsEnabled,
            feedUrl = p.feedUrl,
            updatedAt = maxOf(p.subscribedAt, p.unsubscribedAt, now),
        )
    }

    private fun buildHistoryDtos(
        dirtyHistory: List<ListeningHistoryEntity>,
        now: Long,
    ): List<ListeningHistorySyncDto> = dirtyHistory.map { h ->
        ListeningHistorySyncDto(
            episodeId = h.episodeId,
            podcastId = h.podcastId,
            progressMs = h.progressMs,
            durationMs = h.durationMs,
            isCompleted = h.isCompleted,
            isLiked = h.isLiked,
            likedAt = h.likedAt,
            lastPlayedAt = h.lastPlayedAt,
            updatedAt = maxOf(h.lastPlayedAt, h.likedAt, now),
        )
    }

    private fun buildQueueDto(
        queueDirty: Boolean,
        queueMeta: QueueMetadataEntity?,
        queueItems: List<QueueItem>,
    ): QueueSyncDto? {
        if (!queueDirty || queueMeta == null) return null
        return QueueSyncDto(
            items = queueItems.map { q ->
                QueueItemSyncDto(
                    episodeId = q.episodeId,
                    podcastId = q.podcastId,
                    position = q.position,
                    addedAt = q.addedAt,
                    contextType = q.contextType,
                    contextSourceId = q.contextSourceId,
                    updatedAt = queueMeta.queueUpdatedAt,
                )
            },
            queueUpdatedAt = queueMeta.queueUpdatedAt,
            queueSequence = queueMeta.queueSequence,
            lastModifiedDeviceId = queueMeta.lastModifiedDeviceId,
            recentRemovedEpisodeIds = QueueDao.parseRecentRemovedEpisodeIds(queueMeta.recentRemovedEpisodeIds),
        )
    }

    private suspend fun clearOptimisticConcurrencyFlags(
        dirtyPodcasts: List<PodcastEntity>,
        dirtyHistory: List<ListeningHistoryEntity>,
        queueMeta: QueueMetadataEntity?,
        syncedAt: Long,
    ) {
        for (p in dirtyPodcasts) {
            requirePodcastDao.markPodcastSyncedIfUnchanged(
                id = p.podcastId,
                snapshotIsSubscribed = p.isSubscribed,
                snapshotSubscribedAt = p.subscribedAt,
                snapshotUnsubscribedAt = p.unsubscribedAt,
                snapshotAutoDownload = p.autoDownloadEnabled,
                snapshotNotifications = p.notificationsEnabled,
                snapshotCustomGenre = p.customGenre,
                snapshotFeedUrl = p.feedUrl,
                syncedAt = syncedAt,
            )
        }

        for (h in dirtyHistory) {
            requireListeningHistoryDao.markHistorySyncedIfUnchanged(
                episodeId = h.episodeId,
                snapshotLastPlayedAt = h.lastPlayedAt,
                snapshotLikedAt = h.likedAt,
                snapshotProgressMs = h.progressMs,
                snapshotIsCompleted = h.isCompleted,
                snapshotIsLiked = h.isLiked,
                syncedAt = syncedAt,
            )
        }

        if (queueMeta != null && queueMeta.isDirty) {
            requireQueueSyncPort.markQueueSynced(queueMeta.queueSequence, syncedAt)
        }
    }

    suspend fun executePull(since: Long, token: String? = null): Result<SyncPullResponse> = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider()
                ?: error("No auth token available")

            val call = requireBoxLoreApi.syncPull(
                publicKey = publicKey,
                authorization = "Bearer $resolvedToken",
                request = SyncPullRequest(since = since),
            )
            val response = call.execute()
            if (!response.isSuccessful || response.body() == null) {
                error("Sync pull failed with HTTP ${response.code()}: ${response.errorBody()?.string()}")
            }

            val body = response.body()!!
            val syncedAt = body.syncedAt

            // 1. Resolve subscriptions
            for (sub in body.subscriptions) {
                requireSubscriptionSyncResolver.resolveSubscription(sub, syncedAt)
            }

            // 2. Resolve history
            for (item in body.history) {
                requireHistorySyncResolver.resolveHistoryItem(item, syncedAt)
            }

            // 3. Resolve queue
            body.queue?.let { q ->
                requireQueueSyncResolver.resolveQueue(q, syncedAt)
            }

            requireBoxcastPrefs.setLastSyncTimestamp(syncedAt)
            body
        }
    }

    data class PushBatchSummary(
        val pushedSubscriptions: Int,
        val pushedHistory: Int,
        val pushedQueue: Boolean,
        val syncedAt: Long,
    )

    companion object {
        const val MAX_SUBSCRIPTION_BATCH_SIZE = 100
        const val MAX_HISTORY_BATCH_SIZE = 100
    }
}
