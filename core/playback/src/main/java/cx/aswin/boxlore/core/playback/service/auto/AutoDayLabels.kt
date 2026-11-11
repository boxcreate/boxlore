package cx.aswin.boxlore.core.playback.service.auto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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

    /** "14 Oct", or "14 Oct 2025" once the year would otherwise be unclear. */
    fun shortDate(publishedSeconds: Long, sameYear: Boolean, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val pattern = if (sameYear) "d MMM" else "d MMM yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochSecond(publishedSeconds).atZone(zone))
    }
}
