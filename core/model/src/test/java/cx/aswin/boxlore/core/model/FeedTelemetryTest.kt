package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedTelemetryTest {
    @Test
    fun `a pass counts every show it read`() {
        val pass = FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, okCount = 4, notModifiedCount = 5, failedCount = 2)

        assertEquals(11, pass.showsCount)
        assertEquals(FeedPassResult.MIXED, pass.passResult)
    }

    @Test
    fun `a pass is ok without failures and failed when nothing worked`() {
        assertEquals(FeedPassResult.OK, FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 0, 3, 0).passResult)
        assertEquals(FeedPassResult.FAILED, FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 0, 0, 3).passResult)
    }

    @Test
    fun `negative counts from a caller are treated as zero`() {
        val pass = FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, okCount = -3, notModifiedCount = 0, failedCount = 2)

        assertEquals(2, pass.showsCount)
        assertEquals(FeedPassResult.FAILED, pass.passResult)
    }

    @Test
    fun `only ok and not modified count as success`() {
        assertFalse(FeedFetchOutcome.OK.isFailure)
        assertFalse(FeedFetchOutcome.NOT_MODIFIED.isFailure)
        FeedFetchOutcome.entries.filterNot { it == FeedFetchOutcome.OK || it == FeedFetchOutcome.NOT_MODIFIED }.forEach {
            assertTrue(it.isFailure, it.name)
        }
    }
}
