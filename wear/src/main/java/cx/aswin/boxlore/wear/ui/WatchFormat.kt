package cx.aswin.boxlore.wear.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure text helpers shared by the watch screens. */
internal object WatchFormat {
    /** A short day and month, such as "6 Nov", in the listener's language. */
    fun shortDate(epochMillis: Long, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("d MMM", locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** A file size for a small screen: whole megabytes below a gigabyte, one decimal above. */
    fun megabytes(bytes: Long, locale: Locale): String {
        val megabytes = bytes.coerceAtLeast(0L) / BYTES_PER_MB.toDouble()
        if (megabytes < MB_PER_GB) return String.format(locale, "%.0f MB", megabytes)
        val gigabytes = megabytes / MB_PER_GB
        val whole = gigabytes == Math.floor(gigabytes)
        return String.format(locale, if (whole) "%.0f GB" else "%.1f GB", gigabytes)
    }

    private const val BYTES_PER_MB = 1024L * 1024L
    private const val MB_PER_GB = 1000

    /** Whole minutes left of [durationMinutes] at [progress], never below one while unfinished. */
    fun minutesLeft(durationMinutes: Int, progress: Float): Int {
        val left = durationMinutes * (1f - progress.coerceIn(0f, 1f))
        return if (left <= 0f) 0 else maxOf(1, left.toInt())
    }
}
