package cx.aswin.boxlore.core.designsystem.share

/**
 * An episode's details line: its length and the day it came out ("49 min · 30 Oct"), with the year
 * when it isn't this year. Either part is left out when unknown; null when both are.
 */
fun shareEpisodeDetails(
    durationSeconds: Int,
    publishedSeconds: Long,
    today: java.time.LocalDate = java.time.LocalDate.now(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    locale: java.util.Locale = java.util.Locale.getDefault(),
): String? {
    val length = shareLength(durationSeconds)
    val date = publishedSeconds.takeIf { it > 0 }?.let {
        val day = java.time.Instant.ofEpochSecond(it).atZone(zone).toLocalDate()
        val pattern = if (day.year == today.year) "d MMM" else "d MMM yyyy"
        day.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
    }
    return detailsLine(length, date)
}

/** "49 min" or "1 hr 12 min"; null under a minute (unknown lengths are 0). */
internal fun shareLength(seconds: Int): String? {
    if (seconds < 60) return null
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
}

/**
 * A show's details line: its genre and how often its feed says it posts ("Technology · Daily").
 * The catch-all "Podcast" genre says nothing, so it is left out; null when nothing is known.
 */
fun sharePodcastDetails(genre: String?, updateFrequency: String?): String? =
    detailsLine(genre?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Podcast", ignoreCase = true) }, shareFrequency(updateFrequency))

/** The feed's update tag as a word: a name ("weekly") or episodes a week ("0.5"). */
internal fun shareFrequency(tag: String?): String? {
    val clean = tag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return clean.toDoubleOrNull()?.let(::frequencyPerWeek) ?: frequencyName(clean)
}

private fun frequencyPerWeek(perWeek: Double): String? = when {
    perWeek >= 7.0 -> "Daily"
    perWeek >= 2.0 -> "Several times a week"
    perWeek >= 1.0 -> "Weekly"
    perWeek >= 0.5 -> "Every 2 weeks"
    perWeek >= 0.2 -> "Monthly"
    else -> null
}

private fun frequencyName(tag: String): String? = when {
    "daily" in tag -> "Daily"
    "bi-weekly" in tag || "biweekly" in tag || "2 weeks" in tag -> "Every 2 weeks"
    "weekly" in tag -> "Weekly"
    "monthly" in tag -> "Monthly"
    else -> null
}

/** Joins the known parts with " · "; each part keeps its words together, so a wrap only falls between parts. */
private fun detailsLine(vararg parts: String?): String? =
    parts.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.replace(' ', NoBreakSpace) }

private const val NoBreakSpace = '\u00A0'
