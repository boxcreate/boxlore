package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastScoring
import cx.aswin.boxlore.core.database.ScorablePodcast
import kotlin.math.ln

/**
 * Deterministic subscription ranking shared by Home, Library, widgets, and Auto.
 *
 * The raw, interpretable [PodcastScoring] listening signals are log-normalized
 * before the single bounded subscribe-recency floor is applied. This keeps a
 * genuinely new show near the top without counting subscription age twice.
 */
internal object YourShowsScorer {
    fun score(
        podcasts: List<ScorablePodcast>,
        history: List<ListeningHistoryEntity>,
        includeAutoDownloadBoost: Boolean,
        nowMs: Long,
    ): Map<String, Double> {
        if (podcasts.isEmpty()) return emptyMap()
        val priors =
            PodcastScoring.calculateScores(
                podcasts = podcasts,
                allHistory = history,
                includeAutoDownloadBoost = includeAutoDownloadBoost,
                includeSubscriptionRecency = false,
                nowMs = nowMs,
            )
        val normalized = normalize(priors)
        return podcasts.associate { podcast ->
            podcast.id to
                YourShowsSubscriptionRecency.apply(
                    score = normalized[podcast.id] ?: 0.0,
                    subscribedAt = podcast.subscribedAt,
                    nowMs = nowMs,
                )
        }
    }

    private fun normalize(scores: Map<String, Double>): Map<String, Double> {
        val finite = scores.filterValues { it.isFinite() && it >= 0.0 }
        val max = finite.values.maxOrNull() ?: return scores.mapValues { 0.0 }
        if (max <= 0.0) return scores.mapValues { 0.0 }
        val denominator = ln(1.0 + max)
        return scores.mapValues { (_, value) ->
            if (!value.isFinite() || value <= 0.0) {
                0.0
            } else {
                (ln(1.0 + value) / denominator).coerceIn(0.0, 1.0)
            }
        }
    }
}
