package cx.aswin.boxlore.feature.onboarding

import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `success returns the fetched value`() = runTest {
        assertEquals(listOf("row"), orFallbackOnFailure(emptyList()) { listOf("row") })
    }
}
