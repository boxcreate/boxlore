package cx.aswin.boxlore.core.catalog.sync

import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.EpisodeIdentityDao
import cx.aswin.boxlore.core.database.FolderDao
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningInsightsMaintenance
import cx.aswin.boxlore.core.database.ListeningRollupDao
import cx.aswin.boxlore.core.database.ListeningSessionDao
import cx.aswin.boxlore.core.database.PlayedShowDao
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.model.persistableArtworkUrl
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueRemovalSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
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
 *
 * With [episodeIdentity] (own-episode-IDs P4, plan section 8), history and queue rows carry their
 * episode's recipe ID and catalog key, the queue carries its rich removals, pulls ask for the rich
 * removals and record pulled rows' identity before they are merged, and the idle mini-player hands
 * off to the pulled episode's owner ID. A one-time catch-up (sync metadata version 3, and again
 * for each account the phone switches to) pulls from the start and pushes back the cloud copies
 * that lack an identity this phone knows, so rows older builds pushed gain it.
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
    /** Shared like and completion settings of episodes stored under several IDs; cleared with history on an account switch. */
    private val episodeIdentityDao: EpisodeIdentityDao? = null,
    /**
     * Episode identity on the sync wire; null sends and reads rows as older builds do. Added in
     * P4 PR 11 of the own-episode-IDs plan (plan section 8, cloud sync identity).
     */
    private val episodeIdentity: SyncEpisodeIdentity? = null,
    /** Shows played; cleared on an account switch and synced with [listeningInsightsMaintenance] (Room v40). */
    private val playedShowDao: PlayedShowDao? = null,
    /** Daily listening per phone for the cloud sync (Room v40); null syncs no listening, as older builds. */
    private val listeningInsightsMaintenance: ListeningInsightsMaintenance? = null,
) {
    private val syncMutex = Mutex()

    /**
     * One push at a time, and no listening pull or reset during one: a push records what it sent as the
     * cloud's once it is answered, so two pushes answered out of order (a background push beside a sync)
     * could record totals the cloud no longer holds. Taken after [syncMutex], never before it.
     */
    private val pushMutex = Mutex()

    private val listeningSync: ListeningSync? by lazy {
        val maintenance = listeningInsightsMaintenance ?: return@lazy null
        val shows = playedShowDao ?: return@lazy null
        val prefs = boxcastPrefs ?: return@lazy null
        ListeningSync(maintenance, shows, listeningHistoryDao ?: return@lazy null, prefs)
    }

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

    /**
     * Runs the one-time identity catch-up again on the next sync (a pull from the start, then the
     * pushes of cloud copies that gain an identity) when it already ran: sets the sync metadata
     * version back below [IDENTITY_SYNC_METADATA_VERSION]. A phone that never synced, or one still
     * waiting for the catch-up, is left as it is. Waits for a running sync, so that sync can't
     * record the catch-up done over this request.
     *
     * Called once the one-time feed address repair has read every followed show again, so history
     * and queue rows whose episodes those reads settled reach the cloud with their identity. Added
     * in P4 PR 15 of the own-episode-IDs plan (plan sections 8 and 11).
     * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
     */
    suspend fun requestIdentityCatchUp() = syncMutex.withLock {
        if (requireBoxcastPrefs.getSyncMetadataVersion() >= IDENTITY_SYNC_METADATA_VERSION) {
            requireBoxcastPrefs.setSyncMetadataVersion(IDENTITY_SYNC_METADATA_VERSION - 1)
        }
    }

    private suspend fun handleAccountSwitch(userId: String, token: String?): SyncResult {
        purgeLocalDataForAccountSwitch()
        requireBoxcastPrefs.setLastSyncTimestamp(0L)
        resetListeningSync()
        requireBoxcastPrefs.setLastSyncedUserId(userId)
        // The new account's cloud rows get the identity catch-up too; until it completes, every sync pulls from the start.
        val needsIdentityCatchUp = episodeIdentity != null
        if (needsIdentityCatchUp && requireBoxcastPrefs.getSyncMetadataVersion() >= IDENTITY_SYNC_METADATA_VERSION) {
            requireBoxcastPrefs.setSyncMetadataVersion(IDENTITY_SYNC_METADATA_VERSION - 1)
        }
        val pullResult = executePull(since = 0L, token)
        return pullResult.fold(
            onSuccess = { res ->
                val catchUp = if (needsIdentityCatchUp) completeIdentityCatchUp(token, res) else null
                SyncResult.Success(
                    pushedSubscriptions = 0,
                    pushedHistory = catchUp?.pushedHistory ?: 0,
                    pushedQueue = catchUp?.pushedQueue == true,
                    pulledSubscriptions = res.subscriptions.size,
                    pulledHistory = res.history.size,
                    pulledQueue = res.queue != null,
                    syncedAt = maxOf(res.syncedAt, catchUp?.syncedAt ?: 0L),
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
        val metadataVersion = requireBoxcastPrefs.getSyncMetadataVersion()
        val needsBackfill = metadataVersion < 2 &&
            requireListeningHistoryDao.hasAnyHistoryWithBlankTitle()
        val needsIdentityCatchUp = episodeIdentity != null && metadataVersion < IDENTITY_SYNC_METADATA_VERSION
        val lastSync = requireBoxcastPrefs.getLastSyncTimestamp()
        val since = if (needsBackfill || needsIdentityCatchUp) 0L else lastSync
        // Rows up to the last sync were delivered before, so a pull from the start doesn't restore ones removed here.
        val pullResult = executePull(since, token, deliveredUntil = lastSync)

        return pullResult.fold(
            onSuccess = { pullResponse ->
                if (needsBackfill) {
                    requireBoxcastPrefs.setSyncMetadataVersion(2)
                }
                val catchUp = if (needsIdentityCatchUp) completeIdentityCatchUp(token, pullResponse) else null
                val pushed = catchUp?.let { pushSummary + it } ?: pushSummary
                requireBoxcastPrefs.setLastSyncedUserId(userId)
                SyncResult.Success(
                    pushedSubscriptions = pushed.pushedSubscriptions,
                    pushedHistory = pushed.pushedHistory,
                    pushedQueue = pushed.pushedQueue,
                    pulledSubscriptions = pullResponse.subscriptions.size,
                    pulledHistory = pullResponse.history.size,
                    pulledQueue = pullResponse.queue != null,
                    syncedAt = maxOf(pushed.syncedAt, pullResponse.syncedAt),
                )
            },
            onFailure = { err ->
                SyncResult.Failure(err, partialSyncedAt = pushSummary.syncedAt.takeIf { it > 0 })
            },
        )
    }

    /**
     * The one-time identity catch-up, right after [pulled] (a pull from the start) was merged:
     * pushes back the cloud copies that lack the identity this phone knows for them
     * ([identityCatchUp]), batch after batch (at most [MAX_CATCH_UP_PUSHES] batches of
     * [MAX_HISTORY_BATCH_SIZE] rows). Each copy is sent as the cloud has it, with its own
     * `updatedAt`, so the server keeps every value but the added identity, and other devices' delta
     * pulls receive nothing: no newer progress is overwritten, and rows other devices removed
     * aren't sent to them again. No local row is marked for the push. Version 3 is recorded only
     * after every batch went through, so a failed or cut-short run starts again, with a pull from
     * the start, on the next sync. Returns what was pushed, or null when nothing was.
     *
     * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, the one-time sync catch-up).
     * COMPAT(own-ids): cloud rows of older builds gain their episode identity. Permanent: never remove (MIGRATION.md §5).
     */
    private suspend fun completeIdentityCatchUp(token: String?, pulled: SyncPullResponse): PushBatchSummary? {
        val identity = episodeIdentity ?: return null
        val catchUp = identity.identityCatchUp(pulled)
        val requests = catchUpRequests(catchUp)
        var total: PushBatchSummary? = null
        for (request in requests.take(MAX_CATCH_UP_PUSHES)) {
            val syncedAt = sendCatchUpPush(token, request) ?: return total
            val pushed = PushBatchSummary(0, request.history.size, request.queue != null, syncedAt)
            total = total?.plus(pushed) ?: pushed
        }
        if (requests.size <= MAX_CATCH_UP_PUSHES) {
            requireBoxcastPrefs.setSyncMetadataVersion(IDENTITY_SYNC_METADATA_VERSION)
        }
        return total
    }

    /** The catch-up's pushes: history in batches, the queue with the first one (or alone). */
    private fun catchUpRequests(catchUp: IdentityCatchUp): List<SyncPushRequest> {
        if (catchUp.isEmpty) return emptyList()
        val now = System.currentTimeMillis()
        val batches = catchUp.history.chunked(MAX_HISTORY_BATCH_SIZE).ifEmpty { listOf(emptyList()) }
        return batches.mapIndexed { index, batch ->
            SyncPushRequest(history = batch, queue = catchUp.queue.takeIf { index == 0 }, clientTimestamp = now)
        }
    }

    /** Sends one catch-up push; its sync time, or null when it failed. */
    private suspend fun sendCatchUpPush(token: String?, request: SyncPushRequest): Long? = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider() ?: error("No auth token available")
            sendPushPayload(resolvedToken, request).syncedAt
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
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
        playedShowDao?.deleteAllPlayedShows()
        // The previous account's like and completion settings must not decide the new account's episodes.
        episodeIdentityDao?.deleteAllGroupStates()
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
                // The previous account's removals with identity go too, so none reaches the new account's queue.
                recentRemovedEpisodes = null,
            ),
        )
        activePlaybackSyncPort?.stopAndClearActiveSession()
    }

    private suspend fun purgeAccountMetadata() {
        userPreferencesRepository?.setSubscriptionManualOrder(emptyList())
        userPreferencesRepository?.setHomePinnedPodcastIds(emptyList())
    }

    open suspend fun executePush(token: String? = null): Result<PushBatchSummary> = withContext(ioDispatcher) {
        pushMutex.withLock { pushOnce(token) }
    }

    /** Starts listening sync over for another cloud copy ([ListeningSync.reset]), never during a push. */
    private suspend fun resetListeningSync() {
        val sync = listeningSync ?: return
        pushMutex.withLock { sync.reset() }
    }

    private suspend fun pushOnce(token: String?): Result<PushBatchSummary> = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider()
                ?: error("No auth token available")

            if (shouldAbortPushForPendingAccountSwitch()) {
                // Pending account switch cleanup: abort push so previous account data is never pushed with new token
                return@runCatching PushBatchSummary(0, 0, false, requireBoxcastPrefs.getLastSyncTimestamp())
            }

            val batch = collectPushBatch()
                ?: return@runCatching PushBatchSummary(0, 0, false, requireBoxcastPrefs.getLastSyncTimestamp())

            val now = System.currentTimeMillis()
            val subDtos = buildSubscriptionDtos(batch.dirtyPodcasts, now)
            val histDtos = buildHistoryDtos(batch.dirtyHistory, now)
            val queueDto = buildQueueDto(batch.queueDirty, batch.queueMeta, batch.queueItems)

            val pushReq = SyncPushRequest(
                subscriptions = subDtos,
                // Listening dates go along once (the backfill), except for rows this push sends anyway.
                history = histDtos + batch.listening?.dateDtos.orEmpty().filter { dates -> histDtos.none { it.episodeId == dates.episodeId } },
                queue = queueDto,
                listeningDays = batch.listening?.dayDtos.orEmpty(),
                playedShows = batch.listening?.showDtos.orEmpty(),
                listeningTotals = true,
                listeningReleaseKnown = true,
                clientTimestamp = now,
            )

            val answer = sendPushPayload(resolvedToken, pushReq)
            val syncedAt = answer.syncedAt
            clearOptimisticConcurrencyFlags(batch.dirtyPodcasts, batch.dirtyHistory, batch.queueMeta, syncedAt)
            batch.listening?.let { listeningSync?.onPushed(it, answer.listeningAccepted, answer.listeningTotals, answer.listeningReleaseKnown) }

            PushBatchSummary(
                pushedSubscriptions = subDtos.size,
                pushedHistory = histDtos.size,
                pushedQueue = queueDto != null,
                syncedAt = syncedAt,
            )
        }.onFailure { if (it is CancellationException) throw it }
    }

    private fun shouldAbortPushForPendingAccountSwitch(): Boolean {
        val currentUserId = authUserIdProvider()
        val lastSyncedUser = requireBoxcastPrefs.getLastSyncedUserId()
        return lastSyncedUser != null && currentUserId != null && lastSyncedUser != currentUserId
    }

    private suspend fun collectPushBatch(): PendingPushBatch? {
        ensureDirtyFlagsForSyncState()

        val dirtyPodcasts = requirePodcastDao.getDirtyPodcasts().take(MAX_SUBSCRIPTION_BATCH_SIZE)
        val dirtyHistory = requireListeningHistoryDao.getDirtyListeningHistoryBatch(MAX_HISTORY_BATCH_SIZE)
        val queueMeta = requireQueueSyncPort.getQueueMetadata()
        val queueDirty = queueMeta?.isDirty == true
        val queueItems = if (queueDirty) requireQueueSyncPort.getQueueSnapshot() else emptyList()
        val listening = listeningSync?.outgoing()?.takeUnless { it.isEmpty }

        val libraryChanged = dirtyPodcasts.isNotEmpty() || dirtyHistory.isNotEmpty() || queueDirty
        if (!libraryChanged && listening == null) {
            return null
        }

        return PendingPushBatch(
            dirtyPodcasts = dirtyPodcasts,
            dirtyHistory = dirtyHistory,
            queueMeta = queueMeta,
            queueDirty = queueDirty,
            queueItems = queueItems,
            listening = listening,
        )
    }

    private fun sendPushPayload(token: String, request: SyncPushRequest): SyncPushResponse {
        val call = requireBoxLoreApi.syncPush(
            publicKey = publicKey,
            authorization = "Bearer $token",
            request = request,
        )
        val response = call.execute()
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            error("Sync push failed with HTTP ${response.code()}: ${response.errorBody()?.string()}")
        }
        return body
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

    private suspend fun buildHistoryDtos(
        dirtyHistory: List<ListeningHistoryEntity>,
        now: Long,
    ): List<ListeningHistorySyncDto> {
        val identities = episodeIdentity?.outgoing(dirtyHistory.map { SyncEpisodeIdentity.OutgoingRow(it.episodeId, it.podcastId) }).orEmpty()
        return dirtyHistory.map { h -> historyDto(h, now, identities[SyncEpisodeIdentity.OutgoingRow(h.episodeId, h.podcastId)]) }
    }

    private fun historyDto(
        h: ListeningHistoryEntity,
        now: Long,
        identity: SyncEpisodeIdentity.WireIdentity?,
    ): ListeningHistorySyncDto =
        ListeningHistorySyncDto(
            episodeId = h.episodeId,
            podcastId = h.podcastId,
            episodeTitle = h.episodeTitle.takeIf { it.isNotBlank() },
            episodeImageUrl = persistableArtworkUrl(h.episodeImageUrl),
            podcastImageUrl = persistableArtworkUrl(h.podcastImageUrl),
            podcastName = h.podcastName.takeIf { it.isNotBlank() },
            episodeAudioUrl = h.episodeAudioUrl?.takeIf { it.isNotBlank() },
            progressMs = h.progressMs,
            durationMs = h.durationMs,
            isCompleted = h.isCompleted,
            isLiked = h.isLiked,
            likedAt = h.likedAt,
            lastPlayedAt = h.lastPlayedAt,
            updatedAt = maxOf(h.lastPlayedAt, h.likedAt, now),
            recipeId = identity?.recipeId,
            catalogKey = identity?.catalogKey,
            firstPlayedAt = h.firstPlayedAt,
            finishedAt = h.finishedAt,
        )

    private suspend fun buildQueueDto(
        queueDirty: Boolean,
        queueMeta: QueueMetadataEntity?,
        queueItems: List<QueueItem>,
    ): QueueSyncDto? {
        if (!queueDirty || queueMeta == null) return null
        val identities = episodeIdentity?.outgoing(queueItems.map { SyncEpisodeIdentity.OutgoingRow(it.episodeId, it.podcastId) }).orEmpty()
        return QueueSyncDto(
            items = queueItems.map { q -> queueItemDto(q, queueMeta, identities[SyncEpisodeIdentity.OutgoingRow(q.episodeId, q.podcastId)]) },
            queueUpdatedAt = queueMeta.queueUpdatedAt,
            queueSequence = queueMeta.queueSequence,
            lastModifiedDeviceId = queueMeta.lastModifiedDeviceId,
            recentRemovedEpisodeIds = QueueDao.parseRecentRemovedEpisodeIds(queueMeta.recentRemovedEpisodeIds),
            recentRemovedEpisodes = richRemovalDtos(queueMeta),
        )
    }

    private fun queueItemDto(
        q: QueueItem,
        queueMeta: QueueMetadataEntity,
        identity: SyncEpisodeIdentity.WireIdentity?,
    ): QueueItemSyncDto =
        QueueItemSyncDto(
            episodeId = q.episodeId,
            podcastId = q.podcastId,
            title = q.title.takeIf { it.isNotBlank() },
            podcastTitle = q.podcastTitle.takeIf { it.isNotBlank() },
            imageUrl = persistableArtworkUrl(q.imageUrl),
            podcastImageUrl = persistableArtworkUrl(q.podcastImageUrl),
            audioUrl = q.audioUrl.takeIf { it.isNotBlank() },
            duration = q.duration,
            pubDate = q.pubDate,
            description = q.description?.takeIf { it.isNotBlank() },
            position = q.position,
            addedAt = q.addedAt,
            contextType = q.contextType,
            contextSourceId = q.contextSourceId,
            updatedAt = queueMeta.queueUpdatedAt,
            recipeId = identity?.recipeId,
            catalogKey = identity?.catalogKey,
        )

    /**
     * The queue's rich removals as pushed: the stored list, always sent (an empty list too), because
     * the server replaces its list with the one sent. Recipe IDs that name another episode of the
     * show are left out ([SyncEpisodeIdentity.outgoingRemovals]).
     *
     * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, rich queue tombstones).
     */
    private suspend fun richRemovalDtos(queueMeta: QueueMetadataEntity): List<QueueRemovalSyncDto> {
        val stored = QueueRemovalIdentity.parseList(queueMeta.recentRemovedEpisodes)
        val sent = episodeIdentity?.outgoingRemovals(stored) ?: stored
        return sent.map { QueueRemovalSyncDto(id = it.id, recipeId = it.recipeId, show = it.show, catalogKey = it.catalogKey) }
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

    /**
     * Pulls the cloud rows changed after [since] and merges them. [deliveredUntil] is the time up
     * to which earlier pulls already delivered every row ([HistorySyncResolver.resolveHistoryItem]);
     * it equals [since] for a delta pull and is the last sync time when a device that synced
     * before pulls from the start.
     */
    suspend fun executePull(since: Long, token: String? = null, deliveredUntil: Long = since): Result<SyncPullResponse> = withContext(ioDispatcher) {
        runCatching {
            val resolvedToken = token ?: tokenProvider()
                ?: error("No auth token available")

            val call = requireBoxLoreApi.syncPull(
                publicKey = publicKey,
                authorization = "Bearer $resolvedToken",
                // Rich removals carry the episode's identity, so a removal reaches it under any of its IDs.
                request =
                SyncPullRequest(
                    since = since,
                    richRemovals = true,
                    listening = listeningSync != null,
                    listeningSince = listeningSync?.pulledUntil(),
                ),
            )
            val response = call.execute()
            if (!response.isSuccessful || response.body() == null) {
                error("Sync pull failed with HTTP ${response.code()}: ${response.errorBody()?.string()}")
            }

            val body = response.body()!!
            applyPullResponse(body, deliveredUntil)
            body
        }.onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun applyPullResponse(body: SyncPullResponse, deliveredUntil: Long) {
        val syncedAt = body.syncedAt
        for (sub in body.subscriptions) {
            requireSubscriptionSyncResolver.resolveSubscription(sub, syncedAt)
        }
        recordPulledIdentities(body)
        for (item in body.history) {
            requireHistorySyncResolver.resolveHistoryItem(item, syncedAt, deliveredUntil)
        }
        body.queue?.let { q ->
            requireQueueSyncResolver.resolveQueue(q, syncedAt)
        }
        handoffIdleMiniplayerIfNewer(body.history)
        listeningSync?.let { sync -> pushMutex.withLock { sync.applyPull(body) } }
        requireBoxcastPrefs.setLastSyncTimestamp(syncedAt)
    }

    open suspend fun deleteCloudSyncData(): Result<Unit> = withContext(ioDispatcher) {
        syncMutex.withLock {
            runCatching {
                val userId = authUserIdProvider()
                if (userId.isNullOrBlank()) {
                    return@runCatching
                }
                val token = tokenProvider() ?: error("Missing authentication token for cloud sync data deletion")
                val call = requireBoxLoreApi.deleteSyncAccount(
                    publicKey = publicKey,
                    authorization = "Bearer $token",
                )
                val response = call.execute()
                val code = response.code()
                if (!response.isSuccessful && code != 404) {
                    error("Delete cloud sync data failed with HTTP $code: ${response.errorBody()?.string()}")
                }
                requireBoxcastPrefs.setLastSyncTimestamp(0L)
                requireBoxcastPrefs.setLastSyncedUserId(null)
                // The listening this phone holds goes up again to whichever account it syncs with next.
                resetListeningSync()
            }
        }
    }

    /**
     * Records the identity of every pulled history and queue row ([SyncEpisodeIdentity.recordIncoming])
     * before the resolvers merge them, so a row stored here under another ID of the same episode
     * is already one episode with it.
     *
     * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, cloud sync identity).
     */
    private suspend fun recordPulledIdentities(body: SyncPullResponse) {
        val identity = episodeIdentity ?: return
        val rows = body.history.map { SyncEpisodeIdentity.IncomingRow(it.episodeId, it.podcastId, it.recipeId, it.catalogKey) } +
            body.queue?.items.orEmpty().map { SyncEpisodeIdentity.IncomingRow(it.episodeId, it.podcastId, it.recipeId, it.catalogKey) }
        identity.recordIncoming(rows)
    }

    private suspend fun handoffIdleMiniplayerIfNewer(history: List<ListeningHistorySyncDto>) {
        val newestRemoteHistory = history.maxByOrNull { it.lastPlayedAt }
        if (newestRemoteHistory != null && newestRemoteHistory.lastPlayedAt > 0L) {
            // The episode's owner ID, so the mini-player opens the episode under the ID this phone plays it as.
            val episodeId = episodeIdentity?.localOwner(newestRemoteHistory.episodeId) ?: newestRemoteHistory.episodeId
            activePlaybackSyncPort?.updateIdlePlaybackSession(
                episodeId = episodeId,
                positionMs = newestRemoteHistory.progressMs,
                lastPlayedAt = newestRemoteHistory.lastPlayedAt,
            )
        }
    }

    private data class PendingPushBatch(
        val dirtyPodcasts: List<PodcastEntity>,
        val dirtyHistory: List<ListeningHistoryEntity>,
        val queueMeta: QueueMetadataEntity?,
        val queueDirty: Boolean,
        val queueItems: List<QueueItem>,
        val listening: ListeningSync.Outgoing? = null,
    )

    data class PushBatchSummary(
        val pushedSubscriptions: Int,
        val pushedHistory: Int,
        val pushedQueue: Boolean,
        val syncedAt: Long,
    ) {
        /** Both pushes together, as one sync reports them. */
        operator fun plus(other: PushBatchSummary): PushBatchSummary = PushBatchSummary(
            pushedSubscriptions = pushedSubscriptions + other.pushedSubscriptions,
            pushedHistory = pushedHistory + other.pushedHistory,
            pushedQueue = pushedQueue || other.pushedQueue,
            syncedAt = maxOf(syncedAt, other.syncedAt),
        )
    }

    companion object {
        const val MAX_SUBSCRIPTION_BATCH_SIZE = 100
        const val MAX_HISTORY_BATCH_SIZE = 100

        /**
         * Sync metadata version recorded once the one-time identity catch-up has completed (a pull
         * from the start, then every push of the cloud copies that gain an identity). An account
         * switch sets it back, so the new account's rows are caught up too. Added in P4 PR 11 of
         * the own-episode-IDs plan (plan section 8, the one-time sync catch-up).
         */
        const val IDENTITY_SYNC_METADATA_VERSION = 3

        /**
         * The most pushes the identity catch-up sends in one sync (10,000 history rows); a larger
         * library finishes on the next syncs, each pulling from the start and skipping rows done.
         */
        const val MAX_CATCH_UP_PUSHES = 100
    }
}
