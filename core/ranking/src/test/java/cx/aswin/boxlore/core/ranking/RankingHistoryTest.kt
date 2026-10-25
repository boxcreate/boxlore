package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Ranking counts an episode stored under several IDs once (docs/EPISODE_IDS.md §5). */
class RankingHistoryTest {
    private val tokens = mapOf("A" to TOKEN, "B" to TOKEN)

    @Test
    fun withOneIdPerEpisodeTheHistoryIsUnchanged() {
        val history = listOf(row("A", playedAt = 3L), row("C", playedAt = 2L))

        assertSame(history, RankingHistory.ofOwnIds(history).rows)
        assertSame(history, RankingHistory.of(history, mapOf("A" to TOKEN)).rows)
        assertEquals("C", RankingHistory.ofOwnIds(history).forEpisode("C")?.episodeId)
        assertNull(RankingHistory.ofOwnIds(history).forEpisode("B"))
    }

    @Test
    fun anEpisodeUnderTwoIdsIsOneRowFoundByEitherId() {
        val history = listOf(row("C", playedAt = 9L), row("A", playedAt = 3L), row("B", playedAt = 5L))

        val once = RankingHistory.of(history, tokens)

        assertEquals(listOf("C", "B"), once.rows.map { it.episodeId })
        assertEquals("B", once.forEpisode("A")?.episodeId)
        assertEquals("B", once.forEpisode("B")?.episodeId)
        assertEquals(once.keyOf("A"), once.keyOf("B"))
        assertEquals("C", once.keyOf("C"))
    }

    private fun row(id: String, playedAt: Long,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "pod-1",
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Podcast",
        progressMs = 10L,
        durationMs = 100L,
        isCompleted = false,
        lastPlayedAt = playedAt,
    )

    private companion object {
        const val TOKEN = "pod-1\u0000guid-a"
    }
}
