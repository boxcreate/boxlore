package cx.aswin.boxlore.core.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadAnalyticsMappingTest {
    @Test
    fun fileSizeMb_unknownLengthIsNullNotZero() {
        assertNull(DownloadAnalyticsMapping.fileSizeMb(-1L))
        assertNull(DownloadAnalyticsMapping.fileSizeMb(0L))
    }

    @Test
    fun fileSizeMb_positiveLengthConvertsToMb() {
        val oneMb = 1024L * 1024L
        assertEquals(1f, DownloadAnalyticsMapping.fileSizeMb(oneMb)!!, 0.0001f)
        assertEquals(2.5f, DownloadAnalyticsMapping.fileSizeMb((2.5f * oneMb).toLong())!!, 0.01f)
    }

    @Test
    fun source_unknownProvenanceIsNullNotManual() {
        assertNull(DownloadAnalyticsMapping.source(null))
        assertEquals("smart", DownloadAnalyticsMapping.source(true))
        assertEquals("manual", DownloadAnalyticsMapping.source(false))
    }

    @Test
    fun failureReason_isAllowlistedNeverRawMessage() {
        assertEquals("unknown", DownloadAnalyticsMapping.failureReason(null))
        assertEquals("io_error", DownloadAnalyticsMapping.failureReason(java.io.IOException("secret path")))
        assertEquals(
            "permission_denied",
            DownloadAnalyticsMapping.failureReason(SecurityException("denied")),
        )
        assertEquals(
            "illegal_state",
            DownloadAnalyticsMapping.failureReason(IllegalStateException("boom")),
        )
        assertEquals(
            "download_failed",
            DownloadAnalyticsMapping.failureReason(RuntimeException("https://cdn.example/secret")),
        )
    }
}
