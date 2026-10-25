package cx.aswin.boxlore.navigation

import androidx.navigation.NavDeepLink
import androidx.navigation.navDeepLink

/**
 * Episode links that also name the episode's show. The share page opens
 * `boxlore://episode/<id>?show=<show>` (with `t` when the link has one), and newer share links carry
 * `&show=`; the show maps onto the episode route's existing `podcastId` argument, so the episode page
 * can find an episode it has no row for through that show. Every older pattern stays; these are only
 * added, and builds without them still open the same links without the show.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 * COMPAT(own-ids): links carrying `show` keep opening. Permanent: never remove (MIGRATION.md §5).
 */
internal object EpisodeShowLinks {
    val customSchemePatterns: List<String> = listOf(
        "boxlore://episode/{episodeId}?show={podcastId}&t={t}&start={start}&end={end}&autoplay={autoplay}",
        "boxlore://episode/{episodeId}?show={podcastId}&t={t}&autoplay={autoplay}",
        "boxlore://episode/{episodeId}?show={podcastId}&autoplay={autoplay}",
        "boxlore://episode/{episodeId}?show={podcastId}",
    )

    /** The HTTPS share patterns with `&show=` for one share page address. */
    fun sharePatterns(base: String): List<String> = listOf(
        "$base?type=episode&id={episodeId}&show={podcastId}&t={t}&start={start}&end={end}&autoplay={autoplay}",
        "$base?type=episode&id={episodeId}&show={podcastId}&t={t}&autoplay={autoplay}",
        "$base?type=episode&id={episodeId}&show={podcastId}&autoplay={autoplay}",
        "$base?type=episode&id={episodeId}&show={podcastId}",
    )
}

/**
 * Every deep link of the episode route: the custom-scheme patterns (boxlore and the legacy boxcast
 * scheme), the ones that name the show ([EpisodeShowLinks]), and the HTTPS share links
 * ([WebAppLinks.episodeShareDeepLinks]). Patterns are only ever added.
 */
internal fun episodeDeepLinks(): List<NavDeepLink> =
    (LEGACY_EPISODE_PATTERNS + EpisodeShowLinks.customSchemePatterns + BOXCAST_EPISODE_PATTERNS)
        .map { pattern -> navDeepLink { uriPattern = pattern } } + WebAppLinks.episodeShareDeepLinks()

/** The episode route's boxlore:// patterns from before links named the show. */
private val LEGACY_EPISODE_PATTERNS = listOf(
    "boxlore://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}",
    "boxlore://episode/{episodeId}?autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}",
    "boxlore://episode/{episodeId}?podcastId={podcastId}&podcastTitle={podcastTitle}",
    "boxlore://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}",
    "boxlore://episode/{episodeId}?t={t}&autoplay={autoplay}",
    "boxlore://episode/{episodeId}?autoplay={autoplay}",
    "boxlore://episode/{episodeId}",
)

/** The legacy boxcast:// episode patterns older links still use. */
private val BOXCAST_EPISODE_PATTERNS = listOf(
    "boxcast://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}",
    "boxcast://episode/{episodeId}?t={t}&autoplay={autoplay}",
    "boxcast://episode/{episodeId}?autoplay={autoplay}",
    "boxcast://episode/{episodeId}",
)
