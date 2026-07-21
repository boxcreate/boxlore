package cx.aswin.boxlore.ui.libraryimport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class LibraryBackupAnalyticsErrorsTest {
    @Test
    fun fromThrowable_mapsIoAndPermission() {
        assertEquals(
            LibraryBackupAnalyticsErrors.IO_ERROR,
            LibraryBackupAnalyticsErrors.fromThrowable(IOException("disk"), LibraryBackupAnalyticsErrors.EXPORT_FAILED),
        )
        assertEquals(
            LibraryBackupAnalyticsErrors.PERMISSION_DENIED,
            LibraryBackupAnalyticsErrors.fromThrowable(
                SecurityException("denied"),
                LibraryBackupAnalyticsErrors.IMPORT_FAILED,
            ),
        )
    }

    @Test
    fun fromThrowable_usesFallbackForUnknown() {
        assertEquals(
            LibraryBackupAnalyticsErrors.EXPORT_FAILED,
            LibraryBackupAnalyticsErrors.fromThrowable(
                IllegalStateException("boom"),
                LibraryBackupAnalyticsErrors.EXPORT_FAILED,
            ),
        )
        assertEquals(
            LibraryBackupAnalyticsErrors.IMPORT_FAILED,
            LibraryBackupAnalyticsErrors.fromThrowable(
                RuntimeException("x"),
                LibraryBackupAnalyticsErrors.IMPORT_FAILED,
            ),
        )
    }
}
