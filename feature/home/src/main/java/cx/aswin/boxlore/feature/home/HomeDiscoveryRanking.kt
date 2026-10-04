package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.DiversityPolicy
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.PodcastRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.home.logic.toRecommendationPodcast

internal data class HomeDiscoveryRankingInput(
    val trending: List<Podcast>,
    val recommendations: List<Episode>,
    val history: List<ListeningHistoryEntity>,
    val subscribedIds: Set<String>,
)

internal data class HomeDiscoveryRankingResult(
    val trending: List<Podcast>,
    val recommendations: List<Episode>,
)

/** One collector owns this cache; flags, briefing and editorial updates never invalidate it. */
internal class HomeDiscoveryRankingCache {
    private var previousInput: HomeDiscoveryRankingInput? = null
    private var previousResult: HomeDiscoveryRankingResult? = null

    suspend fun get(
        input: HomeDiscoveryRankingInput,
        rank: suspend (HomeDiscoveryRankingInput) -> HomeDiscoveryRankingResult,
    ): HomeDiscoveryRankingResult {
        previousResult?.let { if (input == previousInput) return it }
        val result = rank(input)
        previousInput = input
        previousResult = result
        return result
    }
}

internal suspend fun HomeViewModel.rankDiscovery(input: HomeDiscoveryRankingInput): HomeDiscoveryRankingResult =
    HomeDiscoveryRankingResult(
        trending = adaptiveScorer.rankPodcasts(
            inputs = input.trending.mapIndexed { index, podcast ->
                PodcastRankingInput(
                    podcast = podcast,
                    priorScore = (input.trending.size - index).toDouble(),
                    source = CandidateSource.TRENDING,
                    isNovel = podcast.id !in input.subscribedIds,
                )
            },
            history = input.history,
            objective = RankingObjective.DISCOVERY,
            surface = RankingSurface.HOME,
            diversityPolicy = DiversityPolicy(limit = input.trending.size, maxPerShow = 1, reserveNovelSlot = true),
        ),
        recommendations = adaptiveScorer.rankEpisodes(
            inputs = input.recommendations.mapIndexed { index, episode ->
                val podcast = episode.toRecommendationPodcast()
                EpisodeRankingInput(
                    episode = episode,
                    podcast = podcast,
                    priorScore = (input.recommendations.size - index).toDouble(),
                    source = CandidateSource.SERVER_RECOMMENDATION,
                    isNovel = podcast.id !in input.subscribedIds,
                )
            },
            history = input.history,
            objective = RankingObjective.DISCOVERY,
            surface = RankingSurface.HOME,
            diversityPolicy = DiversityPolicy(limit = input.recommendations.size, maxPerShow = 2, reserveNovelSlot = true),
        ),
    )
