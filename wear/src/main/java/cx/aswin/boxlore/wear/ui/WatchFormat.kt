package cx.aswin.boxlore.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.wear.R
import java.util.Locale
import java.util.TimeZone

/** Pure text helpers shared by the watch screens. */
internal object WatchFormat {
    /** A short day and month, such as "6 Nov" or "Nov 6", in the listener's language and order. */
    fun shortDate(epochMillis: Long, timeZone: TimeZone, locale: Locale): String =
        LocaleFormats.date(epochMillis, LocaleFormats.DateStyle.DAY_MONTH, locale, timeZone)

    /** A file size's number and unit, before the unit's words are added. */
    data class Size(val amount: String, val inGigabytes: Boolean)

    /** A file size for a small screen: whole megabytes below a gigabyte, one decimal above. */
    fun size(bytes: Long, locale: Locale): Size {
        val megabytes = bytes.coerceAtLeast(0L) / BYTES_PER_MB.toDouble()
        if (megabytes < MB_PER_GB) return Size(String.format(locale, "%.0f", megabytes), inGigabytes = false)
        val gigabytes = megabytes / MB_PER_GB
        val whole = gigabytes == Math.floor(gigabytes)
        return Size(String.format(locale, if (whole) "%.0f" else "%.1f", gigabytes), inGigabytes = true)
    }

    private const val BYTES_PER_MB = 1024L * 1024L
    private const val MB_PER_GB = 1000

    /** Whole minutes left of [durationMinutes] at [progress], never below one while unfinished. */
    fun minutesLeft(durationMinutes: Int, progress: Float): Int {
        val left = durationMinutes * (1f - progress.coerceIn(0f, 1f))
        return if (left <= 0f) 0 else maxOf(1, left.toInt())
    }
}

/** [bytes] as "30 MB" or "1.5 GB", with the number and unit in the listener's language. */
@Composable
internal fun fileSizeText(bytes: Long): String {
    val size = WatchFormat.size(bytes, LocalConfiguration.current.locales[0])
    return stringResource(if (size.inGigabytes) R.string.wear_size_gigabytes else R.string.wear_size_megabytes, size.amount)
}
