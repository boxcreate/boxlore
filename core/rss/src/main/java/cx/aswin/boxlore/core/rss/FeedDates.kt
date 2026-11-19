package cx.aswin.boxlore.core.rss

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A feed date in epoch seconds, or null when it can't be read: epoch seconds or milliseconds, an
 * RSS date (RFC 822, see [parseRfc822Date]) or an ISO 8601 date.
 */
internal fun parseDate(value: String): Long? {
    if (value.isBlank()) return null
    value.toLongOrNull()?.let { numeric ->
        return if (numeric > 10_000_000_000L) numeric / 1000L else numeric
    }
    val text = value.trim()
    return sequenceOf(
        { parseRfc822Date(text) },
        { OffsetDateTime.parse(text).toEpochSecond() },
        { Instant.parse(text).epochSecond },
    ).firstNotNullOfOrNull { parser -> runCatching(parser).getOrNull() }
}

/**
 * An RSS `pubDate` as feeds write it. Java's RFC 1123 parser reads only `GMT` and numeric offsets,
 * so the other RFC 822 zone names (`EST`, `PDT`, `UT`, `Z`...) and `UTC` become their offsets
 * first. A weekday that doesn't match the date is dropped, because the date is what the feed means.
 */
private fun parseRfc822Date(text: String): Long {
    val zoned = withNumericZone(text)
    val parsed =
        runCatching { ZonedDateTime.parse(zoned, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrElse { error ->
            val withoutWeekday = zoned.substringAfter(',', missingDelimiterValue = "").trim()
            if (withoutWeekday.isEmpty()) throw error
            ZonedDateTime.parse(withoutWeekday, DateTimeFormatter.RFC_1123_DATE_TIME)
        }
    return parsed.toEpochSecond()
}

private fun withNumericZone(text: String): String {
    val zone = text.substringAfterLast(' ', missingDelimiterValue = "")
    val offset = RFC822_ZONE_OFFSETS[zone.uppercase(Locale.ROOT)] ?: return text
    return text.dropLast(zone.length) + offset
}

private val RFC822_ZONE_OFFSETS =
    mapOf(
        "UT" to "+0000",
        "UTC" to "+0000",
        "Z" to "+0000",
        "EST" to "-0500",
        "EDT" to "-0400",
        "CST" to "-0600",
        "CDT" to "-0500",
        "MST" to "-0700",
        "MDT" to "-0600",
        "PST" to "-0800",
        "PDT" to "-0700",
    )
