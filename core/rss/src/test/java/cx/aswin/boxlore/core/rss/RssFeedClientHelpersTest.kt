package cx.aswin.boxlore.core.rss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure helper coverage for [RssFeedClient] duration/date parsers (Phase 2 PR3).
 *
 * Full fetch/parse against MockWebServer needs HTTPS + Android Xml (Robolectric);
 * those stay follow-ups. Helpers are the deterministic JVM slice.
 */
class RssFeedClientHelpersTest {
    @Test
    fun `parseDuration accepts seconds integer`() {
        assertEquals(90, parseDuration("90"))
        assertEquals(0, parseDuration("0"))
        assertEquals(0, parseDuration("not-a-duration"))
    }

    @Test
    fun `parseDuration accepts hms clock format`() {
        assertEquals(90, parseDuration("00:01:30"))
        assertEquals(65, parseDuration("1:05"))
        assertEquals(3661, parseDuration("1:01:01"))
    }

    @Test
    fun `parseDate accepts RFC1123 and epoch seconds`() {
        val rfc = parseDate("Wed, 01 Jan 2020 00:00:00 GMT")
        assertTrue(rfc != null && rfc > 0L)
        assertEquals(1_577_836_800L, parseDate("1577836800"))
        assertEquals(1_577_836_800L, parseDate("1577836800000"))
        assertNull(parseDate(""))
        assertNull(parseDate("not-a-date"))
    }

    /** Regression: dates with an RFC 822 zone name (EST, PDT...) parsed to nothing, so the episodes had no date. */
    @Test
    fun `parseDate reads RFC 822 zone names as their offsets`() {
        assertEquals(1_795_093_800L, parseDate("Thu, 19 Nov 2026 08:10:00 EST"))
        assertEquals(1_793_247_000L, parseDate("Thu, 29 Oct 2026 00:10:00 EDT"))
        assertEquals(1_767_614_400L, parseDate("Mon, 05 Jan 2026 06:00:00 CST"))
        assertEquals(1_781_114_400L, parseDate("Wed, 10 Jun 2026 12:00:00 MDT"))
        assertEquals(1_793_638_800L, parseDate("Mon, 02 Nov 2026 09:00:00 PST"))
        assertEquals(1_782_921_600L, parseDate("Wed, 01 Jul 2026 09:00:00 PDT"))
        assertEquals(1_577_836_800L, parseDate("Wed, 01 Jan 2020 00:00:00 UT"))
        assertEquals(1_577_836_800L, parseDate("Wed, 01 Jan 2020 00:00:00 UTC"))
        assertEquals(1_577_836_800L, parseDate("Wed, 01 Jan 2020 00:00:00 Z"))
        assertEquals(1_795_093_800L, parseDate("  Thu, 19 Nov 2026 08:10:00 est "))
    }

    @Test
    fun `parseDate keeps GMT and numeric offsets`() {
        assertEquals(1_577_836_800L, parseDate("Wed, 01 Jan 2020 00:00:00 GMT"))
        assertEquals(1_795_093_800L, parseDate("Thu, 19 Nov 2026 08:10:00 -0500"))
        assertEquals(1_795_093_800L, parseDate("19 Nov 2026 08:10:00 -0500"))
        assertEquals(1_795_093_800L, parseDate("2026-11-19T08:10:00-05:00"))
    }

    @Test
    fun `parseDate reads a date whose weekday is wrong`() {
        // 19 Nov 2026 is a Thursday.
        assertEquals(1_795_093_800L, parseDate("Fri, 19 Nov 2026 08:10:00 EST"))
        assertEquals(1_795_093_800L, parseDate("Fri, 19 Nov 2026 13:10:00 GMT"))
    }

    @Test
    fun `parseDate rejects unknown zone names and broken dates`() {
        assertNull(parseDate("Thu, 19 Nov 2026 08:10:00 XYZ"))
        assertNull(parseDate("Thu, 32 Nov 2026 08:10:00 EST"))
        assertNull(parseDate("Thu, 19 Nov 2026 EST"))
    }
}
