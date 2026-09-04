package cx.aswin.boxlore.core.downloads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartDownloadSyncGateTest {
    @Test
    fun `concurrent automatic callers execute the sync body once`() = runTest {
        val nowMs = 1_700_000_000_000L
        val gate = SmartDownloadSyncGate(currentTimeMs = { nowMs })
        val firstSyncStarted = CompletableDeferred<Unit>()
        val allowFirstSyncToFinish = CompletableDeferred<Unit>()
        var lastSuccessfulSyncMs = 0L
        var executionCount = 0

        val first =
            async {
                gate.run(
                    isManual = false,
                    lastSuccessfulSyncMs = { lastSuccessfulSyncMs },
                    onAutomaticCadenceSatisfied = {},
                ) {
                    executionCount++
                    firstSyncStarted.complete(Unit)
                    allowFirstSyncToFinish.await()
                    lastSuccessfulSyncMs = nowMs
                    true
                }
            }
        firstSyncStarted.await()
        val second =
            async {
                gate.run(
                    isManual = false,
                    lastSuccessfulSyncMs = { lastSuccessfulSyncMs },
                    onAutomaticCadenceSatisfied = {},
                ) {
                    executionCount++
                    true
                }
            }

        allowFirstSyncToFinish.complete(Unit)
        val results = awaitAll(first, second)

        assertEquals(1, executionCount)
        assertTrue(results.all { it })
    }
}
