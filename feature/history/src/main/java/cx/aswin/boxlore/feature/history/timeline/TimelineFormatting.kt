package cx.aswin.boxlore.feature.history.timeline

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import cx.aswin.boxlore.feature.history.R
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Listening history's words for time: lengths ("2 h 05 min", "23 min"), day and month titles and
 * times of day. Lengths round down to whole minutes, so a day never reads longer than it was.
 */
object TimelineTime {
    private const val MS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L

    /** Whole hours and minutes in [ms], or null when under a minute. */
    fun hoursMinutes(ms: Long): Pair<Long, Long>? {
        val minutes = ms / MS_PER_MINUTE
        if (minutes <= 0L) return null
        return (minutes / MINUTES_PER_HOUR) to (minutes % MINUTES_PER_HOUR)
    }

    /** Today, yesterday, or the day of the week and date ("Wednesday, Oct 28"; the year when not this year). */
    fun dayTitleParts(date: LocalDate, today: LocalDate): DayTitle = when (date) {
        today -> DayTitle.Today
        today.minusDays(1) -> DayTitle.Yesterday
        else -> DayTitle.Date(date, showYear = date.year != today.year)
    }

    fun dayTitleText(date: LocalDate, showYear: Boolean, locale: Locale): String {
        val pattern = if (showYear) "EEEE, MMM d, yyyy" else "EEEE, MMM d"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** "October", or "October 2025" outside the current year. */
    fun monthTitle(month: YearMonth, today: LocalDate, locale: Locale): String {
        val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        return if (month.year == today.year) name else "$name ${month.year}"
    }

    /** "October 2026", the Jump to a day sheet's month. */
    fun monthYearTitle(month: YearMonth, locale: Locale): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year

    /** "Oct 27": the fast scroller's date and the calendar's day names. */
    fun shortDate(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("MMM d", locale))

    /** "8:12 pm", or "20:12" when the phone uses a 24-hour clock. */
    fun clockTime(epochMs: Long, zoneId: ZoneId, is24Hour: Boolean, locale: Locale): String {
        val time = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalTime()
        return if (is24Hour) {
            time.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        } else {
            time.format(DateTimeFormatter.ofPattern("h:mm a", locale)).lowercase(locale)
        }
    }

    sealed interface DayTitle {
        data object Today : DayTitle

        data object Yesterday : DayTitle

        data class Date(val date: LocalDate, val showYear: Boolean,) : DayTitle
    }
}

@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.getDefault()

/** A length of listening: "2 h 05 min", "23 min" or "Under a minute". */
@Composable
@ReadOnlyComposable
internal fun durationText(ms: Long): String {
    val parts = TimelineTime.hoursMinutes(ms) ?: return stringResource(R.string.history_duration_under_minute)
    val (hours, minutes) = parts
    return if (hours > 0L) {
        stringResource(R.string.history_duration_hours_minutes, hours.toInt(), minutes.toInt())
    } else {
        stringResource(R.string.history_duration_minutes, minutes.toInt())
    }
}

@Composable
@ReadOnlyComposable
internal fun timeLeftText(remainingMs: Long): String = stringResource(R.string.history_time_left, durationText(remainingMs))

@Composable
@ReadOnlyComposable
internal fun dayTitle(date: LocalDate, today: LocalDate): String = when (val title = TimelineTime.dayTitleParts(date, today)) {
    TimelineTime.DayTitle.Today -> stringResource(R.string.history_day_today)
    TimelineTime.DayTitle.Yesterday -> stringResource(R.string.history_day_yesterday)
    is TimelineTime.DayTitle.Date -> TimelineTime.dayTitleText(title.date, title.showYear, currentLocale())
}

@Composable
internal fun clockTime(epochMs: Long): String {
    val context = LocalContext.current
    return TimelineTime.clockTime(epochMs, ZoneId.systemDefault(), DateFormat.is24HourFormat(context), currentLocale())
}
