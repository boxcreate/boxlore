package cx.aswin.boxlore.core.model

/**
 * UI/domain-facing listening history row. Room entities stay behind the playback boundary.
 */
data class ListeningHistoryItem(
    val episodeId: String,
    val podcastId: String,
    val episodeTitle: String,
    val episodeImageUrl: String?,
    val podcastImageUrl: String?,
    val episodeAudioUrl: String?,
    val podcastName: String,
    val progressMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val isLiked: Boolean,
    val lastPlayedAt: Long,
    val enclosureType: String? = null,
    val episodeDescription: String? = null,
)

enum class ListeningPeriod {
    DAYS_7,
    DAYS_30,
    DAYS_180,
    ALL,
}

enum class ListeningTimeBucket {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

data class ListeningTopShow(
    val podcastId: String,
    val podcastName: String,
    val podcastImageUrl: String?,
    val consumedMs: Long,
    val sessionCount: Int,
)

/** One local calendar day's listening total for activity charts. */
data class ListeningDayActivity(val localDay: Long, val consumedMs: Long,)

data class ListeningInsightSummary(
    val period: ListeningPeriod,
    val trackingSinceEpochMs: Long?,
    /** Seek-excluding consumed audio from sessions/rollups (precise). */
    val totalConsumedMs: Long,
    val previousPeriodConsumedMs: Long,
    /**
     * Progress-based library estimate used only when precise session data is not yet available.
     * Never mixed into [totalConsumedMs].
     */
    val estimatedLibraryMs: Long = 0L,
    val completedCount: Int,
    val inProgressCount: Int,
    val likedCount: Int,
    val sessionCount: Int,
    val averageSessionMs: Long,
    val longestSessionMs: Long,
    val streakDays: Int,
    val activeDaysInPeriod: Int,
    val peakBucket: ListeningTimeBucket?,
    val morningMs: Long,
    val afternoonMs: Long,
    val eveningMs: Long,
    val nightMs: Long,
    val topShow: ListeningTopShow?,
    /** Per-day totals for the selected period (precise when available, else estimated). */
    val dailyActivity: List<ListeningDayActivity> = emptyList(),
    /** True when at least one precise session/rollup contributes to listening time. */
    val hasEnoughData: Boolean,
)

/** Snapshot of analytics rows for snackbar undo after per-episode history removal. */
data class ListeningSessionSnapshot(
    val sessionId: String,
    val episodeId: String,
    val podcastId: String,
    val startedAt: Long,
    val endedAt: Long,
    val consumedMs: Long,
    val completed: Boolean,
    val localDay: Long,
    val timeBucket: Int,
)

data class ListeningRollupSnapshot(
    val localDay: Long,
    val episodeId: String,
    val podcastId: String,
    val consumedMs: Long,
    val sessionCount: Int,
    val completionCount: Int,
    val lastListenedAt: Long,
    val morningMs: Long,
    val afternoonMs: Long,
    val eveningMs: Long,
    val nightMs: Long,
)

/**
 * What removing one episode from history took away, so Undo can put it back.
 *
 * @property item the removed row the listener chose
 * @property sessions listening sessions of every removed row
 * @property rollups daily listening totals of every removed row
 * @property otherItems rows of the same episode stored under its other IDs, removed with [item]
 *   so the episode doesn't stay in history through another ID (own-episode-IDs P4, plan section 8)
 */
data class ListeningHistoryRemoval(
    val item: ListeningHistoryItem,
    val sessions: List<ListeningSessionSnapshot> = emptyList(),
    val rollups: List<ListeningRollupSnapshot> = emptyList(),
    val otherItems: List<ListeningHistoryItem> = emptyList(),
)

object ListeningCompletionLogic {
    fun isCompleted(isCompleted: Boolean, progressMs: Long, durationMs: Long,): Boolean = isCompleted || (durationMs > 0 && progressMs > durationMs * 0.9f)

    fun isInProgress(isCompleted: Boolean, progressMs: Long, durationMs: Long,): Boolean = !isCompleted(isCompleted, progressMs, durationMs) && progressMs > 0
}
