package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ScorablePodcast
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingHistorySignature
import cx.aswin.boxlore.core.ranking.RankingScoreCache

/**
 * Library's Shows and New Episodes scores, scored again only when the shows or the
 * [RankingHistorySignature] change. History emits on every progress save while audio plays; the
 * screen still redraws progress bars from it, but saves within the same hour reuse the last scores.
 */
internal class LibraryRankingScores(
    private val scoreShows: suspend (List<ScorablePodcast>, List<ListeningHistoryEntity>) -> Map<String, Double>,
    private val scoreLatest: suspend (List<EpisodeRankingInput>, List<ListeningHistoryEntity>) -> Map<String, Double>,
) {
    private val shows = RankingScoreCache<Pair<List<ScorablePodcast>, RankingHistorySignature>>()
    private val latest = RankingScoreCache<Pair<List<EpisodeRankingInput>, RankingHistorySignature>>()

    suspend fun showScores(podcasts: List<Podcast>, history: List<ListeningHistoryEntity>): Map<String, Double> {
        val scorable = podcasts.map { it.toScorable() }
        return shows.get(scorable to RankingHistorySignature.of(history)) { scoreShows(scorable, history) }
    }

    /** The key leaves out each show's resume bar, which moves with every progress save. */
    suspend fun latestScores(inputs: List<EpisodeRankingInput>, history: List<ListeningHistoryEntity>): Map<String, Double> {
        val key = inputs.map { it.copy(podcast = it.podcast.copy(resumeProgress = null)) }
        return latest.get(key to RankingHistorySignature.of(history)) { scoreLatest(inputs, history) }
    }
}
