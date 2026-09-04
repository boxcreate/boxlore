package cx.aswin.boxlore.core.catalog.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LibraryBackupImportLogicTest {
    @Test
    fun runRestoreRethrowsCancellation() = runTest {
        assertThrows<CancellationException> {
            LibraryBackupImportLogic.runRestore(
                block = { throw CancellationException("cancelled") },
                onFailure = { error("should not log cancellation") },
            )
        }
    }

    @Test
    fun runRestoreReturnsNullForOrdinaryFailure() = runTest {
        var logged = false
        val result =
            LibraryBackupImportLogic.runRestore<String>(
                block = { error("rss down") },
                onFailure = { logged = true },
            )
        assertNull(result)
        assertTrue(logged)
    }

    @Test
    fun opmlImportCountRethrowsCancellation() = runTest {
        assertThrows<CancellationException> {
            LibraryBackupImportLogic.opmlImportCount {
                throw CancellationException("cancelled")
            }
        }
    }

    @Test
    fun opmlImportCountReturnsMinusOneForOrdinaryFailure() = runTest {
        var logged = false
        val count =
            LibraryBackupImportLogic.opmlImportCount(
                onFailure = { logged = true },
            ) { error("parse failed") }
        assertEquals(-1, count)
        assertTrue(logged)
    }
}
