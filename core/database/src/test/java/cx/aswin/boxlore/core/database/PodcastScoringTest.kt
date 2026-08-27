package cx.aswin.boxlore.core.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastScoringTest {
    private val nowMs = 1_700_000_000_000L

    @Test
    fun `just subscribed peak recency outranks an idle older sub`() {
        val scores =
            PodcastScoring.calculateScores(
                podcasts =
                    listOf(
                        scorable("fresh", nowMs),
                        scorable("idle", nowMs - 200L * 24 * 3_600_000L),
                    ),
                allHistory = emptyList(),
                nowMs = nowMs,
            )
        assertEquals(PodcastScoring.SUBSCRIBE_RECENCY_PEAK, scores.getValue("fresh"), 1e-9)
        assertTrue(scores.getValue("fresh") > scores.getValue("idle"))
    }

    @Test
    fun `recency is still material three days after subscribe`() {
        val scores =
            PodcastScoring.calculateScores(
                podcasts = listOf(scorable("recent", nowMs - 72L * 3_600_000L)),
                allHistory = emptyList(),
                nowMs = nowMs,
            )
        assertEquals(150.0, scores.getValue("recent"), 1e-9)
    }

    @Test
    fun `missing subscribedAt adds no recency`() {
        val scores =
            PodcastScoring.calculateScores(
                podcasts = listOf(scorable("none", 0L)),
                allHistory = emptyList(),
                nowMs = nowMs,
            )
        assertEquals(0.0, scores.getValue("none"), 1e-9)
    }

    private fun scorable(
        id: String,
        subscribedAt: Long,
    ) = ScorablePodcast(
        id = id,
        subscribedAt = subscribedAt,
        latestEpisode = null,
        notificationsEnabled = false,
        autoDownloadEnabled = false,
    )
}
