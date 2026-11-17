package cx.aswin.boxlore.feature.home.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeChipFeedReadsTest {
    private val minute = 60_000L

    @Test
    fun `tapping a chip reads that show's feed`() {
        // Before, selecting a chip only reordered the next pass, so a show read 3 hours ago kept its old episode.
        assertTrue(HomeChipFeedReads().claim("daily", explicitTap = true, nowMs = 0L))
    }

    @Test
    fun `fast taps between chips read each show once`() {
        val reads = HomeChipFeedReads()
        val taps = listOf("daily", "serial", "daily", "serial", "daily")
        val granted = taps.mapIndexed { second, id -> id to reads.claim(id, explicitTap = true, nowMs = second * 1_000L) }.filter { it.second }

        assertEquals(listOf("daily" to true, "serial" to true), granted)
    }

    @Test
    fun `a show reads again only after 15 minutes`() {
        val reads = HomeChipFeedReads()
        assertTrue(reads.claim("daily", explicitTap = true, nowMs = 0L))
        assertFalse(reads.claim("daily", explicitTap = true, nowMs = 15 * minute - 1))
        assertTrue(reads.claim("daily", explicitTap = true, nowMs = 15 * minute))
        assertFalse(reads.claim("daily", explicitTap = true, nowMs = 20 * minute))
    }

    @Test
    fun `the automatic selection of a library's only show reads nothing and uses no read`() {
        val reads = HomeChipFeedReads()
        assertFalse(reads.claim("daily", explicitTap = false, nowMs = 0L))
        assertTrue(reads.claim("daily", explicitTap = true, nowMs = 1L))
    }

    @Test
    fun `a clock moved back does not block a read`() {
        val reads = HomeChipFeedReads()
        assertTrue(reads.claim("daily", explicitTap = true, nowMs = 60 * minute))
        assertTrue(reads.claim("daily", explicitTap = true, nowMs = 10 * minute))
    }
}
