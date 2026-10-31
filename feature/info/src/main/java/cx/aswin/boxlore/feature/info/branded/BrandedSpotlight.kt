package cx.aswin.boxlore.feature.info.branded

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isTrailer
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeActionsCallbacks
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeActionsState
import cx.aswin.boxlore.feature.info.logic.FeedItem

/** The latest episode's live state for a branded page's spotlight card. */
@Immutable
internal data class BrandedSpotlight(
    val episode: Episode,
    /** The episode's own picture, or null when it has none ([episodeOwnPicture]). */
    val episodePicture: String?,
    /** The show's cover for a card to fall back on ([brandedShowPicture]), or null when the show has none. */
    val showPicture: String?,
    val actions: PodcastEpisodeActionsState,
    val callbacks: PodcastEpisodeActionsCallbacks,
    val isCompleted: Boolean,
    /** Listening progress while partly played, otherwise null. */
    val progress: Float?,
    val timeLeft: String?,
    val isSelected: Boolean,
    val onOpen: () -> Unit,
    val onLongPress: () -> Unit,
)

/**
 * The newest full episode leads a branded page in newest-first order. Sorting oldest first or
 * searching hides the spotlight, and the episode returns to its place in the list. With completed
 * episodes hidden, a finished latest episode is hidden too: no card, rather than an older episode
 * passed off as the latest.
 */
internal fun brandedSpotlightEpisode(episodes: List<Episode>, sort: EpisodeSort, searching: Boolean, hiddenIds: Set<String> = emptySet()): Episode? =
    if (sort != EpisodeSort.NEWEST || searching) null else episodes.firstOrNull { !it.isTrailer }?.takeUnless { it.id in hiddenIds }

/**
 * The spotlight's list key is its episode's ID, the key that episode has in the list, so Select
 * visible counts it. The list below leaves that episode out, so the key is never used twice.
 */
internal fun brandedSpotlightKey(episode: Episode): String = episode.id

/**
 * The episode's own picture, or null when it has none. The catalogue fills a missing episode picture
 * with the show's cover, so a picture that is one of [showPictures] (or the episode's own copy of
 * the cover) counts as none, and a page can set its own stand-in.
 */
internal fun episodeOwnPicture(episode: Episode, showPictures: List<String?>): String? {
    val picture = episode.imageUrl?.takeIf { it.isNotBlank() } ?: return null
    val covers = (showPictures + episode.podcastImageUrl).mapNotNull { cover -> cover?.takeIf(String::isNotBlank)?.let(::pictureAddress) }
    return picture.takeUnless { pictureAddress(it) in covers }
}

/**
 * The latest episode's own picture on [podcast]'s page. Only the show's cover counts as no picture:
 * the page's fallback show image is often this very episode's picture, standing in for a missing cover.
 */
internal fun brandedEpisodePicture(episode: Episode, podcast: Podcast): String? = episodeOwnPicture(episode, listOf(podcast.imageUrl))

/** The show's cover on [podcast]'s page, or its fallback image when the catalogue has no cover. */
internal fun brandedShowPicture(podcast: Podcast): String? =
    podcast.imageUrl.takeIf(String::isNotBlank) ?: podcast.fallbackImageUrl?.takeIf(String::isNotBlank)

/** Query parameters that only size, crop or track a picture: the same file asked for differently. */
private val PictureSizingParameters = setOf(
    "ixlib", "w", "h", "width", "height", "max-w", "max-h", "fit", "auto", "q", "quality",
    "fm", "format", "dpr", "crop", "aid", "size", "resize",
)

/**
 * A picture's address without its scheme, trailing slash or sizing parameters, so one file served
 * two ways still matches. Other parameters stay: some hosts name the picture itself in the query.
 */
private fun pictureAddress(url: String): String {
    val address = url.trim().substringAfter("://").substringBefore('#')
    val path = address.substringBefore('?').trimEnd('/')
    val query = address.substringAfter('?', "").split('&')
        .filter { it.isNotBlank() && it.substringBefore('=').lowercase() !in PictureSizingParameters }
        .sorted()
    return if (query.isEmpty()) path else query.joinToString("&", prefix = "$path?")
}

/** An episode's length as a card types it: "45 min" or "1 hr 5 min"; empty under a minute. */
internal fun brandedLength(durationSeconds: Int): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    return when {
        durationSeconds < 60 -> ""
        hours > 0 -> "$hours hr $minutes min"
        else -> "$minutes min"
    }
}

/** The spotlight's episode is not repeated in the list below it. */
internal fun feedItemsWithoutSpotlight(items: List<FeedItem>, spotlight: Episode?): List<FeedItem> =
    if (spotlight == null) items else items.filterNot { it is FeedItem.NormalEpisode && it.episode.id == spotlight.id }
