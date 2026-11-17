package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isTrailer

/**
 * Pure helper logic for evaluating same-show continuation availability
 * and computing candidate episodes when same-show continuation was skipped
 * due to a recommendation/discovery playback origin.
 */
object SameShowContinuationLogic {
    /**
     * Checks if the banner is allowed to be evaluated for [episode].
     *
     * Trigger conditions:
     * - [episode] is present and not a curiosity card (`learn:`) or briefing.
     * - [sameShowQueueOnly] preference is not enabled.
     * - `contextSourceId` is not in [SmartQueueEngine.SHOW_BINGE_SOURCES],
     *   meaning playback started from recommendations/discovery.
     */
    fun canShowBanner(episode: Episode?, sameShowQueueOnly: Boolean,): Boolean {
        if (episode == null) return false
        if (sameShowQueueOnly) return false
        if (episode.id.startsWith(QueueMath.LEARN_PREFIX)) return false
        if (episode.id.startsWith("briefing_") || episode.podcastId?.startsWith("briefing_") == true) return false
        val sourceId = episode.contextSourceId
        // If contextSourceId is null or in SHOW_BINGE_SOURCES, same-show continuation was not skipped.
        if (sourceId == null || sourceId in SmartQueueEngine.SHOW_BINGE_SOURCES) {
            return false
        }
        return true
    }

    /**
     * Resolves the effective sort order for same-show continuation.
     */
    fun effectiveSort(podcast: Podcast): String {
        podcast.preferredSort?.takeIf { it.isNotBlank() }?.let { return it }
        return if (podcast.type == "serial") "oldest" else "newest"
    }

    /**
     * Filters and orders candidates forward in time from [currentEpisode], excluding
     * the current track, existing queue items, trailers, and items without an audio URL.
     * When [allEpisodes] doesn't hold [currentEpisode], episodes newer than it count only if it
     * is no older than the window's oldest: a window that starts after it can't say what follows.
     */
    fun computeCandidates(
        allEpisodes: List<Episode>,
        currentEpisode: Episode,
        @Suppress("UNUSED_PARAMETER") podcast: Podcast,
        excludeEpisodeIds: Set<String> = emptySet(),
        maxCount: Int = SameShowContinuationState.MAX_CONTINUATION_OFFER,
    ): List<Episode> {
        if (allEpisodes.isEmpty()) return emptyList()

        val currentPublished =
            allEpisodes.firstOrNull { it.id == currentEpisode.id }?.publishedDate
                ?: currentEpisode.publishedDate

        val chronological = allEpisodes.sortedBy { it.publishedDate }
        val idx = chronological.indexOfFirst { it.id == currentEpisode.id }
        val rawCandidates =
            if (idx == -1) {
                forwardWithoutAnchor(chronological, currentPublished)
            } else {
                chronological.drop(idx + 1)
            }

        return rawCandidates
            .asSequence()
            .filter { it.id != currentEpisode.id }
            .filter { it.id !in excludeEpisodeIds }
            .filter { !it.isTrailer }
            .filter { it.audioUrl.isNotBlank() }
            .distinctBy { it.id }
            .take(maxCount)
            .toList()
    }

    /**
     * The episodes of [chronological] (oldest first) after an episode it doesn't hold, published at
     * [currentPublished]: none when that is before the window's oldest. The window then starts
     * somewhere past the listener's place, and its oldest isn't the episode that follows.
     */
    internal fun forwardWithoutAnchor(chronological: List<Episode>, currentPublished: Long): List<Episode> {
        val oldest = chronological.firstOrNull()?.publishedDate ?: return emptyList()
        if (currentPublished < oldest) return emptyList()
        return chronological.filter { it.publishedDate > currentPublished }
    }
}
