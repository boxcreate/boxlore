package cx.aswin.boxlore.core.model.text

import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.icu.text.DateFormat
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import android.text.format.DateFormat as AndroidDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Dates, times, durations, relative times and numbers written the way the listener's language
 * writes them. Every function takes the [Locale] to write in: [Context.displayLocale] in code with
 * a context, `currentLocale()` (`:core:designsystem`) in Compose. Never build these from fixed
 * patterns ("MMM d, yyyy", "h:mm a") or hand-made units ("min", "h", "ago").
 *
 * Upper- and lower-casing display text also takes the locale: `text.uppercase(locale)`.
 */
object LocaleFormats {
    /** Which parts of a date to show; each language orders and spells them its own way. */
    enum class DateStyle(internal val skeleton: String) {
        /** "Mar 5" */
        DAY_MONTH("MMMd"),

        /** "Mar 5, 2026" */
        DAY_MONTH_YEAR("yMMMd"),

        /** "March 5, 2026" */
        LONG_DAY_MONTH_YEAR("yMMMMd"),

        /** "Thursday, March 5" */
        WEEKDAY_DAY_MONTH("MMMMEEEEd"),

        /** "Thursday, March 5, 2026" */
        WEEKDAY_DAY_MONTH_YEAR("yMMMMEEEEd"),

        /** "March 2026" */
        MONTH_YEAR("yMMMM"),

        /** "March" (standalone month name) */
        MONTH("LLLL"),

        /** "Thursday" (standalone weekday name) */
        WEEKDAY("cccc"),

        /** "Thu" */
        SHORT_WEEKDAY("ccc"),

        /** "3/5/2026" */
        NUMERIC("yMd"),
    }

    /** How long unit names are in [duration]: "1h 5m", "1 hr, 5 min" or "1 hour, 5 minutes" in English. */
    enum class DurationWidth(internal val icu: MeasureFormat.FormatWidth) {
        NARROW(MeasureFormat.FormatWidth.NARROW),
        SHORT(MeasureFormat.FormatWidth.SHORT),
        WIDE(MeasureFormat.FormatWidth.WIDE),
    }

    /** How long relative times are: "3 hours ago", "3 hr. ago" or "3h ago" in English. */
    enum class RelativeStyle(internal val icu: RelativeDateTimeFormatter.Style) {
        LONG(RelativeDateTimeFormatter.Style.LONG),
        SHORT(RelativeDateTimeFormatter.Style.SHORT),
        NARROW(RelativeDateTimeFormatter.Style.NARROW),
    }

    fun date(
        epochMillis: Long,
        style: DateStyle,
        locale: Locale,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String = formatSkeleton(style.skeleton, epochMillis, locale, timeZone)

    /**
     * A time of day. [use24Hour] is the device's clock setting
     * (`android.text.format.DateFormat.is24HourFormat(context)`); null uses the language's habit.
     */
    fun time(
        epochMillis: Long,
        locale: Locale,
        use24Hour: Boolean? = null,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val skeleton = when (use24Hour) {
            null -> "jm"
            true -> "Hm"
            false -> "hm"
        }
        return formatSkeleton(skeleton, epochMillis, locale, timeZone)
    }

    /**
     * A length of time: hours and minutes ("1 hr, 5 min"), whole hours, or minutes; under a minute it
     * shows seconds. Negative values count as zero.
     */
    fun duration(totalSeconds: Long, locale: Locale, width: DurationWidth = DurationWidth.SHORT): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val measures = when {
            hours > 0 && minutes > 0 -> listOf(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
            hours > 0 -> listOf(Measure(hours, MeasureUnit.HOUR))
            minutes > 0 -> listOf(Measure(minutes, MeasureUnit.MINUTE))
            else -> listOf(Measure(seconds, MeasureUnit.SECOND))
        }
        return MeasureFormat.getInstance(locale, width.icu).formatMeasures(*measures.toTypedArray())
    }

    /**
     * How long ago (or how far ahead) [epochMillis] is from [nowMillis]: "now" under a minute, then
     * minutes, hours, days, weeks (from 7 days), months (from 30 days) and years (from 365 days).
     * [capitalized] starts the text with a capital where the language allows it, for a line on its own.
     */
    fun relativeTime(
        epochMillis: Long,
        nowMillis: Long,
        locale: Locale,
        style: RelativeStyle = RelativeStyle.LONG,
        capitalized: Boolean = false,
    ): String {
        val formatter = RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(locale),
            NumberFormat.getInstance(locale),
            style.icu,
            if (capitalized) {
                android.icu.text.DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
            } else {
                android.icu.text.DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE
            },
        )
        val deltaSeconds = (epochMillis - nowMillis) / MILLIS_PER_SECOND
        val sign = if (deltaSeconds < 0) -1 else 1
        val magnitude = abs(deltaSeconds)
        if (magnitude < SECONDS_PER_MINUTE) {
            return formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
        }
        val days = magnitude / SECONDS_PER_DAY
        val (amount, unit) = when {
            magnitude < SECONDS_PER_HOUR -> magnitude / SECONDS_PER_MINUTE to RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE
            magnitude < SECONDS_PER_DAY -> magnitude / SECONDS_PER_HOUR to RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR
            days < DAYS_PER_WEEK -> days to RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
            days < DAYS_PER_MONTH -> days / DAYS_PER_WEEK to RelativeDateTimeFormatter.RelativeDateTimeUnit.WEEK
            days < DAYS_PER_YEAR -> days / DAYS_PER_MONTH to RelativeDateTimeFormatter.RelativeDateTimeUnit.MONTH
            else -> days / DAYS_PER_YEAR to RelativeDateTimeFormatter.RelativeDateTimeUnit.YEAR
        }
        return formatter.formatNumeric((sign * amount).toDouble(), unit)
    }

    /**
     * A compact age for tight rows. English keeps boxlore's own "5m ago", "3h ago", "2d ago", "1w ago",
     * "3mo ago", "1y ago" (the same steps as [relativeTime]); other languages use [relativeTime]'s
     * narrow style. Under a minute callers show their own "Just now".
     */
    fun compactAgo(epochMillis: Long, nowMillis: Long, locale: Locale): String {
        if (locale.language != "en") return relativeTime(epochMillis, nowMillis, locale, RelativeStyle.NARROW)
        val seconds = ((nowMillis - epochMillis) / MILLIS_PER_SECOND).coerceAtLeast(0L)
        val days = seconds / SECONDS_PER_DAY
        val (amount, unit) = when {
            seconds < SECONDS_PER_HOUR -> seconds / SECONDS_PER_MINUTE to "m"
            seconds < SECONDS_PER_DAY -> seconds / SECONDS_PER_HOUR to "h"
            days < DAYS_PER_WEEK -> days to "d"
            days < DAYS_PER_MONTH -> days / DAYS_PER_WEEK to "w"
            days < DAYS_PER_YEAR -> days / DAYS_PER_MONTH to "mo"
            else -> days / DAYS_PER_YEAR to "y"
        }
        return "$amount$unit ago" // i18n-ignore: English-only compact form; other languages use ICU above
    }

    /** A whole number with the language's grouping: "12,345" or "12.345". */
    fun number(value: Long, locale: Locale): String = NumberFormat.getInstance(locale).format(value)

    /** A short count for tight spaces: "1.5M" in English, "1,5 Mio." in German. */
    fun compactNumber(value: Long, locale: Locale): String =
        CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(value)

    /** A share as a percentage, [fraction] 0.0–1.0: "42%" or "42 %". */
    fun percent(fraction: Double, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(fraction)

    private fun formatSkeleton(skeleton: String, epochMillis: Long, locale: Locale, timeZone: TimeZone): String {
        val format = DateFormat.getInstanceForSkeleton(skeleton, locale)
        format.timeZone = IcuTimeZone.getTimeZone(timeZone.id)
        return format.format(Date(epochMillis))
    }

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
    private const val DAYS_PER_WEEK = 7L
    private const val DAYS_PER_MONTH = 30L
    private const val DAYS_PER_YEAR = 365L
}

/** The language this context's text is shown in: the app language when one is set, else the system's. */
fun Context.displayLocale(): Locale = resources.configuration.locales[0] ?: Locale.getDefault()

/** [LocaleFormats.time] with the device's 12/24-hour clock setting. */
fun Context.formatTimeOfDay(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
    LocaleFormats.time(epochMillis, displayLocale(), AndroidDateFormat.is24HourFormat(this), timeZone)
