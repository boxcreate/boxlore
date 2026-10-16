package cx.aswin.boxlore.feature.onboarding

import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression (0.0.25, 0.0.28): the genre synthesis request ran inside `async`; a timeout failed the
 * parent job and crashed the app even though `await()` was wrapped in try/catch.
 */
class OnboardingFallbackTest {
    @Test
    fun `network failure inside async falls back without failing the parent`() = runTest {
        var reported: Exception? = null
        val result =
            coroutineScope {
                val rows =
                    async(Dispatchers.IO) {
                        orFallbackOnFailure(emptyList<String>(), onFailure = { reported = it }) {
                            throw SocketTimeoutException("timeout")
                        }
                    }
                val charts = async { listOf("trending") }
                charts.await() + rows.await()
            }

        assertEquals(listOf("trending"), result)
        assertTrue(reported is SocketTimeoutException)
    }

    @Test
    fun `cancellation is rethrown, not turned into the fallback, and not reported`() = runTest {
        var reported: Exception? = null
        val thrown =
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    orFallbackOnFailure(emptyList<String>(), onFailure = { reported = it }) {
                        throw CancellationException("screen left")
                    }
                }
            }
        assertEquals("screen left", thrown.message)
        assertNull(reported)
    }

    @Test
    fun `cancelling the parent stops the fetch instead of falling back`() = runTest {
        var reported: Exception? = null
        var fellBack = false
        val job =
            launch {
                val rows = orFallbackOnFailure(emptyList<String>(), onFailure = { reported = it }) {
                    awaitCancellation()
                }
                fellBack = rows.isEmpty()
            }
        testScheduler.runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(fellBack)
        assertNull(reported)
    }

    @Test
    fun `success returns the fetched value`() = runTest {
        assertEquals(listOf("row"), orFallbackOnFailure(emptyList()) { listOf("row") })
    }
}
