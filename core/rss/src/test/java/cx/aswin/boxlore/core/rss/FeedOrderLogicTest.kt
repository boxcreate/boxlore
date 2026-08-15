package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalFeedOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeedOrderLogicTest {
    @Test
    fun newestFirstWhenDatesDecrease() {
        assertEquals(
            LocalFeedOrder.NEWEST_FIRST,
            FeedOrderLogic.classify(listOf(50L, 40L, 30L, 20L, 10L)),
        )
    }

    @Test
    fun oldestFirstWhenDatesIncrease() {
        assertEquals(
            LocalFeedOrder.OLDEST_FIRST,
            FeedOrderLogic.classify(listOf(10L, 20L, 30L, 40L, 50L)),
        )
    }

    @Test
    fun mixedWhenShortOrJumbled() {
        assertEquals(LocalFeedOrder.MIXED, FeedOrderLogic.classify(listOf(10L, 20L)))
        assertEquals(LocalFeedOrder.MIXED, FeedOrderLogic.classify(listOf(10L, 50L, 20L, 40L, 15L)))
    }

    @Test
    fun tipIsMaxPublishedDateNotFirstItem() {
        assertEquals(50L, FeedOrderLogic.newestByPublishedDate(listOf(10L, 50L, 20L)))
    }
}
