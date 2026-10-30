package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import kotlinx.coroutines.flow.Flow

/**
 * Narrow history + insights seam for Library History UI.
 * Production: [cx.aswin.boxlore.core.playback.PlaybackRepository].
 */
interface ListeningHistoryPort {
    fun observeHistoryTimeline(): Flow<List<ListeningHistoryItem>>

    /**
     * Every episode the listener played, once, most recently played first: the whole history,
     * without the screens' 300-row limit and without descriptions. Episodes only marked played
     * are left out, as in [observeHistoryTimeline].
     */
    fun observeFullHistoryTimeline(): Flow<List<ListeningHistoryItem>>

    /** Timed listening per local day and episode, from every phone ([ListeningDayEpisode]). */
    fun observeListeningByDay(): Flow<List<ListeningDayEpisode>>

    /** Every show the listener played, with its genre when known ([ListeningPlayedShow]). */
    fun observePlayedShows(): Flow<List<ListeningPlayedShow>>

    fun observeInsights(period: ListeningPeriod): Flow<ListeningInsightSummary>

    suspend fun removeHistoryItem(episodeId: String): ListeningHistoryRemoval?

    suspend fun restoreHistoryRemoval(removal: ListeningHistoryRemoval)

    suspend fun clearHistory()

    suspend fun maintainListeningAnalytics()
}
