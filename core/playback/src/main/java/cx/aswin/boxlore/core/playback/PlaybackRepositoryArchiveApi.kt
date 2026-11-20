package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

/*
 * Archive [PlaybackRepository] API: an archived episode leaves every list without being marked
 * played (`listening_history.dismissedAt`, docs in `:core:database`).
 */

/**
 * Every archived ID as projected: an episode stored under several IDs is archived under all of them
 * or none. One read shared by every screen; lists leave these episodes out.
 */
val PlaybackRepository.dismissedEpisodeIds: Flow<Set<String>>
    get() = historyStore.dismissedEpisodeIds

/**
 * How many episodes of the shows [podcastIds] (a show and the show it was switched from) are
 * archived, counted from the stored decisions, so a show page knows before every page is loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun PlaybackRepository.dismissedEpisodeCount(podcastIds: Set<String>): Flow<Int> =
    historyStore.dismissedEpisodeIds.mapLatest { historyStore.listenerStore.dismissedEpisodeCount(it, podcastIds) }.distinctUntilChanged()

/** [dismissedEpisodeIds] read once, for one-off picks (Smart Queue, Android Auto, widgets). */
suspend fun PlaybackRepository.getDismissedEpisodeIds(): Set<String> = historyStore.listenerStore.dismissedIds()

/**
 * Archives [episodes] of one show, or brings them back when [dismissed] is false. Archiving never
 * marks an episode played nor changes its progress.
 */
suspend fun PlaybackRepository.setEpisodesDismissed(
    episodes: List<Episode>,
    podcastId: String,
    podcastTitle: String,
    podcastImageUrl: String?,
    dismissed: Boolean,
) = historyStore.setEpisodesDismissed(episodes, podcastId, podcastTitle, podcastImageUrl, dismissed)
