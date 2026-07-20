package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.HistoryItem
import kotlinx.coroutines.flow.Flow

/** History / like / completion [PlaybackRepository] API (non-port methods). */
val PlaybackRepository.lastPlayedSession: Flow<PlaybackSession?>
    get() = historyStore.lastPlayedSession

val PlaybackRepository.resumeSessions: Flow<List<PlaybackSession>>
    get() = historyStore.resumeSessions

val PlaybackRepository.likedEpisodes: Flow<List<ListeningHistoryEntity>>
    get() = historyStore.likedEpisodes

val PlaybackRepository.completedEpisodeIds: Flow<Set<String>>
    get() = historyStore.completedEpisodeIds

suspend fun PlaybackRepository.recordListeningSession(session: ListeningSessionEntity) =
    historyStore.recordListeningSession(session)

suspend fun PlaybackRepository.toggleLike(
    episode: Episode,
    podcastId: String,
    podcastTitle: String,
    podcastImageUrl: String?,
) = historyStore.toggleLike(episode, podcastId, podcastTitle, podcastImageUrl)

suspend fun PlaybackRepository.toggleCompletion(
    episode: Episode,
    podcastId: String,
    podcastTitle: String,
    podcastImageUrl: String?,
) = historyStore.toggleCompletion(episode, podcastId, podcastTitle, podcastImageUrl)

suspend fun PlaybackRepository.savePlaybackState(input: ListeningHistoryUpsertLogic.ProgressSaveInput) =
    historyStore.savePlaybackState(input)

suspend fun PlaybackRepository.toggleLike() = historyStore.toggleLike()

suspend fun PlaybackRepository.deleteSession(episodeId: String) = historyStore.deleteSession(episodeId)

suspend fun PlaybackRepository.getSession(episodeId: String): PlaybackSession? =
    historyStore.getSession(episodeId)

suspend fun PlaybackRepository.getRecentHistoryList(limit: Int): List<ListeningHistoryEntity> =
    historyStore.getRecentHistoryList(limit)

suspend fun PlaybackRepository.markAllEpisodesUncompleted(episodes: List<Episode>) =
    historyStore.markAllEpisodesUncompleted(episodes)

suspend fun PlaybackRepository.getHistoryForRecommendations(limit: Int = 15): List<HistoryItem> =
    historyStore.getHistoryForRecommendations(limit)
