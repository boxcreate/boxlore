package cx.aswin.boxlore.ui.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionResumeRefreshLogicTest {
    @Test
    fun suppressesTheInitialOnStart() {
        assertFalse(SubscriptionResumeRefreshLogic.shouldRequestRefreshOnStart(isFirstStart = true))
    }

    @Test
    fun requestsRefreshOnLaterOnStart() {
        assertTrue(SubscriptionResumeRefreshLogic.shouldRequestRefreshOnStart(isFirstStart = false))
    }
}
