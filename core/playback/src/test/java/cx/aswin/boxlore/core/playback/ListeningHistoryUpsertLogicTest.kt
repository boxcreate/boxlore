package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListeningHistoryUpsertLogicTest {
    @Test
    fun `progress save marks dirty and preserves fields`() {
        val entity =
            ListeningHistoryUpsertLogic.buildProgressSaveEntity(
                ListeningHistoryUpsertLogic.ProgressSaveInput(
                    podcastId = "p",
                    episodeId = "e",
                    positionMs = 12_000L,
                    durationMs = 60_000L,
                    episodeTitle = "Ep",
                    episodeImageUrl = "img",
                    podcastImageUrl = "pimg",
                    episodeAudioUrl = "https://a",
                    podcastName = "Show",
                    isCompleted = false,
                    isLiked = true,
                    lastPlayedAt = 99L,
                    episodeDescription = "desc",
                ),
            )
        assertEquals("e", entity.episodeId)
        assertEquals(12_000L, entity.progressMs)
        assertTrue(entity.isLiked)
        assertTrue(entity.isDirty)
        assertFalse(entity.isManualCompletion)
        assertEquals("desc", entity.episodeDescription)
    }

    @Test
    fun `bulk complete updates existing or creates new`() {
        val episode = TestFixtures.episode(id = "e1", title = "One")
        val existing =
            ListeningHistoryEntity(
                episodeId = "e1",
                podcastId = "p",
                episodeTitle = "Old",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://a",
                podcastName = "Show",
                progressMs = 5_000L,
                durationMs = 10_000L,
                isCompleted = false,
                isLiked = true,
                lastPlayedAt = 1L,
                episodeDescription = "keep",
            )
        val updated =
            ListeningHistoryUpsertLogic.buildBulkCompleteEntity(
                episode = episode,
                podcastId = "p",
                podcastTitle = "Show",
                podcastImageUrl = null,
                existing = existing,
                nowMs = 50L,
            )!!
        assertTrue(updated.isCompleted)
        assertTrue(updated.isBulkCompletion)
        assertEquals(0L, updated.progressMs)
        assertEquals("keep", updated.episodeDescription)
        assertEquals(50L, updated.lastPlayedAt)

        val created =
            ListeningHistoryUpsertLogic.buildBulkCompleteEntity(
                episode = episode,
                podcastId = "p",
                podcastTitle = "Show",
                podcastImageUrl = "pi",
                existing = null,
                nowMs = 50L,
            )!!
        assertEquals(episode.title, created.episodeTitle)
        assertTrue(created.isCompleted)
    }

    @Test
    fun `bulk complete leaves an episode already finished as it is`() {
        // Regression: Mark all as played turned episodes finished by listening into marked rows,
        // so they left Listening history, insights and monthly stories.
        val episode = TestFixtures.episode(id = "e1", title = "One")
        val finished =
            ListeningHistoryEntity(
                episodeId = "e1",
                podcastId = "p",
                episodeTitle = "One",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://a",
                podcastName = "Show",
                progressMs = 0L,
                durationMs = 10_000L,
                isCompleted = true,
                lastPlayedAt = 1L,
                isDirty = false,
                firstPlayedAt = 1L,
                finishedAt = 1L,
            )
        listOf(finished, finished.copy(isManualCompletion = true), finished.copy(isBulkCompletion = true)).forEach { existing ->
            assertNull(ListeningHistoryUpsertLogic.buildBulkCompleteEntity(episode, "p", "Show", null, existing, nowMs = 50L))
        }
    }

    @Test
    fun `bulk uncomplete only for completed rows`() {
        val incomplete =
            ListeningHistoryEntity(
                episodeId = "e",
                podcastId = "p",
                episodeTitle = "T",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = null,
                podcastName = "S",
                progressMs = 1L,
                durationMs = 2L,
                isCompleted = false,
                lastPlayedAt = 1L,
            )
        assertNull(ListeningHistoryUpsertLogic.buildBulkUncompleteEntity(incomplete))

        val completed = incomplete.copy(isCompleted = true)
        val undone = ListeningHistoryUpsertLogic.buildBulkUncompleteEntity(completed)!!
        assertFalse(undone.isCompleted)
        // Regression: it moved to now, so an episode heard long ago came back under today.
        assertEquals(2L, undone.lastPlayedAt)
        assertTrue(undone.isDirty)
    }
}
