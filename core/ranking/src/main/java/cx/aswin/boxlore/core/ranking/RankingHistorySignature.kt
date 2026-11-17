package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.database.ListeningHistoryEntity

/**
 * The parts of listening history that subscription ranking reads, coarse enough that the progress
 * playback saves every few seconds leaves it unchanged: which episodes are in history, whether each
 * was started, finished or liked, and the hour it was last played. Surfaces re-rank when this
 * changes: a progress save changes it only when an episode is first started or its last-played hour
 * moves, while a new row, a finish or a like always does.
 */
class RankingHistorySignature private constructor(private val rows: List<Row>) {
    private data class Row(
        val episodeId: String,
        val podcastId: String,
        val started: Boolean,
        val isCompleted: Boolean,
        val isLiked: Boolean,
        val likedAt: Long,
        val lastPlayedHour: Long,
    )

    override fun equals(other: Any?): Boolean = other is RankingHistorySignature && rows == other.rows

    override fun hashCode(): Int = rows.hashCode()

    override fun toString(): String = "RankingHistorySignature(rows=${rows.size})"

    companion object {
        private const val HOUR_MS = 3_600_000L

        fun of(history: List<ListeningHistoryEntity>): RankingHistorySignature = RankingHistorySignature(
            history.map { row ->
                Row(
                    episodeId = row.episodeId,
                    podcastId = row.podcastId,
                    started = row.progressMs > 0L,
                    isCompleted = row.isCompleted,
                    isLiked = row.isLiked,
                    likedAt = row.likedAt,
                    lastPlayedHour = Math.floorDiv(row.lastPlayedAt, HOUR_MS),
                )
            },
        )
    }
}

/**
 * Remembers the last scores and the input they came from, so a surface whose inputs did not change
 * reuses them instead of scoring again. A failed scoring run is not remembered. Safe to share
 * between coroutines; when two compute at once, the last one to finish is kept.
 */
class RankingScoreCache<K : Any> {
    private class Entry<K : Any>(val key: K, val scores: Map<String, Double>)

    @Volatile private var last: Entry<K>? = null

    suspend fun get(key: K, score: suspend () -> Map<String, Double>): Map<String, Double> {
        last?.let { if (it.key == key) return it.scores }
        val scores = score()
        last = Entry(key, scores)
        return scores
    }
}
