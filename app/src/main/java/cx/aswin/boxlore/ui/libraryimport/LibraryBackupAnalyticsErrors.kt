package cx.aswin.boxlore.ui.libraryimport

/**
 * Allowlisted analytics error codes for backup / OPML / JSON import-export.
 * Never send raw [Throwable.message] into PostHog — UI toasts may still show it.
 */
object LibraryBackupAnalyticsErrors {
    const val EXPORT_FAILED = "export_failed"
    const val IMPORT_FAILED = "import_failed"
    const val IO_ERROR = "io_error"
    const val PERMISSION_DENIED = "permission_denied"
    const val FILE_OPEN_FAILED = "file_open_failed"
    const val NO_FEEDS = "no_feeds"
    const val NO_PODCASTS_RESOLVED = "no_podcasts_resolved"
    const val MARK_COMPLETED_FAILED = "mark_completed_failed"

    fun fromThrowable(
        error: Throwable,
        fallback: String,
    ): String =
        when (error) {
            is java.io.IOException -> IO_ERROR
            is SecurityException -> PERMISSION_DENIED
            else -> fallback
        }
}
