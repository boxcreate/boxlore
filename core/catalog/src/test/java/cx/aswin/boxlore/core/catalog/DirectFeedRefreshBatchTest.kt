package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectFeedRefreshBatchTest {
    @Test fun largeLibrariesRotateEvenWhenEarlierFeedsKeepFailing() = runTest {
        val ids = (1..25).map(Int::toString)
        val batch = DirectFeedRefreshBatch()
        assertEquals(ids.take(10), batch.select(ids, null) { true })
        assertEquals(ids.drop(10).take(10), batch.select(ids, null) { true })
        assertEquals(ids.drop(20) + ids.take(5), batch.select(ids, null) { true })
    }

    @Test fun recentFeedsDoNotConsumeSlotsAndVisibleShowIsPrioritized() = runTest {
        val batch = DirectFeedRefreshBatch(2)
        assertEquals(listOf("visible", "old"), batch.select(listOf("fresh", "old", "visible", "later"), "visible") { it != "fresh" })
        assertEquals(listOf("later"), batch.select(listOf("fresh", "old", "visible", "later"), "visible") { it == "later" })
        assertTrue(batch.select(emptyList(), "missing") { true }.isEmpty())
    }
}
