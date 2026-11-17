package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Playback saves progress every few seconds; ranking must not see those saves as new history. */
class RankingHistorySignatureTest {
    private val playing = row("e1", progressMs = 60_000L, lastPlayedAt = HOUR_START + 60_000L)

    @Test
    fun progressSavesWithinTheHourLeaveTheSignatureUnchanged() {
        val later = playing.copy(progressMs = 70_000L, lastPlayedAt = HOUR_START + 70_000L, durationMs = 3_600_000L)

        assertEquals(RankingHistorySignature.of(listOf(playing)), RankingHistorySignature.of(listOf(later)))
    }

    @Test
    fun startingFinishingLikingANewRowOrANewHourChangeIt() {
        val base = RankingHistorySignature.of(listOf(playing))

        assertNotEquals(base, RankingHistorySignature.of(listOf(playing.copy(progressMs = 0L))))
        assertNotEquals(base, RankingHistorySignature.of(listOf(playing.copy(isCompleted = true))))
        assertNotEquals(base, RankingHistorySignature.of(listOf(playing.copy(isLiked = true, likedAt = 5L))))
        assertNotEquals(base, RankingHistorySignature.of(listOf(playing, row("e2"))))
        assertNotEquals(base, RankingHistorySignature.of(listOf(playing.copy(lastPlayedAt = HOUR_START + HOUR_MS))))
    }

    @Test
    fun theCacheScoresAgainOnlyForANewInputAndForgetsFailures() = runTest {
        val cache = RankingScoreCache<RankingHistorySignature>()
        var runs = 0
        val score = suspend {
            runs++
            mapOf("p1" to runs.toDouble())
        }
        val first = RankingHistorySignature.of(listOf(playing))

        assertEquals(mapOf("p1" to 1.0), cache.get(first, score))
        assertEquals(mapOf("p1" to 1.0), cache.get(RankingHistorySignature.of(listOf(playing.copy(progressMs = 90_000L))), score))
        assertEquals(1, runs)

        val finished = RankingHistorySignature.of(listOf(playing.copy(isCompleted = true)))
        assertTrue(runCatching { cache.get(finished) { error("scoring failed") } }.isFailure)
        assertEquals(mapOf("p1" to 2.0), cache.get(finished, score))
        assertEquals(2, runs)
    }

    private fun row(id: String, progressMs: Long = 0L, lastPlayedAt: Long = HOUR_START,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "p1",
        episodeTitle = id,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = progressMs,
        durationMs = 3_000_000L,
        isCompleted = false,
        lastPlayedAt = lastPlayedAt,
    )

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val HOUR_START = 1_700_002_800_000L
    }
}
