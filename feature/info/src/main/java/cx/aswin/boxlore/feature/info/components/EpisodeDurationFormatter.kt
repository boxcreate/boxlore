package cx.aswin.boxlore.feature.info.components

import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.info.R
import java.util.Locale

/** An episode's length in the language's short units ("1h 5m", "45m"); blank under a minute. */
internal fun formatEpisodeDuration(seconds: Int, locale: Locale): String {
    if (seconds < 60) return ""
    return LocaleFormats.duration(seconds.toLong(), locale, LocaleFormats.DurationWidth.NARROW)
}

/** "1h 5m" or "45m", as the full episode card writes it; blank when the length is unknown. */
internal fun formatEpisodeListDuration(seconds: Int, locale: Locale): String {
    if (seconds <= 0) return ""
    return LocaleFormats.duration(seconds.toLong(), locale, LocaleFormats.DurationWidth.NARROW)
}

/** "2h ago" style age of an episode published at [timestampSeconds]; blank when the date is unknown. */
internal fun formatEpisodeRelativeDate(timestampSeconds: Long, locale: Locale, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
    if (timestampSeconds == 0L) return ""
    return LocaleFormats.relativeTime(timestampSeconds * 1_000L, nowSeconds * 1_000L, locale, LocaleFormats.RelativeStyle.NARROW)
}

/** "S2 E5", "S2" or "E12" as an episode row abbreviates the feed's numbers; null when it numbers neither. */
internal fun seasonEpisodeShort(season: Int?, episode: Int?): UiText? = when {
    season != null && episode != null -> UiText.res(R.string.episode_list_season_episode_short, season, episode)
    season != null -> UiText.res(R.string.episode_list_season_short, season)
    episode != null -> UiText.res(R.string.episode_list_episode_short, episode)
    else -> null
}
