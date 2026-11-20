package cx.aswin.boxlore.feature.history.timeline

import android.icu.text.DateFormat as IcuDateFormat
import android.icu.util.TimeZone as IcuTimeZone
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.history.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    /** "Wednesday, Oct 28", or "Wednesday, Oct 28, 2025": written in [locale]'s order. */
    fun dayTitleText(date: LocalDate, showYear: Boolean, locale: Locale): String =
        skeletonDate(date, if (showYear) "yMMMEEEEd" else "MMMEEEEd", locale)

    /** "October", or "October 2025" outside the current year. */
    fun monthTitle(month: YearMonth, today: LocalDate, locale: Locale): String =
        if (month.year == today.year) {
            month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        } else {
            monthYearTitle(month, locale)
        }

    /** "October 2026", the Jump to a day sheet's month. */
    fun monthYearTitle(month: YearMonth, locale: Locale): String =
        LocaleFormats.date(utcMillis(month.atDay(1)), LocaleFormats.DateStyle.MONTH_YEAR, locale, UTC)

    /** "Oct 27": the fast scroller's date and the calendar's day names. */
    fun shortDate(date: LocalDate, locale: Locale): String =
        LocaleFormats.date(utcMillis(date), LocaleFormats.DateStyle.DAY_MONTH, locale, UTC)

    /** "Mon, Sep 14": a short weekday with the day and month, for the story's biggest day. */
    fun shortWeekdayDate(date: LocalDate, locale: Locale): String = skeletonDate(date, "MMMEd", locale)

    /** "8:12 pm", or "20:12" when the phone uses a 24-hour clock; the day period is lower-cased as before. */
    fun clockTime(epochMs: Long, zoneId: ZoneId, is24Hour: Boolean, locale: Locale): String =
        LocaleFormats.time(epochMs, locale, is24Hour, TimeZone.getTimeZone(zoneId)).lowercase(locale)

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * A date from an ICU skeleton, so each language picks its own order and punctuation. Used for the
     * shapes [LocaleFormats.DateStyle] has no style for (weekday with a short month).
     */
    private fun skeletonDate(date: LocalDate, skeleton: String, locale: Locale): String {
        val format = IcuDateFormat.getInstanceForSkeleton(skeleton, locale)
        format.timeZone = IcuTimeZone.getTimeZone("UTC")
        return format.format(Date(utcMillis(date)))
    }

    sealed interface DayTitle {
        data object Today : DayTitle

        data object Yesterday : DayTitle

        data class Date(val date: LocalDate, val showYear: Boolean,) : DayTitle
    }
}

@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

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
