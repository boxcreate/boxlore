package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ScorablePodcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YourShowsScorerTest {
    private val nowMs = 1_700_000_000_000L
    private val dayMs = 24L * 60L * 60L * 1_000L

    @Test
    fun `fresh subscription ranks near top without displacing strongest habit`() {
        val scores =
            YourShowsScorer.score(
                podcasts =
                listOf(
                    podcast("habit", nowMs - 200L * dayMs),
                    podcast("fresh", nowMs),
                    podcast("idle", nowMs - 200L * dayMs),
                ),
                history = (1..100).map { history(it) },
                includeAutoDownloadBoost = true,
                nowMs = nowMs,
            )

        assertEquals(1.0, scores.getValue("habit"), 1e-9)
        assertEquals(YourShowsSubscriptionRecency.PEAK_FLOOR, scores.getValue("fresh"), 1e-9)
        assertTrue(scores.getValue("fresh") > scores.getValue("idle"))
    }

    @Test
    fun `three day subscription keeps bounded score floor`() {
        val scores =
            YourShowsScorer.score(
                podcasts =
                listOf(
                    podcast("habit", nowMs - 200L * dayMs),
                    podcast("recent", nowMs - 3L * dayMs),
                ),
                history = (1..100).map { history(it) },
                includeAutoDownloadBoost = true,
                nowMs = nowMs,
            )

        assertEquals(
            YourShowsSubscriptionRecency.WINDOW_END_FLOOR,
            scores.getValue("recent"),
            1e-9,
        )
    }

    @Test
    fun `subscription age is applied once after listening normalization`() {
        val scores =
            YourShowsScorer.score(
                podcasts =
                listOf(
                    podcast("fresh", nowMs),
                    podcast("old", nowMs - 200L * dayMs),
                ),
                history = emptyList(),
                includeAutoDownloadBoost = true,
                nowMs = nowMs,
            )

        assertEquals(YourShowsSubscriptionRecency.PEAK_FLOOR, scores.getValue("fresh"), 1e-9)
        assertEquals(0.0, scores.getValue("old"), 1e-9)
    }

    private fun podcast(id: String, subscribedAt: Long,) = ScorablePodcast(
        id = id,
        subscribedAt = subscribedAt,
        latestEpisode = null,
        notificationsEnabled = false,
        autoDownloadEnabled = false,
    )

    private fun history(index: Int) = ListeningHistoryEntity(
        episodeId = "habit-$index",
        podcastId = "habit",
        episodeTitle = "Episode $index",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Habit",
        progressMs = 1L,
        durationMs = 2L,
        isCompleted = true,
        lastPlayedAt = nowMs - dayMs,
    )
}
