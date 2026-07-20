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
}
