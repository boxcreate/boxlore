package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Library screen redraws progress on every playback save but ranks only on real history changes. */
class LibraryRankingScoresTest {
    private val episode = Episode(id = "e1", title = "Ep", description = "", audioUrl = "https://example.com/e1.mp3", publishedDate = 1_700_000_000L)
    private val show = TestFixtures.podcast(id = "p1").copy(latestEpisode = episode)
    private var showRuns = 0
    private var latestRuns = 0
    private val scores = LibraryRankingScores(
        scoreShows = { podcasts, _ ->
            showRuns++
            podcasts.associate { it.id to 1.0 }
        },
        scoreLatest = { inputs, _ ->
            latestRuns++
            inputs.associate { it.episode.id to 1.0 }
        },
    )

    @Test
    fun progressOnlyHistoryUpdatesReuseTheShowAndNewEpisodeScores() = runTest {
        for (tick in 0..20) {
            val history = listOf(played(progressMs = 10_000L + tick * 10_000L, lastPlayedAt = HOUR_START + tick * 10_000L))
            // The screen's own per-save enrichment moves the resume bar each time.
            val enriched = show.copy(resumeProgress = (tick + 1) / 100f)
            scores.showScores(listOf(enriched), history)
            scores.latestScores(listOf(input(enriched)), history)
        }

        assertEquals(1, showRuns)
        assertEquals(1, latestRuns)
    }

    @Test
    fun finishingAnEpisodeOrAddingAShowScoresAgain() = runTest {
        val history = listOf(played(progressMs = 10_000L, lastPlayedAt = HOUR_START))
        scores.showScores(listOf(show), history)
        scores.latestScores(listOf(input(show)), history)

        val finished = listOf(played(progressMs = 3_000_000L, lastPlayedAt = HOUR_START, isCompleted = true))
        scores.showScores(listOf(show), finished)
        scores.latestScores(listOf(input(show)), finished)
        assertEquals(2, showRuns)
        assertEquals(2, latestRuns)

        val another = TestFixtures.podcast(id = "p2")
        scores.showScores(listOf(show, another), finished)
        assertEquals(3, showRuns)
    }

    private fun input(podcast: cx.aswin.boxlore.core.model.Podcast) = EpisodeRankingInput(
        episode = episode,
        podcast = podcast,
        priorScore = episode.publishedDate.toDouble(),
        source = CandidateSource.SUBSCRIPTION,
    )

    private fun played(progressMs: Long, lastPlayedAt: Long, isCompleted: Boolean = false,) = ListeningHistoryEntity(
        episodeId = "e1",
        podcastId = "p1",
        episodeTitle = "Ep",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = progressMs,
        durationMs = 3_000_000L,
        isCompleted = isCompleted,
        lastPlayedAt = lastPlayedAt,
    )

    private companion object {
        const val HOUR_START = 1_700_002_800_000L
    }
}
