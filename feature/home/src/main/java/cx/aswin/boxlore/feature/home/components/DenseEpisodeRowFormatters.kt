package cx.aswin.boxlore.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.ListeningTime
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.home.R

internal fun isNewEpisode(
    episode: Episode,
    podcast: Podcast,
    status: EpisodeStatus,
): Boolean = status == EpisodeStatus.UNPLAYED &&
    podcast.subscribedAt > 0L &&
    episode.publishedDate > (podcast.subscribedAt / 1000L - 7 * 24 * 3600L)

/** The row's length, or its time left while in progress ("1h 5m", "12m left"), in the app language. */
@Composable
internal fun denseDurationText(
    durationSeconds: Int,
    progress: Float,
    isInProgress: Boolean,
): String? {
    if (durationSeconds <= 0) return null
    val locale = currentLocale()
    if (isInProgress && progress > 0f) {
        // Rounded as the hero, Daily Mix and the mini player round time left: a partial minute counts.
        val minutes = ListeningTime.minutesLeft(durationSeconds.toLong(), progress) ?: 1L
        return stringResource(R.string.home_hero_remaining, LocaleFormats.duration(minutes * 60L, locale, LocaleFormats.DurationWidth.NARROW))
    }
    return LocaleFormats.duration(durationSeconds.toLong(), locale, LocaleFormats.DurationWidth.NARROW)
}
