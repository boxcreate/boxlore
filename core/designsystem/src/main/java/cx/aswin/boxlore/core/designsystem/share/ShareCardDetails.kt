package cx.aswin.boxlore.core.designsystem.share

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.text.LocaleFormats
import java.util.Locale
import java.util.TimeZone

/**
 * An episode's details line in [locale]: its length and the day it came out ("49 min · 30 Oct"),
 * with the year when it isn't this year. Either part is left out when unknown; null when both are.
 */
fun shareEpisodeDetails(
    durationSeconds: Int,
    publishedSeconds: Long,
    today: java.time.LocalDate = java.time.LocalDate.now(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    val length = shareLength(durationSeconds, locale)
    val date = publishedSeconds.takeIf { it > 0 }?.let {
        val day = java.time.Instant.ofEpochSecond(it).atZone(zone).toLocalDate()
        val style = if (day.year == today.year) LocaleFormats.DateStyle.DAY_MONTH else LocaleFormats.DateStyle.DAY_MONTH_YEAR
        LocaleFormats.date(it * MILLIS_PER_SECOND, style, locale, TimeZone.getTimeZone(zone))
    }
    return detailsLine(length, date)
}

/** "49 min" or "1 hr, 12 min" in English; null under a minute (unknown lengths are 0). */
internal fun shareLength(seconds: Int, locale: Locale): String? =
    seconds.takeIf { it >= SECONDS_PER_MINUTE }?.let { LocaleFormats.duration(it.toLong(), locale, LocaleFormats.DurationWidth.SHORT) }

/**
 * A show's details line: its genre and how often its feed says it posts ("Technology · Daily").
 * The catch-all "Podcast" genre says nothing, so it is left out; null when nothing is known. The
 * genre is the feed's own text and stays as it is.
 */
fun sharePodcastDetails(genre: String?, updateFrequency: String?, resources: Resources): String? = detailsLine(
    genre?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Podcast", ignoreCase = true) },
    shareFrequency(updateFrequency)?.let(resources::getString),
)

/** [sharePodcastDetails] in the screen's language. */
@Composable
@ReadOnlyComposable
fun sharePodcastDetails(genre: String?, updateFrequency: String?): String? {
    LocalConfiguration.current
    return sharePodcastDetails(genre, updateFrequency, LocalContext.current.resources)
}

/** The feed's update tag as words: a name ("weekly") or episodes a week ("0.5"). */
@StringRes
internal fun shareFrequency(tag: String?): Int? {
    val clean = tag?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return clean.toDoubleOrNull()?.let(::frequencyPerWeek) ?: frequencyName(clean)
}

@StringRes
private fun frequencyPerWeek(perWeek: Double): Int? = when {
    perWeek >= 7.0 -> R.string.share_frequency_daily
    perWeek >= 2.0 -> R.string.share_frequency_several_weekly
    perWeek >= 1.0 -> R.string.share_frequency_weekly
    perWeek >= 0.5 -> R.string.share_frequency_every_two_weeks
    perWeek >= 0.2 -> R.string.share_frequency_monthly
    else -> null
}

@StringRes
private fun frequencyName(tag: String): Int? = when {
    "daily" in tag -> R.string.share_frequency_daily
    "bi-weekly" in tag || "biweekly" in tag || "2 weeks" in tag -> R.string.share_frequency_every_two_weeks
    "weekly" in tag -> R.string.share_frequency_weekly
    "monthly" in tag -> R.string.share_frequency_monthly
    else -> null
}

/** Joins the known parts with " · "; each part keeps its words together, so a wrap only falls between parts. */
private fun detailsLine(vararg parts: String?): String? =
    parts.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.replace(' ', NoBreakSpace) }

private const val NoBreakSpace = ' '
private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1_000L
