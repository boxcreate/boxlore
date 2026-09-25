package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.FolderDao
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupDao
import cx.aswin.boxlore.core.database.ListeningSessionDao
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
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
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
@Suppress("LongParameterList", "TooManyFunctions", "kotlin:S107")
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
    private val activePlaybackSyncPort: ActivePlaybackSyncPort? = null,
    private val folderDao: FolderDao? = null,
    private val listeningSessionDao: ListeningSessionDao? = null,
    private val listeningRollupDao: ListeningRollupDao? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
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
                return handleAccountSwitch(userId, token)
            }
            if (lastSyncedUser == null) {
                requireBoxcastPrefs.setLastSyncedUserId(userId)
            }

            initMetadataVersionIfNeeded()

            val pushSummary = executePush(token).getOrElse { err ->
                return SyncResult.Failure(err)
            }

            executePullWithBackfill(userId, pushSummary, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult.Failure(e)
        }
    }

    private suspend fun handleAccountSwitch(userId: String, token: String?): SyncResult {
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

    private suspend fun initMetadataVersionIfNeeded() {
        val metadataVersion = requireBoxcastPrefs.getSyncMetadataVersion()
        if (metadataVersion < 1) {
            requireListeningHistoryDao.markAllHistoryWithTitlesDirty()
            requireBoxcastPrefs.setSyncMetadataVersion(1)
        }
    }

    private suspend fun executePullWithBackfill(
        userId: String,
        pushSummary: PushBatchSummary,
        token: String?,
    ): SyncResult {
        val needsBackfill = requireBoxcastPrefs.getSyncMetadataVersion() < 2 &&
            requireListeningHistoryDao.hasAnyHistoryWithBlankTitle()
        val since = if (needsBackfill) 0L else requireBoxcastPrefs.getLastSyncTimestamp()
        val pullResult = executePull(since, token)

        return pullResult.fold(
            onSuccess = { pullResponse ->
                if (needsBackfill) {
                    requireBoxcastPrefs.setSyncMetadataVersion(2)
                }
                requireBoxcastPrefs.setLastSyncedUserId(userId)
                SyncResult.Success(
                    pushedSubscriptions = pushSummary.pushedSubscriptions,
                    pushedHistory = pushSummary.pushedHistory,
                    pushedQueue = pushSummary.pushedQueue,
                    pulledSubscriptions = pullResponse.subscriptions.size,
                    pulledHistory = pullResponse.history.size,
                    pulledQueue = pullResponse.queue != null,
                    syncedAt = maxOf(pushSummary.syncedAt, pullResponse.syncedAt),
                )
            },
            onFailure = { err ->
                SyncResult.Failure(err, partialSyncedAt = pushSummary.syncedAt.takeIf { it > 0 })
            },
        )
    }

    private suspend fun purgeLocalDataForAccountSwitch() = withContext(ioDispatcher) {
        purgeSubscriptions()
        purgeHistory()
        purgeQueue()
        purgeAccountMetadata()
    }

    private suspend fun purgeSubscriptions() {
        requirePodcastDao.clearAllSubscriptionsForAccountSwitch()
        folderDao?.deleteAllCrossRefs()
        folderDao?.deleteAllFolders()
    }

    private suspend fun purgeHistory() {
        requireListeningHistoryDao.deleteAll()
        listeningSessionDao?.deleteAllSessions()
        listeningRollupDao?.deleteAllRollups()
    }

    private suspend fun purgeQueue() {
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
        activePlaybackSyncPort?.stopAndClearActiveSession()
    }

    private suspend fun purgeAccountMetadata() {
        userPreferencesRepository?.setSubscriptionManualOrder(emptyList())
        userPreferencesRepository?.setHomePinnedPodcastIds(emptyList())
    }

    open suspend fun executePush(token: String? = null): Result<PushBatchSummary> = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider()
                ?: error("No auth token available")

            val currentUserId = authUserIdProvider()
            val lastSyncedUser = requireBoxcastPrefs.getLastSyncedUserId()
            if (lastSyncedUser != null && currentUserId != null && lastSyncedUser != currentUserId) {
                // Pending account switch cleanup: abort push so previous account data is never pushed with new token
                return@runCatching PushBatchSummary(0, 0, false, requireBoxcastPrefs.getLastSyncTimestamp())
            }

            ensureDirtyFlagsForSyncState()

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
        }.onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun ensureDirtyFlagsForSyncState() {
        val isFirstSyncForDevice = requireBoxcastPrefs.getLastSyncTimestamp() == 0L
        if (isFirstSyncForDevice) {
            markAllDirtyForFirstSync()
        } else {
            markAllDirtyForSubsequentSync()
        }
    }

    private suspend fun markAllDirtyForFirstSync() {
        requirePodcastDao.markAllSubscribedPodcastsDirty()
        requireListeningHistoryDao.markAllHistoryDirty()
        if (requireQueueSyncPort.getQueueSnapshot().isNotEmpty()) {
            requireQueueSyncPort.markQueueDirty()
        }
    }

    private suspend fun markAllDirtyForSubsequentSync() {
        requirePodcastDao.markAllUnsyncedSubscribedPodcastsDirty()
        requireListeningHistoryDao.markAllUnsyncedHistoryDirty()
        val initialQueueMeta = requireQueueSyncPort.getQueueMetadata()
        if (initialQueueMeta?.syncedAt == 0L && requireQueueSyncPort.getQueueSnapshot().isNotEmpty()) {
            requireQueueSyncPort.markQueueDirty()
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
            episodeTitle = h.episodeTitle.takeIf { it.isNotBlank() },
            episodeImageUrl = h.episodeImageUrl?.takeIf { it.isNotBlank() },
            podcastImageUrl = h.podcastImageUrl?.takeIf { it.isNotBlank() },
            podcastName = h.podcastName.takeIf { it.isNotBlank() },
            episodeAudioUrl = h.episodeAudioUrl?.takeIf { it.isNotBlank() },
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
                    title = q.title.takeIf { it.isNotBlank() },
                    podcastTitle = q.podcastTitle.takeIf { it.isNotBlank() },
                    imageUrl = q.imageUrl?.takeIf { it.isNotBlank() },
                    podcastImageUrl = q.podcastImageUrl?.takeIf { it.isNotBlank() },
                    audioUrl = q.audioUrl.takeIf { it.isNotBlank() },
                    duration = q.duration,
                    pubDate = q.pubDate,
                    description = q.description?.takeIf { it.isNotBlank() },
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
            applyPullResponse(body)
            body
        }.onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun applyPullResponse(body: SyncPullResponse) {
        val syncedAt = body.syncedAt
        for (sub in body.subscriptions) {
            requireSubscriptionSyncResolver.resolveSubscription(sub, syncedAt)
        }
        for (item in body.history) {
            requireHistorySyncResolver.resolveHistoryItem(item, syncedAt)
        }
        body.queue?.let { q ->
            requireQueueSyncResolver.resolveQueue(q, syncedAt)
        }
        handoffIdleMiniplayerIfNewer(body.history)
        requireBoxcastPrefs.setLastSyncTimestamp(syncedAt)
    }

    private suspend fun handoffIdleMiniplayerIfNewer(history: List<ListeningHistorySyncDto>) {
        val newestRemoteHistory = history.maxByOrNull { it.lastPlayedAt }
        if (newestRemoteHistory != null && newestRemoteHistory.lastPlayedAt > 0L) {
            activePlaybackSyncPort?.updateIdlePlaybackSession(
                episodeId = newestRemoteHistory.episodeId,
                positionMs = newestRemoteHistory.progressMs,
                lastPlayedAt = newestRemoteHistory.lastPlayedAt,
            )
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
