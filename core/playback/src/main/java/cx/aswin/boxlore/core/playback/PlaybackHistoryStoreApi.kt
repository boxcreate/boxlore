package cx.aswin.boxlore.core.playback

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeListenerStore
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Non-port [PlaybackHistoryStore] API kept off the class for TooManyFunctions. */
internal val PlaybackHistoryStore.lastPlayedSession: Flow<PlaybackSession?>
    get() =
        projectedResumeItems()
            .map { historyList ->
                val latest = historyList.firstOrNull()
                if (latest != null) {
                    PlaybackSessionMapping.fromHistoryEntity(latest)
                } else {
                    null
                }
            }

internal val PlaybackHistoryStore.resumeSessions: Flow<List<PlaybackSession>>
    get() =
        projectedResumeItems()
            .map { historyList ->
                historyList.map { entity ->
                    PlaybackSessionMapping.fromHistoryEntity(entity)
                }
            }

/** Resume rows with each episode once and its projected state; a projected completion drops it. */
private fun PlaybackHistoryStore.projectedResumeItems(): Flow<List<ListeningHistoryEntity>> =
    listenerStore.projectedRows(data.listeningHistoryDao.getResumeItems()) { !it.isCompleted && it.progressMs > 0 }

internal val PlaybackHistoryStore.likedEpisodes: Flow<List<ListeningHistoryEntity>>
    get() = listenerStore.projectedRows(data.listeningHistoryDao.getLikedEpisodes()) { it.isLiked }

/**
 * Every stored ID of each liked episode, for screens that ask "is this ID liked": an episode
 * stored under several IDs is liked under all of them or none (own-episode-IDs P4, plan section 8).
 */
internal val PlaybackHistoryStore.likedEpisodeIds: Flow<Set<String>>
    get() = listenerStore.projectedLikedIds(data.listeningHistoryDao.getLikedEpisodes())

/**
 * Recent history for looking an episode up by the ID a screen shows: as [PlaybackHistoryStore.getAllHistory],
 * but an episode stored under several IDs appears once per stored ID with its projected state, so
 * its progress shows whichever ID the screen lists. Not for lists, which count it once.
 */
internal val PlaybackHistoryStore.historyForEpisodeLookup: Flow<List<ListeningHistoryEntity>>
    get() = listenerStore.projectedRowsByMember(data.listeningHistoryDao.getAllHistory())

/** Completed IDs as projected: an episode stored under several IDs is completed for all or none of them. */
internal val PlaybackHistoryStore.completedEpisodeIds: Flow<Set<String>>
    get() = listenerStore.projectedCompletedIds(data.listeningHistoryDao.getCompletedEpisodeIdsFlow())

internal suspend fun PlaybackHistoryStore.recordListeningSession(session: ListeningSessionEntity) {
    ListeningSessionRecordLogic.persistSessionAndRollUp(
        sessions = data.listeningSessionDao,
        maintenance = data.listeningInsightsMaintenance,
        session = session,
    )
}

/**
 * Flips the like of [episode]'s episode. The target is worked out from the projected state and
 * written to every trusted ID of the episode and its group settings ([EpisodeListenerStore.setLiked]);
 * with no row yet, the owner's row is created. An episode stored under one ID behaves as before.
 */
internal suspend fun PlaybackHistoryStore.toggleLike(episode: Episode, podcastId: String, podcastTitle: String, podcastImageUrl: String?,) {
    val dao = data.listeningHistoryDao
    val existing = listenerStore.projectedHistory(episode.id)
    val newStatus = !(existing?.isLiked ?: false)
    val stateFlow = player.playerStateFlow

    if (stateFlow.value.currentEpisode?.id == episode.id) {
        stateFlow.value = stateFlow.value.copy(isLiked = newStatus)
    }

    val now = System.currentTimeMillis()
    if (listenerStore.setLiked(episode.id, newStatus, now) == 0) {
        val built =
            ListeningHistoryEntity(
                episodeId = listenerStore.historyId(episode.id),
                podcastId = podcastId,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = podcastImageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = podcastTitle,
                progressMs = 0L,
                durationMs = episode.duration * 1000L,
                isCompleted = false,
                isLiked = newStatus,
                likedAt = if (newStatus) now else 0L,
                lastPlayedAt = 0L,
                isDirty = true,
                enclosureType = episode.enclosureType,
                episodeDescription = episode.description,
            )
        // The owner of an episode stored under several IDs keeps its own show.
        dao.upsert(listenerStore.ownerRow(built, episode.id))
    }
    data.rankingFeedbackRepository.recordAction(
        target =
        FeedbackTarget(
            episodeId = episode.id,
            podcastId = podcastId,
            genre = episode.podcastGenre,
        ),
        action = if (newStatus) RankingAction.LIKE else RankingAction.UNLIKE,
    )
    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackEpisodeLikedToggled(
        episodeId = episode.id,
        podcastId = podcastId,
        isLiked = newStatus,
    )
}

/**
 * Flips the played state of [episode]'s episode. The target comes from the projected state; the
 * owner's row is written as before, and the target reaches the episode's other trusted IDs and its
 * group settings ([EpisodeListenerStore.recordCompletion]).
 */
internal suspend fun PlaybackHistoryStore.toggleCompletion(
    episode: Episode,
    podcastId: String,
    podcastTitle: String,
    podcastImageUrl: String?,
) {
    val dao = data.listeningHistoryDao
    val historyId = listenerStore.historyId(episode.id)
    val projected = listenerStore.projectedHistory(episode.id)
    val newStatus = !(projected?.isCompleted ?: false)
    val existing = dao.getHistoryItem(historyId)
    val now = System.currentTimeMillis()

    if (existing != null) {
        val updated =
            existing.copy(
                isCompleted = newStatus,
                isManualCompletion = newStatus,
                progressMs = 0L,
                lastPlayedAt = now,
                isDirty = true,
                episodeDescription = existing.episodeDescription ?: episode.description,
            )
        dao.upsert(updated)
    } else {
        val entity =
            ListeningHistoryEntity(
                episodeId = historyId,
                podcastId = podcastId,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = podcastImageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = podcastTitle,
                progressMs = 0L,
                durationMs = episode.duration * 1000L,
                isCompleted = newStatus,
                isLiked = false,
                lastPlayedAt = now,
                isDirty = true,
                enclosureType = episode.enclosureType,
                isManualCompletion = newStatus,
                isBulkCompletion = false,
                episodeDescription = episode.description,
            )
        dao.upsert(listenerStore.ownerRow(entity, episode.id))
    }
    listenerStore.recordCompletion(historyId, newStatus, now)
    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackEpisodeMarkPlayed(
        episodeId = episode.id,
        podcastId = podcastId,
        isPlayed = newStatus,
    )
}

/**
 * Marks [episode]'s episode played on its owner's row (created when missing), then on the episode's
 * other trusted IDs and its group settings ([EpisodeListenerStore.recordCompletion]).
 */
internal suspend fun PlaybackHistoryStore.markEpisodeAsCompleted(episode: Episode, podcast: Podcast?,) {
    android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: START for ${episode.title}")
    val dao = data.listeningHistoryDao
    val historyId = listenerStore.historyId(episode.id)
    dao.setCompletionStatus(historyId, true)
    val existing = dao.getHistoryItem(historyId)

    if (existing == null) {
        if (podcast == null && episode.podcastId == null) {
            android.util.Log.e("PlaybackRepo", "markEpisodeAsCompleted: Cannot create history item, podcast is null")
            return
        }
        android.util.Log.d("PlaybackRepo", "Creating new history item for completed episode")
        val entity =
            ListeningHistoryEntity(
                episodeId = historyId,
                podcastId = episode.podcastId ?: podcast!!.id,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = episode.podcastImageUrl ?: podcast?.imageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName =
                episode.podcastTitle.orEmpty().ifBlank {
                    podcast?.title ?: "Unknown Podcast"
                },
                progressMs = 0L,
                durationMs = episode.duration * 1000L,
                isCompleted = true,
                isLiked = false,
                lastPlayedAt = System.currentTimeMillis(),
                isDirty = true,
                enclosureType = episode.enclosureType,
                episodeDescription = episode.description,
            )
        dao.upsert(listenerStore.ownerRow(entity, episode.id))
    } else {
        android.util.Log.d("PlaybackRepo", "Updating existing history item as completed")
        val updated =
            existing.copy(
                isCompleted = true,
                progressMs = 0L,
                lastPlayedAt = System.currentTimeMillis(),
                isDirty = true,
                episodeDescription = existing.episodeDescription ?: episode.description,
            )
        dao.upsert(updated)
    }
    listenerStore.recordCompletion(historyId, completed = true, nowMs = System.currentTimeMillis())
    android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: DONE for ${episode.title}")
}

/**
 * Creates the history row needed by the service-owned progress writer, under the episode's owner
 * ID when the episode is stored under several IDs. Existing rows are never replaced from UI state,
 * because that state intentionally stops polling in the background.
 */
internal suspend fun PlaybackHistoryStore.ensureCurrentHistoryRow() {
    val state = player.playerStateFlow.value
    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast ?: return
    val dao = data.listeningHistoryDao
    val built =
        ListeningHistoryUpsertLogic.buildProgressSaveEntity(
            ListeningHistoryUpsertLogic.ProgressSaveInput(
                podcastId = podcast.id,
                episodeId = listenerStore.historyId(episode.id),
                positionMs = state.position.coerceAtLeast(0L),
                durationMs =
                state.duration.takeIf { it > 0L }
                    ?: episode.duration.toLong().coerceAtLeast(0L) * 1_000L,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = podcast.imageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = podcast.title,
                isCompleted = false,
                isLiked = state.isLiked,
                lastPlayedAt = System.currentTimeMillis(),
                enclosureType = episode.enclosureType,
                episodeDescription = episode.description,
            ),
        )
    // A seeded owner row of an episode stored under several IDs carries the owner's show.
    val history = listenerStore.ownerRow(built, episode.id)
    val inserted = dao.insertIfAbsent(history)
    if (inserted == -1L) {
        dao.updateLastPlayedAt(history.episodeId, history.lastPlayedAt)
    }
    dao.enrichMetadataIfMissing(
        episodeId = history.episodeId,
        podcastId = history.podcastId,
        episodeTitle = history.episodeTitle,
        episodeImageUrl = history.episodeImageUrl,
        podcastImageUrl = history.podcastImageUrl,
        episodeAudioUrl = history.episodeAudioUrl,
        podcastName = history.podcastName,
        durationMs = history.durationMs,
        enclosureType = history.enclosureType,
        episodeDescription = history.episodeDescription,
    )
}

/**
 * Saves a position chosen outside the player (a seek on an episode page). An episode stored under
 * one ID writes its row as before. An episode stored under several IDs writes its owner's row and
 * keeps that row's like, show and episode metadata as stored: the caller's like flag and show may
 * come from a screen showing another of the episode's IDs (the RSS show a Podcast Index show was
 * switched to), only the like button changes a like, and the owner row stays under its own show. A
 * missing owner row is created under the owner's show ([EpisodeListenerStore.ownerRow]).
 */
internal suspend fun PlaybackHistoryStore.savePlaybackState(input: ListeningHistoryUpsertLogic.ProgressSaveInput) {
    android.util.Log.v(
        "PlaybackRepo",
        "Saving playback state: ${input.episodeTitle}, pos=${input.positionMs}, completed=${input.isCompleted}",
    )
    val historyId = listenerStore.historyId(input.episodeId)
    val entity = ListeningHistoryUpsertLogic.buildProgressSaveEntity(input.copy(episodeId = historyId))
    if (!listenerStore.group(input.episodeId).isShared) {
        data.listeningHistoryDao.upsert(entity)
        return
    }
    val stored = data.listeningHistoryDao.getHistoryItem(historyId)
    val row =
        if (stored != null) {
            listenerStore.keepingOwnerMetadata(entity, stored).copy(isLiked = stored.isLiked, likedAt = stored.likedAt)
        } else {
            listenerStore.ownerRow(entity.copy(isLiked = false, likedAt = 0L), input.episodeId)
        }
    data.listeningHistoryDao.upsert(row)
}

internal suspend fun PlaybackHistoryStore.toggleLike() {
    val state = player.playerStateFlow.value
    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast ?: return
    toggleLike(episode, podcast.id, podcast.title, podcast.imageUrl)
}

internal suspend fun PlaybackHistoryStore.deleteSession(episodeId: String) {
    data.listeningHistoryDao.delete(episodeId)
}

internal suspend fun PlaybackHistoryStore.getSession(episodeId: String): PlaybackSession? {
    val entity = listenerStore.projectedHistory(episodeId) ?: return null
    return PlaybackSessionMapping.fromHistoryEntity(entity)
}

/** Recent history with each episode once and its projected state. */
internal suspend fun PlaybackHistoryStore.getRecentHistoryList(limit: Int): List<ListeningHistoryEntity> =
    listenerStore.projectRows(data.listeningHistoryDao.getRecentHistoryList(limit))

/**
 * Marks [episodes] unplayed on their owners' rows, then on each episode's other trusted IDs and
 * group settings ([EpisodeListenerStore.recordCompletion]), in one transaction.
 */
internal suspend fun PlaybackHistoryStore.markAllEpisodesUncompleted(episodes: List<Episode>) {
    val currentTime = System.currentTimeMillis()
    BoxLoreDatabase.getDatabase(player.context).withTransaction {
        val historyIds = listenerStore.historyIds(episodes.map { it.id }).values.distinct()
        val entitiesToUpsert =
            historyIds.mapNotNull { historyId ->
                val existing = data.listeningHistoryDao.getHistoryItem(historyId) ?: return@mapNotNull null
                ListeningHistoryUpsertLogic.buildBulkUncompleteEntity(
                    existing = existing,
                    nowMs = currentTime,
                )
            }
        if (entitiesToUpsert.isNotEmpty()) {
            data.listeningHistoryDao.upsertAll(entitiesToUpsert)
        }
        listenerStore.recordCompletions(historyIds, completed = false, nowMs = currentTime)
    }
}

internal suspend fun PlaybackHistoryStore.findPodcastIdForEpisode(episodeId: String): String? {
    val historyItem = data.listeningHistoryDao.getHistoryItem(episodeId)
    if (historyItem != null) return historyItem.podcastId
    return data.podcastRepository.getEpisode(episodeId)?.podcastId
}

internal suspend fun PlaybackHistoryStore.getHistoryForRecommendations(limit: Int = 15): List<HistoryItem> {
    val database = BoxLoreDatabase.getDatabase(player.context)
    val podcastDao = database.podcastDao()
    val rawHistory = listenerStore.projectRows(data.listeningHistoryDao.getRecentHistoryList(limit * 3))
    return HistoryRecommendationLogic
        .selectEligible(
            raw = rawHistory,
            limit = limit,
        ) { entity ->
            HistoryRecommendationLogic.isEligible(
                isManualCompletion = entity.isManualCompletion,
                isBulkCompletion = entity.isBulkCompletion,
                progressMs = entity.progressMs,
                isCompleted = entity.isCompleted,
            )
        }.map { entity ->
            val podcast = podcastDao.getPodcast(entity.podcastId)
            android.util.Log.d(
                "PlaybackRepo",
                "Passing history: ${entity.episodeTitle} | Has Description: ${!entity.episodeDescription.isNullOrEmpty()} | Length: ${entity.episodeDescription?.length ?: 0}",
            )
            HistoryItem(
                podcastTitle = entity.podcastName,
                episodeTitle = entity.episodeTitle,
                podcastId = entity.podcastId,
                episodeId = entity.episodeId,
                genre = podcast?.genre,
                durationMs = entity.durationMs,
                progressMs = entity.progressMs,
                isCompleted = entity.isCompleted,
                isLiked = entity.isLiked,
                episodeDescription = entity.episodeDescription,
            )
        }
}
