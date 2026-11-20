package cx.aswin.boxlore.core.playback.service.auto

import cx.aswin.boxlore.core.model.text.LocaleFormats
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone

/** How long ago an episode came out, counted in calendar days, for the car's episode rows. */
internal object AutoDayLabels {
    /**
     * Calendar days between [publishedSeconds] (Unix seconds) and [nowMillis] in [zone]: 0 is today,
     * 1 is yesterday. Null for a missing date or one in the future.
     */
    fun daysAgo(publishedSeconds: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (publishedSeconds <= 0) return null
        val published = Instant.ofEpochSecond(publishedSeconds).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(published, today).takeIf { it >= 0 }
    }

    /** "14 Oct" ("Oct 14" in US English), or with the year once it would otherwise be unclear, in [locale]'s order. */
    fun shortDate(publishedSeconds: Long, sameYear: Boolean, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String = LocaleFormats.date(
        epochMillis = publishedSeconds * MILLIS_PER_SECOND,
        style = if (sameYear) LocaleFormats.DateStyle.DAY_MONTH else LocaleFormats.DateStyle.DAY_MONTH_YEAR,
        locale = locale,
        timeZone = TimeZone.getTimeZone(zone),
    )

    private const val MILLIS_PER_SECOND = 1_000L
}
