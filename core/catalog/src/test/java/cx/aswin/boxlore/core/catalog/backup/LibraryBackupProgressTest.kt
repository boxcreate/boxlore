package cx.aswin.boxlore.core.catalog.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryBackupProgressTest {
    @Test
    fun defaultValues_areInitializedProperly() {
        val progress = JsonBackupProgress()
        assertEquals(JsonBackupPhase.PREPARING, progress.phase)
        assertEquals(0, progress.current)
        assertEquals(0, progress.total)
        assertEquals("", progress.currentTitle)
        assertEquals(0f, progress.progressRatio)
    }

    @Test
    fun progressRatio_withZeroTotal_returnsZero() {
        val progress = JsonBackupProgress(
            phase = JsonBackupPhase.SUBSCRIBING,
            current = 5,
            total = 0,
        )
        assertEquals(0f, progress.progressRatio)
    }

    @Test
    fun progressRatio_withPositiveTotal_computesAccurately() {
        val progress = JsonBackupProgress(
            phase = JsonBackupPhase.SUBSCRIBING,
            current = 25,
            total = 100,
            currentTitle = "Test Podcast",
        )
        assertEquals(0.25f, progress.progressRatio)
    }

    @Test
    fun progressRatio_clampedAtOne() {
        val progress = JsonBackupProgress(
            phase = JsonBackupPhase.COMPLETED,
            current = 15,
            total = 10,
        )
        assertEquals(1f, progress.progressRatio)
    }

    @Test
    fun allPhases_areDefined() {
        val phases = JsonBackupPhase.entries
        assertTrue(phases.contains(JsonBackupPhase.PREPARING))
        assertTrue(phases.contains(JsonBackupPhase.SUBSCRIBING))
        assertTrue(phases.contains(JsonBackupPhase.RESTORING_HISTORY))
        assertTrue(phases.contains(JsonBackupPhase.REFRESHING_FEEDS))
        assertTrue(phases.contains(JsonBackupPhase.COMPLETED))
    }
}
