package cx.aswin.boxlore.core.rss

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RssUnchangedLogicTest {
    @Test
    fun head304IsUnchanged() {
        assertTrue(RssUnchangedLogic.headMeansUnchanged(304))
        assertFalse(RssUnchangedLogic.headMeansUnchanged(200))
    }

    @Test
    fun head405OrFailedHeadTriesConditionalGet() {
        assertTrue(RssUnchangedLogic.headMeansTryConditionalGet(405))
        assertTrue(RssUnchangedLogic.headMeansTryConditionalGet(501))
        assertTrue(RssUnchangedLogic.headMeansTryConditionalGet(null))
        assertFalse(RssUnchangedLogic.headMeansTryConditionalGet(200))
        assertFalse(RssUnchangedLogic.headMeansTryConditionalGet(304))
    }
}
