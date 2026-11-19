package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared pacer: at most 25 requests a minute per route, and a 429 holds the route until Retry-After. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiRouteBudgetTest {
    private fun TestScope.budget() = ApiRouteBudget(nowMs = { currentTime }, sleep = { kotlinx.coroutines.delay(it) })

    @Test
    fun aRouteNeverSendsMoreThanItsBudgetInAnyMinute() = runTest {
        val budget = budget()
        val sentAt = mutableListOf<Long>()
        repeat(100) {
            budget.acquire(ApiRouteBudget.PODCAST)
            sentAt += currentTime
        }
        sentAt.forEachIndexed { index, at ->
            val inWindow = sentAt.count { it in (at - ApiRouteBudget.WINDOW_MS + 1)..at }
            assertTrue("request $index shares its minute with $inWindow", inWindow <= ApiRouteBudget.DEFAULT_PER_MINUTE)
        }
        assertEquals("the first 25 go at once", 0L, sentAt[24])
        assertTrue("100 requests take about four minutes, not a burst", sentAt.last() >= 3 * ApiRouteBudget.WINDOW_MS)
    }

    @Test
    fun routesAreCountedApart() = runTest {
        val budget = budget()
        repeat(ApiRouteBudget.DEFAULT_PER_MINUTE) { assertTrue(budget.tryAcquire(ApiRouteBudget.PODCAST)) }
        assertFalse("the route is full", budget.tryAcquire(ApiRouteBudget.PODCAST))
        assertTrue("another route still has room", budget.tryAcquire(ApiRouteBudget.V2_EPISODES))
    }

    @Test
    fun aRateLimitedRouteWaitsForRetryAfterBeforeSendingAgain() = runTest {
        val budget = budget()
        budget.acquire(ApiRouteBudget.PODCAST)
        budget.rateLimited(ApiRouteBudget.PODCAST, retryAfterMs = 45_000L)
        assertFalse(budget.tryAcquire(ApiRouteBudget.PODCAST))
        assertEquals(45_000L, budget.waitMs(ApiRouteBudget.PODCAST))
        budget.acquire(ApiRouteBudget.PODCAST)
        assertEquals("the next request went once Retry-After passed", 45_000L, currentTime)
    }

    @Test
    fun aRateLimitWithoutRetryAfterWaitsAMinute() = runTest {
        val budget = budget()
        budget.rateLimited(ApiRouteBudget.SEARCH, retryAfterMs = null)
        budget.acquire(ApiRouteBudget.SEARCH)
        assertEquals(ApiRouteBudget.WINDOW_MS, currentTime)
    }
}
