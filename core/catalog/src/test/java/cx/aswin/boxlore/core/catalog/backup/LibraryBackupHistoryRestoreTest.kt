package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryBackupHistoryRestoreTest {
    @Test
    fun sanitizePreservesManualAndBulkCompletionProvenance() {
        val restored =
            LibraryBackupHistoryRestore.sanitize(
                history().copy(
                    isManualCompletion = true,
                    isBulkCompletion = true,
                ),
            )

        assertTrue(restored.isManualCompletion)
        assertTrue(restored.isBulkCompletion)
    }

    @Test
    fun sanitizeKeepsTheLikeTime() {
        val restored = LibraryBackupHistoryRestore.sanitize(history().copy(isLiked = true, likedAt = 42L))

        assertTrue(restored.isLiked)
        assertEquals(42L, restored.likedAt)
    }

    @Test
    fun sanitizeKeepsTheArchiveDecisionEitherWay() {
        assertEquals(42L, LibraryBackupHistoryRestore.sanitize(history().copy(dismissedAt = 42L)).dismissedAt)
        assertEquals(-43L, LibraryBackupHistoryRestore.sanitize(history().copy(dismissedAt = -43L)).dismissedAt)
        assertEquals(0L, LibraryBackupHistoryRestore.sanitize(history()).dismissedAt)
    }

    private fun history() = ListeningHistoryEntity(
        episodeId = "-9",
        podcastId = "100",
        episodeTitle = "Episode",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example.com/episode.mp3",
        podcastName = "Show",
        progressMs = 1L,
        durationMs = 2L,
        isCompleted = true,
        lastPlayedAt = 3L,
    )
}
