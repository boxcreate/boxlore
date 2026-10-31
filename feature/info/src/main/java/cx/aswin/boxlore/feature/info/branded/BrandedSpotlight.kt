package cx.aswin.boxlore.feature.info.branded

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.isTrailer
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeActionsCallbacks
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeActionsState
import cx.aswin.boxlore.feature.info.logic.FeedItem

/** The latest episode's live state for a branded page's spotlight card. */
@Immutable
internal data class BrandedSpotlight(
    val episode: Episode,
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
 * searching hides the spotlight, and the episode returns to its place in the list.
 */
internal fun brandedSpotlightEpisode(episodes: List<Episode>, sort: EpisodeSort, searching: Boolean): Episode? =
    if (sort != EpisodeSort.NEWEST || searching) null else episodes.firstOrNull { !it.isTrailer }

/**
 * The spotlight's list key is its episode's ID, the key that episode has in the list, so Select
 * visible counts it. The list below leaves that episode out, so the key is never used twice.
 */
internal fun brandedSpotlightKey(episode: Episode): String = episode.id

/** The spotlight's episode is not repeated in the list below it. */
internal fun feedItemsWithoutSpotlight(items: List<FeedItem>, spotlight: Episode?): List<FeedItem> =
    if (spotlight == null) items else items.filterNot { it is FeedItem.NormalEpisode && it.episode.id == spotlight.id }
