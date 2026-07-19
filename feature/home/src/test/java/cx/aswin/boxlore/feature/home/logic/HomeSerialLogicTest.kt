package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeSerialLogicTest {
    @Test
    fun `progress ids merge history and completed set`() {
        val history =
            listOf(
                item("ep-1", completed = true, progressMs = 0L),
                item("ep-2", completed = false, progressMs = 10L),
                item("ep-3", completed = false, progressMs = 0L),
            )
        val progress = HomeSerialLogic.progressIds(history, completedEpisodeIds = setOf("ep-4"))

        assertEquals(setOf("ep-1", "ep-4"), progress.completed)
        assertEquals(setOf("ep-2"), progress.inProgress)
    }

    @Test
    fun `find pending serial podcasts requires oldest sort and unresolved episode`() {
        val oldest = TestFixtures.podcast(id = "old").copy(preferredSort = "oldest")
        val newest = TestFixtures.podcast(id = "new").copy(preferredSort = "newest")
        val resolved = mapOf("old" to TestFixtures.episode(id = "ep-done"))
        val history = listOf(item("ep-done", completed = true, progressMs = 0L, podcastId = "old"))

        val pending =
            HomeSerialLogic.findPendingSerialPodcasts(
                subs = listOf(oldest, newest),
                allHistory = history,
                completedEpisodeIds = emptySet(),
                resolvedSerial = resolved,
                inFlightResolutions = emptySet(),
            )

        assertEquals(listOf("old"), pending.map { it.id })
    }

    @Test
    fun `ongoing and last completed pick latest by lastPlayedAt`() {
        val history =
            listOf(
                item("ep-a", completed = false, progressMs = 5L, lastPlayedAt = 1L),
                item("ep-b", completed = false, progressMs = 5L, lastPlayedAt = 9L),
                item("ep-c", completed = true, progressMs = 0L, lastPlayedAt = 2L),
                item("ep-d", completed = true, progressMs = 0L, lastPlayedAt = 8L),
            )

        assertEquals("ep-b", HomeSerialLogic.ongoingEpisodeId(history, "pod-1"))
        assertEquals("ep-d", HomeSerialLogic.lastCompletedEpisodeId(history, "pod-1"))
    }

    private fun item(
        episodeId: String,
        completed: Boolean,
        progressMs: Long,
        podcastId: String = "pod-1",
        lastPlayedAt: Long = 1L,
    ) = HomeListeningHistoryItem(
        episodeId = episodeId,
        podcastId = podcastId,
        podcastName = "Show",
        podcastImageUrl = null,
        progressMs = progressMs,
        durationMs = 100L,
        isCompleted = completed,
        isLiked = false,
        lastPlayedAt = lastPlayedAt,
    )
}
