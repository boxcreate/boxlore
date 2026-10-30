package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.withTransaction
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeListenerStore
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningInsightsMaintenance
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/**
 * Listening history / like / completion store for [PlaybackRepository].
 * Implements [ListeningHistoryPort] and [ListeningHistoryBackupPort] so the repository
 * can expose those seams via Kotlin class delegation.
 *
 * Non-port helpers live in [PlaybackHistoryStoreApi] extensions.
 */
internal class PlaybackHistoryStore(internal val player: PlaybackHistoryPlayerDeps, internal val data: PlaybackHistoryDataDeps,) :
    ListeningHistoryPort,
    ListeningHistoryBackupPort {
    /** Exposed so [PlaybackRepository] can share scope / player state created with the store. */
    internal val playerDeps: PlaybackHistoryPlayerDeps get() = player

    internal var likeStateObserverJob: Job? = null

    /**
     * Listener state across an episode's stored IDs: projected reads, owner writes, absolute like
     * and completion writes (own-episode-IDs P4, plan section 8). Every episode stored under one ID
     * reads and writes exactly as before.
     */
    internal val listenerStore: EpisodeListenerStore by lazy {
        data.listenerStore ?: BoxLoreDatabase.getDatabase(player.context).episodeListenerStore()
    }

    /** Mirrors the current episode's projected like and completion into the player state. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun monitorLikeState() {
        val dao = data.listeningHistoryDao
        player.scope.launch {
            player.playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    likeStateObserverJob?.cancel()
                    if (episodeId != null) {
                        likeStateObserverJob =
                            launch {
                                dao
                                    .getHistoryItemFlow(episodeId)
                                    .mapLatest { row -> listenerStore.projectedHistory(episodeId, row) }
                                    .collect { history -> applyLikeAndCompletion(history) }
                            }
                    }
                }
        }
    }

    private fun applyLikeAndCompletion(history: ListeningHistoryEntity?) {
        val stateFlow = player.playerStateFlow
        if (history == null) {
            stateFlow.value = stateFlow.value.copy(isLiked = false, isCompleted = false)
        } else if (stateFlow.value.isLiked != history.isLiked || stateFlow.value.isCompleted != history.isCompleted) {
            stateFlow.value =
                stateFlow.value.copy(
                    isLiked = history.isLiked,
                    isCompleted = history.isCompleted,
                )
        }
    }

    /** Every episode once, with its projected state ([EpisodeListenerStore.projectedRows]). */
    override fun getAllHistory(): Flow<List<ListeningHistoryEntity>> = listenerStore.projectedRows(data.listeningHistoryDao.getAllHistory())

    /** Every stored row, one per episode ID, for library export. */
    override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = data.listeningHistoryDao.getAllStoredHistory()

    override fun observeHistoryTimeline(): Flow<List<ListeningHistoryItem>> = getAllHistory().map { entities ->
        entities
            .filter { !it.isManualCompletion && !it.isBulkCompletion }
            .map { it.toListeningHistoryItem() }
    }

    /**
     * Listening insights for [period]. Listening of a Podcast Index show the listener switched to
     * its RSS feed counts as the RSS show's ([EpisodeListenerStore.showReplacementsFlow]): the
     * switch keeps every row under its stored show (own-episode-IDs P4, plan section 8; decision D8).
     */
    override fun observeInsights(period: ListeningPeriod): Flow<ListeningInsightSummary> = combine(
        data.listeningSessionDao.observeAllSessions(),
        data.listeningRollupDao.observeAllRollups(),
        // All of history, not the screens' latest 300, so counts stay right for long histories.
        listenerStore.projectedCompleteRows(data.listeningHistoryDao.observeAllStoredHistory()),
        listenerStore.showReplacementsFlow(),
    ) { storedSessions, storedRollups, storedHistory, replacedBy ->
        val sessions = storedSessions.map { row -> replacedBy[row.podcastId]?.let { row.copy(podcastId = it) } ?: row }
        val rollups = storedRollups.map { row -> replacedBy[row.podcastId]?.let { row.copy(podcastId = it) } ?: row }
        val history = storedHistory.map { row -> replacedBy[row.podcastId]?.let { row.copy(podcastId = it) } ?: row }
        val timeline =
            history.filter { !it.isManualCompletion && !it.isBulkCompletion }
        val completedCount =
            timeline.count {
                ListeningCompletionLogic.isCompleted(it.isCompleted, it.progressMs, it.durationMs)
            }
        val inProgressCount =
            timeline.count {
                ListeningCompletionLogic.isInProgress(it.isCompleted, it.progressMs, it.durationMs)
            }
        val likedCount = timeline.count { it.isLiked }
        val podcastMeta =
            timeline
                .groupBy { it.podcastId }
                .mapValues { (_, rows) ->
                    val first = rows.first()
                    ListeningInsightsLogic.PodcastMeta(
                        name = first.podcastName,
                        imageUrl = first.podcastImageUrl ?: first.episodeImageUrl,
                    )
                }
        val historyRows =
            timeline.map {
                ListeningInsightsLogic.HistoryActivityRow(
                    podcastId = it.podcastId,
                    podcastName = it.podcastName,
                    podcastImageUrl = it.podcastImageUrl ?: it.episodeImageUrl,
                    progressMs = it.progressMs,
                    durationMs = it.durationMs,
                    isCompletedFlag = it.isCompleted,
                    lastPlayedAt = it.lastPlayedAt,
                )
            }
        val trackingSince =
            listOfNotNull(
                sessions.minOfOrNull { it.endedAt },
                rollups.minOfOrNull { it.lastListenedAt },
            ).minOrNull()
        ListeningInsightsLogic.summarize(
            ListeningInsightsLogic.SummarizeInput(
                period = period,
                sessions = sessions,
                rollups = rollups,
                historyRows = historyRows,
                historyCompleted = completedCount,
                historyInProgress = inProgressCount,
                historyLiked = likedCount,
                podcastMetaById = podcastMeta,
                today = LocalDate.now(),
                trackingSinceEpochMs = trackingSince,
            ),
        )
    }

    override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) {
        data.listeningHistoryDao.upsert(entity)
    }

    override suspend fun maintainListeningAnalytics() {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        data.listeningInsightsMaintenance.rollUpEligibleSessions(
            cutoffEndedAtExclusive =
            ListeningSessionRecordLogic.retentionCutoffEndedAtExclusive(nowMs, zone),
            todayLocalDay = today.toEpochDay(),
        )
    }

    /**
     * Removes [episodeId]'s episode from history: its row and the rows of the episode's other
     * trusted IDs, with their listening sessions and daily totals, so the episode doesn't stay in
     * history through another ID, and the episode's shared like and completion settings, so they
     * can't come back with a later row. The returned removal restores all of it unchanged.
     */
    override suspend fun removeHistoryItem(episodeId: String): ListeningHistoryRemoval? {
        val existing = data.listeningHistoryDao.getHistoryItem(episodeId) ?: return null
        val database = BoxLoreDatabase.getDatabase(player.context)
        return database.withTransaction {
            val otherIds = listenerStore.memberIds(episodeId).filter { it != episodeId }
            val others = if (otherIds.isEmpty()) emptyList() else data.listeningHistoryDao.getHistoryItems(otherIds)
            val removedIds = listOf(episodeId) + others.map { it.episodeId }
            val sessions = removedIds.flatMap { data.listeningSessionDao.getSessionsForEpisode(it) }
            val rollups = removedIds.flatMap { data.listeningRollupDao.getRollupsForEpisode(it) }
            val groupState = listenerStore.removeGroupState(episodeId)
            val removal =
                ListeningHistoryRemoval(
                    item = existing.toListeningHistoryItem(),
                    sessions = sessions.map { it.toListeningSessionSnapshot() },
                    rollups = rollups.map { it.toListeningRollupSnapshot() },
                    rows = if (others.isEmpty()) emptyList() else (listOf(existing) + others).map { it.toRowSnapshot() },
                    groupState = groupState?.toSnapshot(),
                )
            for (id in removedIds) {
                data.listeningHistoryDao.delete(id)
                data.listeningInsightsMaintenance.deleteEpisodeAnalytics(id)
            }
            removal
        }
    }

    /**
     * Puts back what [removeHistoryItem] took away. An episode stored under several IDs gets every
     * row back exactly as it was, with its shared settings; an episode stored under one ID gets its
     * row back as before.
     */
    override suspend fun restoreHistoryRemoval(removal: ListeningHistoryRemoval) {
        val item = removal.item
        val database = BoxLoreDatabase.getDatabase(player.context)
        database.withTransaction {
            if (removal.rows.isEmpty()) {
                data.listeningHistoryDao.upsert(item.toListeningHistoryEntity())
            } else {
                data.listeningHistoryDao.upsertAll(removal.rows.map { it.toListeningHistoryEntity() })
            }
            removal.groupState?.let { listenerStore.restoreGroupState(it.toEntity()) }
            if (removal.sessions.isNotEmpty()) {
                data.listeningSessionDao.upsertSessions(removal.sessions.map { it.toListeningSessionEntity() })
            }
            if (removal.rollups.isNotEmpty()) {
                data.listeningRollupDao.upsertRollups(removal.rollups.map { it.toListeningRollupEntity() })
            }
        }
    }

    /** Deletes all history, its analytics and every episode's shared like and completion settings. */
    override suspend fun clearHistory() {
        val database = BoxLoreDatabase.getDatabase(player.context)
        database.withTransaction {
            data.listeningHistoryDao.deleteAll()
            data.listeningInsightsMaintenance.clearAllAnalytics()
            listenerStore.clearGroupStates()
        }
        data.rankingFeedbackRepository.reset()
    }

    /**
     * Marks [episodes] played in one transaction. Each episode's row is its owner's (an episode
     * stored under one ID writes that ID, as before), and the completion reaches the episode's other
     * trusted IDs and its group settings ([EpisodeListenerStore.recordCompletions]). Owners and
     * settings are worked out in batches, so a large show costs a few queries per 500 episodes.
     */
    override suspend fun markAllEpisodesCompleted(
        episodes: List<Episode>,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
        val currentTime = System.currentTimeMillis()
        val database = BoxLoreDatabase.getDatabase(player.context)
        database.withTransaction {
            val historyIdById = listenerStore.historyIds(episodes.map { it.id })
            val historyIds = episodes.map { historyIdById[it.id] ?: it.id }
            val entitiesToUpsert =
                episodes.mapIndexed { index, episode ->
                    val historyId = historyIds[index]
                    ListeningHistoryUpsertLogic.buildBulkCompleteEntity(
                        episode = episode,
                        podcastId = podcastId,
                        podcastTitle = podcastTitle,
                        podcastImageUrl = podcastImageUrl,
                        existing = data.listeningHistoryDao.getHistoryItem(historyId),
                        nowMs = currentTime,
                    ).copy(episodeId = historyId)
                }
            data.listeningHistoryDao.upsertAll(entitiesToUpsert)
            listenerStore.recordCompletions(historyIds, completed = true, nowMs = currentTime)
        }
    }
}

internal fun defaultPlaybackHistoryStore(
    context: Context,
    listeningHistoryDao: ListeningHistoryDao,
    listeningSessionDao: cx.aswin.boxlore.core.database.ListeningSessionDao,
    listeningRollupDao: cx.aswin.boxlore.core.database.ListeningRollupDao,
    listeningInsightsMaintenance: ListeningInsightsMaintenance,
    podcastRepository: cx.aswin.boxlore.core.catalog.PodcastRepository,
    rankingFeedbackRepository: cx.aswin.boxlore.core.ranking.RankingFeedbackRepository,
): PlaybackHistoryStore {
    val playerStateFlow = kotlinx.coroutines.flow.MutableStateFlow(PlayerState())
    val scope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob(),
        )
    return PlaybackHistoryStore(
        player =
        PlaybackHistoryPlayerDeps(
            context = context,
            scope = scope,
            playerState = playerStateFlow,
            playerStateFlow = playerStateFlow,
        ),
        data =
        PlaybackHistoryDataDeps(
            listeningHistoryDao = listeningHistoryDao,
            listeningSessionDao = listeningSessionDao,
            listeningRollupDao = listeningRollupDao,
            listeningInsightsMaintenance = listeningInsightsMaintenance,
            podcastRepository = podcastRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
        ),
    )
}
