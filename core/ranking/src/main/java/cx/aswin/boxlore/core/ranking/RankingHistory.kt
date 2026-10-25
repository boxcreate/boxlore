package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.database.ListeningHistoryEntity

/**
 * Listening history for ranking with each episode counted once (docs/EPISODE_IDS.md §5).
 *
 * An episode can be stored under several IDs. [tokens] maps each ID to a token shared by every ID
 * of its episode (`EpisodeListenerStore.groupTokens`); an ID missing from it is its own token. Rows
 * of one episode collapse to the most recently played one, and a candidate under any of the
 * episode's IDs finds it. With one ID per episode (and so no repeated token) [rows] is the input,
 * unchanged.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, ranking counts an episode once).
 * COMPAT(own-ids): one listener state per episode across its IDs. Permanent: never remove (MIGRATION.md §5).
 */
class RankingHistory private constructor(
    /** One row per episode, in the input order of each episode's first row. */
    val rows: List<ListeningHistoryEntity>,
    private val tokens: Map<String, String>,
) {
    private val byToken: Map<String, ListeningHistoryEntity> = rows.associateBy { keyOf(it.episodeId) }

    /** The token of [episodeId]: equal for every ID of one episode. */
    fun keyOf(episodeId: String): String = tokens[episodeId] ?: episodeId

    /** The episode's history row, found by any of its IDs. */
    fun forEpisode(episodeId: String): ListeningHistoryEntity? = byToken[keyOf(episodeId)]

    companion object {
        /** Each ID its own episode: today's behaviour. */
        fun ofOwnIds(history: List<ListeningHistoryEntity>): RankingHistory = of(history, emptyMap())

        /** [history] with rows sharing a token collapsed to the one played most recently. */
        fun of(history: List<ListeningHistoryEntity>, tokens: Map<String, String>): RankingHistory {
            val keyOf = { episodeId: String -> tokens[episodeId] ?: episodeId }
            val latestByToken = history
                .groupBy { keyOf(it.episodeId) }
                .mapValues { (_, rows) -> rows.maxWith(compareBy({ it.lastPlayedAt }, { it.likedAt })) }
            if (latestByToken.size == history.size) return RankingHistory(history, tokens)
            val seen = HashSet<String>()
            val rows = history.mapNotNull { row ->
                val token = keyOf(row.episodeId)
                if (seen.add(token)) latestByToken.getValue(token) else null
            }
            return RankingHistory(rows, tokens)
        }
    }
}
