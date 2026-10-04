package cx.aswin.boxlore.lifecycle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDownloadForegroundScanTest {
    @Test fun neitherResumeNorPreferenceScansRunOutsideForeground() = runTest {
        var loads = 0
        val scanned = mutableListOf<String>()
        val scans = AutoDownloadForegroundScan(backgroundScope, {
            loads++
            setOf("resume")
        }, { id, _ -> scanned += id })
        scans.request()
        scans.request(setOf("preferences"))
        runCurrent()
        assertEquals(0, loads)
        assertTrue(scanned.isEmpty())
    }

    @Test fun stoppingCancelsBothResumeAndPreferenceScanJobs() = runTest {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val scans = AutoDownloadForegroundScan(backgroundScope, { setOf("resume", "later") }, { id, _ ->
            started += id
            try {
                awaitCancellation()
            } finally {
                cancelled += id
            }
        })
        scans.setForeground(true)
        scans.request()
        runCurrent()
        scans.request(setOf("preferences", "later"))
        runCurrent()
        scans.setForeground(false)
        runCurrent()
        assertEquals(listOf("resume", "preferences"), started)
        assertEquals(started, cancelled)
    }

    @Test fun stoppingRevokesInFlightEnqueueAdmissionAndSkipsRemainingIds() = runTest {
        val started = mutableListOf<String>()
        val enqueued = mutableListOf<String>()
        lateinit var scans: AutoDownloadForegroundScan
        scans = AutoDownloadForegroundScan(backgroundScope, { setOf("first", "later") }, { id, canProceed ->
            started += id
            assertTrue(canProceed())
            scans.setForeground(false)
            if (canProceed()) enqueued += id
        })
        scans.setForeground(true)
        scans.request()
        runCurrent()
        assertEquals(listOf("first"), started)
        assertTrue(enqueued.isEmpty())
    }

    @Test fun foregroundResumeReplaysTheInterruptedScan() = runTest {
        val started = mutableListOf<String>()
        var interruptFirst = true
        val scans = AutoDownloadForegroundScan(backgroundScope, { setOf("first", "later") }, { id, _ ->
            started += id
            if (interruptFirst) {
                interruptFirst = false
                awaitCancellation()
            }
        })
        scans.setForeground(true)
        scans.request()
        runCurrent()
        scans.setForeground(false)
        runCurrent()
        scans.setForeground(true)
        scans.request()
        runCurrent()
        assertEquals(listOf("first", "first", "later"), started)
    }

    @Test fun resumingDoesNotRestoreAdmissionForACancelledScan() = runTest {
        val guards = mutableListOf<suspend () -> Boolean>()
        val scans = AutoDownloadForegroundScan(backgroundScope, { setOf("show") }, { _, canProceed ->
            guards += canProceed
            awaitCancellation()
        })
        scans.setForeground(true)
        scans.request()
        runCurrent()
        scans.setForeground(false)
        runCurrent()
        scans.setForeground(true)
        scans.request()
        runCurrent()
        assertFalse(guards.first()())
        assertTrue(guards.last()())
    }
}
