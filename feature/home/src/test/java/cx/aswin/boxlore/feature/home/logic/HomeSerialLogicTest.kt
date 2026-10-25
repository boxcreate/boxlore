package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.listenerShowIds
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

        assertEquals("ep-b", HomeSerialLogic.ongoingEpisodeId(history, setOf("pod-1")))
        assertEquals("ep-d", HomeSerialLogic.lastCompletedEpisodeId(history, setOf("pod-1")))
    }

    @Test
    fun `a serial show switched to its feed continues after the episode its old show was playing`() {
        // The feed show "rss:feed" replaced "pi-show": the listening stays under that show's IDs.
        val feedShow = TestFixtures.podcast(id = "rss:feed").copy(sourceType = cx.aswin.boxlore.core.model.Podcast.SOURCE_RSS, linkedPodcastIndexId = "pi-show")
        val history =
            listOf(
                item("pi-1", completed = true, progressMs = 0L, podcastId = "pi-show", lastPlayedAt = 1L),
                item("pi-2", completed = false, progressMs = 5L, podcastId = "pi-show", lastPlayedAt = 2L),
            )
        val episodes = (1..3).map { TestFixtures.episode(id = "-$it") }
        val tokens = mapOf("pi-1" to "t1", "-1" to "t1", "pi-2" to "t2", "-2" to "t2")

        val ongoing = HomeSerialLogic.ongoingEpisodeId(history, feedShow.listenerShowIds)
        val lastCompleted = HomeSerialLogic.lastCompletedEpisodeId(history, feedShow.listenerShowIds)
        assertEquals("pi-2", ongoing)
        assertEquals("pi-1", lastCompleted)
        // Found in the feed show's list under the ID the list uses.
        assertEquals("-2", HomeSerialLogic.idInList(ongoing, episodes, tokens))
        assertEquals("-1", HomeSerialLogic.idInList(lastCompleted, episodes, tokens))
        val next =
            resolveNextSerialEpisode(
                allEpisodes = episodes,
                ongoingId = HomeSerialLogic.idInList(ongoing, episodes, tokens),
                lastCompletedId = HomeSerialLogic.idInList(lastCompleted, episodes, tokens),
                completedEpIdsForResolve = emptySet(),
                inProgressEpIdsForResolve = HomeSerialLogic.idsInList(setOf("pi-2"), episodes, tokens),
            )
        assertEquals("-3", next?.id)
        // Without the old show's rows the show would start over at its first episode.
        assertEquals(null, HomeSerialLogic.ongoingEpisodeId(history, setOf("rss:feed")))
    }

    @Test
    fun `an id the list holds, or one with no other id, stays as it is`() {
        val episodes = listOf(TestFixtures.episode(id = "ep-1"), TestFixtures.episode(id = "ep-2"))

        assertEquals("ep-1", HomeSerialLogic.idInList("ep-1", episodes, emptyMap()))
        assertEquals("elsewhere", HomeSerialLogic.idInList("elsewhere", episodes, emptyMap()))
        assertEquals(null, HomeSerialLogic.idInList(null, episodes, emptyMap()))
        assertEquals(setOf("elsewhere"), HomeSerialLogic.idsInList(setOf("elsewhere"), episodes, emptyMap()))
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
