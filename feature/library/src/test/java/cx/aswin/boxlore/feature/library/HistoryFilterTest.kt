package cx.aswin.boxlore.feature.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryFilterTest {
    @Test
    fun filtersCoverAllInProgressAndCompleted() {
        val names = HistoryFilter.entries.map { it.name }.toSet()
        assertEquals(setOf("ALL", "IN_PROGRESS", "COMPLETED"), names)
        assertTrue(HistoryFilter.ALL in HistoryFilter.entries)
    }
}
