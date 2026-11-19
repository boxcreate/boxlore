package cx.aswin.boxlore.feature.explore.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreTopChartPagingTest {
    private val pageSize = 20

    @Test
    fun `a failed third page keeps the chart open and asks for the same page again`() {
        // Regression: a timed-out page came back empty and ended the chart at 40 shows for good.
        val twoPages = ExploreTopChartPaging().afterPage(20, pageSize).afterPage(20, pageSize)
        assertEquals(40, twoPages.offset)

        val failed = twoPages.afterPage(loaded = null, pageSize = pageSize)

        assertTrue(failed.hasMore)
        assertEquals(40, failed.offset, "Try again asks for offset 40 again")
        val retried = failed.afterPage(20, pageSize)
        assertEquals(60, retried.offset)
        assertTrue(retried.hasMore)
    }

    @Test
    fun `only a page that was read and came back short ends the chart`() {
        val short = ExploreTopChartPaging().afterPage(20, pageSize).afterPage(7, pageSize)
        assertFalse(short.hasMore)
        assertEquals(27, short.offset)
        assertFalse(ExploreTopChartPaging().afterPage(0, pageSize).hasMore, "an empty chart that was read has no more pages")
    }

    @Test
    fun `a failed first page leaves the chart unread`() {
        val failed = ExploreTopChartPaging().afterPage(null, pageSize)
        assertEquals(ExploreTopChartPaging(), failed)
    }
}
