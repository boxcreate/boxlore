package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeListeningHistoryItemTest {
    @Test
    fun `maps entity to home history item`() {
        val entity =
            ListeningHistoryEntity(
                episodeId = "episode-1",
                podcastId = "podcast-1",
                episodeTitle = "Ignored episode title",
                episodeImageUrl = "https://example.com/episode.png",
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/audio.mp3",
                podcastName = "Podcast One",
                progressMs = 12_345L,
                durationMs = 67_890L,
                isCompleted = true,
                isLiked = true,
                lastPlayedAt = 123_456_789L,
                isDirty = false,
                syncedAt = 111L,
                enclosureType = "audio/mpeg",
                isManualCompletion = true,
                isBulkCompletion = false,
                episodeDescription = "Ignored description",
                finishedAt = 120_000_000L,
            )

        val item = entity.toHomeListeningHistoryItem()

        assertEquals("episode-1", item.episodeId)
        assertEquals("podcast-1", item.podcastId)
        assertEquals("Podcast One", item.podcastName)
        assertNull(item.podcastImageUrl)
        assertEquals(12_345L, item.progressMs)
        assertEquals(67_890L, item.durationMs)
        assertEquals(true, item.isCompleted)
        assertEquals(true, item.isLiked)
        assertEquals(123_456_789L, item.lastPlayedAt)
        assertEquals(120_000_000L, item.finishedAt)
    }

    @Test
    fun `counts only episodes finished in boxlore since install`() {
        val installedAt = 1_000L
        val history =
            listOf(
                item("finished-here", isCompleted = true, finishedAt = 1_500L),
                item("finished-at-install", isCompleted = true, finishedAt = 1_000L),
                // Restored or synced from before this install.
                item("restored", isCompleted = true, finishedAt = 900L),
                // Marked played: no finish time.
                item("marked-played", isCompleted = true, finishedAt = null),
                // Replayed after finishing, so no longer completed.
                item("restarted", isCompleted = false, finishedAt = 1_600L),
            )

        assertEquals(2, history.countFinishedSince(installedAt))
    }

    private fun item(id: String, isCompleted: Boolean, finishedAt: Long?) =
        HomeListeningHistoryItem(id, "show", "Show", null, 0L, 1L, isCompleted, false, 2_000L, finishedAt)
}
