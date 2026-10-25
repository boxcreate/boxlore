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

/** Every stored ID of each liked episode; an episode stored under several IDs is liked under all of them or none. */
val PlaybackRepository.likedEpisodeIds: Flow<Set<String>>
    get() = historyStore.likedEpisodeIds

/**
 * Recent history for looking an episode up by the ID a screen shows: an episode stored under
 * several IDs appears once per stored ID with its projected state. Lists use `getAllHistory`.
 */
val PlaybackRepository.historyForEpisodeLookup: Flow<List<ListeningHistoryEntity>>
    get() = historyStore.historyForEpisodeLookup

/**
 * A token per ID, equal for every stored ID of one episode and different between episodes; an ID
 * that shares its episode with no other ID is its own token. Finds an episode that a list shows
 * under one ID in history kept under another, such as the episodes of a show switched to its feed
 * (own-episode-IDs P4, plan section 8; decision D8).
 */
suspend fun PlaybackRepository.episodeGroupTokens(episodeIds: Collection<String>): Map<String, String> =
    historyStore.listenerStore.groupTokens(episodeIds)

/**
 * For every ID of an episode stored under several IDs, all of its IDs (re-emitted when the aliases
 * change), so a screen can find a history row, kept under the owner's ID, under the ID it lists.
 */
val PlaybackRepository.episodeAliasGroups: Flow<Map<String, Set<String>>>
    get() = historyStore.listenerStore.aliasGroupsFlow()

/**
 * [episodeIds] plus every other stored ID of their episodes, re-evaluated when either changes, so a
 * screen reads a queue entry kept under another ID of the episode it shows (decision D8).
 */
fun PlaybackRepository.withEpisodeAliases(episodeIds: Flow<Set<String>>): Flow<Set<String>> =
    historyStore.listenerStore.withAllAliasesFlow(episodeIds)

suspend fun PlaybackRepository.recordListeningSession(session: ListeningSessionEntity) = historyStore.recordListeningSession(session)

suspend fun PlaybackRepository.toggleLike(episode: Episode, podcastId: String, podcastTitle: String, podcastImageUrl: String?,) = historyStore.toggleLike(episode, podcastId, podcastTitle, podcastImageUrl)

suspend fun PlaybackRepository.toggleCompletion(episode: Episode, podcastId: String, podcastTitle: String, podcastImageUrl: String?,) = historyStore.toggleCompletion(episode, podcastId, podcastTitle, podcastImageUrl)

suspend fun PlaybackRepository.savePlaybackState(input: ListeningHistoryUpsertLogic.ProgressSaveInput) = historyStore.savePlaybackState(input)

suspend fun PlaybackRepository.toggleLike() = historyStore.toggleLike()

suspend fun PlaybackRepository.deleteSession(episodeId: String) = historyStore.deleteSession(episodeId)

suspend fun PlaybackRepository.getSession(episodeId: String): PlaybackSession? = historyStore.getSession(episodeId)

suspend fun PlaybackRepository.getRecentHistoryList(limit: Int): List<ListeningHistoryEntity> = historyStore.getRecentHistoryList(limit)

suspend fun PlaybackRepository.markAllEpisodesUncompleted(episodes: List<Episode>) = historyStore.markAllEpisodesUncompleted(episodes)

suspend fun PlaybackRepository.getHistoryForRecommendations(limit: Int = 15): List<HistoryItem> = historyStore.getHistoryForRecommendations(limit)
