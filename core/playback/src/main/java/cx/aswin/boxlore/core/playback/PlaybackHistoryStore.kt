package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.withTransaction
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningInsightsMaintenance
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Listening history / like / completion store for [PlaybackRepository].
 * Implements [ListeningHistoryPort] and [ListeningHistoryBackupPort] so the repository
 * can expose those seams via Kotlin class delegation.
 *
 * Non-port helpers live in [PlaybackHistoryStoreApi] extensions.
 */
internal class PlaybackHistoryStore(
    internal val player: PlaybackHistoryPlayerDeps,
    internal val data: PlaybackHistoryDataDeps,
) : ListeningHistoryPort, ListeningHistoryBackupPort {
    /** Exposed so [PlaybackRepository] can share scope / player state created with the store. */
    internal val playerDeps: PlaybackHistoryPlayerDeps get() = player

    internal var likeStateObserverJob: Job? = null

    fun monitorLikeState() {
        val dao = data.listeningHistoryDao
        val stateFlow = player.playerStateFlow
        player.scope.launch {
            player.playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    likeStateObserverJob?.cancel()
                    if (episodeId != null) {
                        likeStateObserverJob =
                            launch {
                                dao.getHistoryItemFlow(episodeId).collect { history ->
                                    if (history == null) {
                                        stateFlow.value = stateFlow.value.copy(isLiked = false, isCompleted = false)
                                    } else {
                                        if (stateFlow.value.isLiked != history.isLiked ||
                                            stateFlow.value.isCompleted != history.isCompleted
                                        ) {
                                            stateFlow.value =
                                                stateFlow.value.copy(
                                                    isLiked = history.isLiked,
                                                    isCompleted = history.isCompleted,
                                                )
                                        }
                                    }
                                }
                            }
                    }
                }
        }
    }

    override fun getAllHistory(): Flow<List<ListeningHistoryEntity>> =
        data.listeningHistoryDao.getAllHistory()

    override fun observeHistoryTimeline(): Flow<List<ListeningHistoryItem>> =
        data.listeningHistoryDao.getAllHistory().map { entities ->
            entities
                .filter { !it.isManualCompletion && !it.isBulkCompletion }
                .map { it.toListeningHistoryItem() }
        }

    override fun observeInsights(period: ListeningPeriod): Flow<ListeningInsightSummary> =
        combine(
            data.listeningSessionDao.observeAllSessions(),
            data.listeningRollupDao.observeAllRollups(),
            data.listeningHistoryDao.getAllHistory(),
        ) { sessions, rollups, history ->
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

    override suspend fun removeHistoryItem(episodeId: String): ListeningHistoryRemoval? {
        val existing = data.listeningHistoryDao.getHistoryItem(episodeId) ?: return null
        val database = BoxLoreDatabase.getDatabase(player.context)
        return database.withTransaction {
            val sessions = data.listeningSessionDao.getSessionsForEpisode(episodeId)
            val rollups = data.listeningRollupDao.getRollupsForEpisode(episodeId)
            val removal =
                ListeningHistoryRemoval(
                    item = existing.toListeningHistoryItem(),
                    sessions = sessions.map { it.toListeningSessionSnapshot() },
                    rollups = rollups.map { it.toListeningRollupSnapshot() },
                )
            data.listeningHistoryDao.delete(episodeId)
            data.listeningInsightsMaintenance.deleteEpisodeAnalytics(episodeId)
            removal
        }
    }

    override suspend fun restoreHistoryRemoval(removal: ListeningHistoryRemoval) {
        val item = removal.item
        val database = BoxLoreDatabase.getDatabase(player.context)
        database.withTransaction {
            data.listeningHistoryDao.upsert(item.toListeningHistoryEntity())
            if (removal.sessions.isNotEmpty()) {
                data.listeningSessionDao.upsertSessions(removal.sessions.map { it.toListeningSessionEntity() })
            }
            if (removal.rollups.isNotEmpty()) {
                data.listeningRollupDao.upsertRollups(removal.rollups.map { it.toListeningRollupEntity() })
            }
        }
    }

    override suspend fun clearHistory() {
        val database = BoxLoreDatabase.getDatabase(player.context)
        database.withTransaction {
            data.listeningHistoryDao.deleteAll()
            data.listeningInsightsMaintenance.clearAllAnalytics()
        }
        data.rankingFeedbackRepository.reset()
    }

    override suspend fun markAllEpisodesCompleted(
        episodes: List<Episode>,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
        val currentTime = System.currentTimeMillis()
        val entitiesToUpsert =
            episodes.map { episode ->
                ListeningHistoryUpsertLogic.buildBulkCompleteEntity(
                    episode = episode,
                    podcastId = podcastId,
                    podcastTitle = podcastTitle,
                    podcastImageUrl = podcastImageUrl,
                    existing = data.listeningHistoryDao.getHistoryItem(episode.id),
                    nowMs = currentTime,
                )
            }
        data.listeningHistoryDao.upsertAll(entitiesToUpsert)
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
