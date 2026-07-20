package cx.aswin.boxlore.core.downloads

/**
 * Pure helpers for download analytics property mapping. Keeps Media3 / Room callers from
 * inventing zero-byte sizes or mislabeling unknown provenance as manual.
 */
object DownloadAnalyticsMapping {
    /** Positive content lengths only; unknown / unset Media3 lengths stay null. */
    fun fileSizeMb(contentLength: Long): Float? = if (contentLength > 0L) contentLength / (1024f * 1024f) else null

    /**
     * Maps known smart/manual provenance. Null when the Room row (or flag) is missing so we
     * do not invent `"manual"`.
     */
    fun source(isSmartDownloaded: Boolean?): String? =
        when (isSmartDownloaded) {
            true -> "smart"
            false -> "manual"
            null -> null
        }
}
