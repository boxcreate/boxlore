package cx.aswin.boxlore.core.database

/**
 * Whether this row is listening the listener did, for history views and ranking: not only marked
 * played (one episode or "Mark all as played"), and not a row that marking unplayed left with
 * nothing heard (no progress, no listening date, not liked). The SQL twin is `LISTENING_ROW` in
 * [ListeningHistoryDao].
 */
val ListeningHistoryEntity.countsAsListening: Boolean
    get() = !isManualCompletion &&
        !isBulkCompletion &&
        (isCompleted || progressMs > 0L || firstPlayedAt != null || finishedAt != null || isLiked)

/**
 * [ListeningHistoryDao.completedHistorySignal]: changes when a row is completed, reopened or deleted,
 * a completed row's `lastPlayedAt` moves or the row is inserted again, or a shared completion
 * setting changes. Other writes to a completed row (a like, a sync stamp) leave it as it is.
 */
data class CompletedHistorySignal(
    val completedRows: Int,
    val completedPlayedAtSum: Long,
    val newestCompletedRow: Long,
    val groupCompletionSum: Long,
)

/**
 * [ListeningHistoryDao.dismissedHistorySignal]: changes when a row's or a shared archive decision
 * changes; a progress save leaves it as it is.
 */
data class DismissedHistorySignal(
    val decidedRows: Int,
    val rowDecisionSum: Long,
    val groupDecisionSum: Long,
)

/**
 * This recent history with the [inProgress] episodes it doesn't hold, newest first: every started
 * episode for status (progress, the listener's place, resume picks), never for ranking, which
 * reads only the bounded recent history.
 */
fun List<ListeningHistoryEntity>.withInProgress(inProgress: List<ListeningHistoryEntity>): List<ListeningHistoryEntity> {
    val held = mapTo(HashSet()) { it.episodeId }
    val missing = inProgress.filter { it.episodeId !in held }
    if (missing.isEmpty()) return this
    return (this + missing).sortedByDescending { it.lastPlayedAt }
}
