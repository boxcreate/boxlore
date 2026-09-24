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
class UserSyncCoordinator(
    private val boxLoreApi: BoxLoreApi,
    private val publicKey: String,
    private val authUserIdProvider: () -> String?,
    private val tokenProvider: suspend () -> String?,
    private val podcastDao: PodcastDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val queueSyncPort: QueueSyncPort,
    private val subscriptionSyncResolver: SubscriptionSyncResolver,
    private val historySyncResolver: HistorySyncResolver,
    private val queueSyncResolver: QueueSyncResolver,
    private val boxcastPrefs: BoxcastPrefs,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()

    suspend fun syncNow(): SyncResult = syncMutex.withLock {
        val userId = authUserIdProvider()
        if (userId.isNullOrBlank()) {
            return SyncResult.SkippedNoAuth
        }

        try {
            val token = tokenProvider()
                ?: return SyncResult.Failure(IllegalStateException("No auth token available"))

            val lastSyncedUser = boxcastPrefs.getLastSyncedUserId()
            if (lastSyncedUser != null && lastSyncedUser != userId) {
                // Account mismatch detected! Purge previous user data to prevent cross-contamination
                purgeLocalDataForAccountSwitch()
                boxcastPrefs.setLastSyncTimestamp(0L)
                boxcastPrefs.setLastSyncedUserId(userId)
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
            val since = boxcastPrefs.getLastSyncTimestamp()
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

            boxcastPrefs.setLastSyncedUserId(userId)

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
        listeningHistoryDao.deleteAll()
        podcastDao.clearAllSubscriptionsForAccountSwitch()
        queueSyncPort.applyRemoteQueueState(
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

    suspend fun executePush(token: String? = null): Result<PushBatchSummary> = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider()
                ?: error("No auth token available")

            val dirtyPodcasts = podcastDao.getDirtyPodcasts().take(MAX_SUBSCRIPTION_BATCH_SIZE)
            val dirtyHistory = listeningHistoryDao.getDirtyListeningHistory().take(MAX_HISTORY_BATCH_SIZE)
            val queueMeta = queueSyncPort.getQueueMetadata()
            val queueDirty = queueMeta?.isDirty == true
            val queueItems = if (queueDirty) queueSyncPort.getQueueSnapshot() else emptyList()

            if (dirtyPodcasts.isEmpty() && dirtyHistory.isEmpty() && !queueDirty) {
                return@runCatching PushBatchSummary(0, 0, false, boxcastPrefs.getLastSyncTimestamp())
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

            val call = boxLoreApi.syncPush(
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
            podcastDao.markPodcastSyncedIfUnchanged(
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
            listeningHistoryDao.markHistorySyncedIfUnchanged(
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
            queueSyncPort.markQueueSynced(queueMeta.queueSequence, syncedAt)
        }
    }

    suspend fun executePull(since: Long, token: String? = null): Result<SyncPullResponse> = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider()
                ?: error("No auth token available")

            val call = boxLoreApi.syncPull(
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
                subscriptionSyncResolver.resolveSubscription(sub, syncedAt)
            }

            // 2. Resolve history
            for (item in body.history) {
                historySyncResolver.resolveHistoryItem(item, syncedAt)
            }

            // 3. Resolve queue
            body.queue?.let { q ->
                queueSyncResolver.resolveQueue(q, syncedAt)
            }

            boxcastPrefs.setLastSyncTimestamp(syncedAt)
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
