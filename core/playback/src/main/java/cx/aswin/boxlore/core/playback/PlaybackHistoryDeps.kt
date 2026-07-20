package cx.aswin.boxlore.core.playback

import android.content.Context
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningInsightsMaintenance
import cx.aswin.boxlore.core.database.ListeningRollupDao
import cx.aswin.boxlore.core.database.ListeningSessionDao
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Player / UI wiring for [PlaybackHistoryStore]. */
internal data class PlaybackHistoryPlayerDeps(
    val context: Context,
    val scope: CoroutineScope,
    val playerState: StateFlow<PlayerState>,
    val playerStateFlow: MutableStateFlow<PlayerState>,
)

/** Persistence and catalog wiring for [PlaybackHistoryStore]. */
internal data class PlaybackHistoryDataDeps(
    val listeningHistoryDao: ListeningHistoryDao,
    val listeningSessionDao: ListeningSessionDao,
    val listeningRollupDao: ListeningRollupDao,
    val listeningInsightsMaintenance: ListeningInsightsMaintenance,
    val podcastRepository: PodcastRepository,
    val rankingFeedbackRepository: RankingFeedbackRepository,
)
