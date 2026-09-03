package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

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
    fun canShowBanner(
        episode: Episode?,
        sameShowQueueOnly: Boolean,
    ): Boolean {
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
     */
    fun computeCandidates(
        allEpisodes: List<Episode>,
        currentEpisode: Episode,
        podcast: Podcast,
        excludeEpisodeIds: Set<String> = emptySet(),
        maxCount: Int = SameShowContinuationState.MAX_CONTINUATION_OFFER,
    ): List<Episode> {
        if (allEpisodes.isEmpty()) return emptyList()

        val sort = effectiveSort(podcast)
        val isSerialListening = podcast.type == "serial" || sort == "oldest"
        val newestFirst =
            !isSerialListening &&
                (sort == "newest" || podcast.genre.equals("News", ignoreCase = true))

        val currentPublished =
            allEpisodes.firstOrNull { it.id == currentEpisode.id }?.publishedDate
                ?: currentEpisode.publishedDate

        val rawCandidates =
            if (newestFirst) {
                allEpisodes
                    .filter { it.publishedDate > currentPublished }
                    .sortedByDescending { it.publishedDate }
            } else {
                val chronological = allEpisodes.sortedBy { it.publishedDate }
                val idx = chronological.indexOfFirst { it.id == currentEpisode.id }
                if (idx == -1) {
                    chronological.filter { it.publishedDate > currentPublished }
                } else {
                    chronological.drop(idx + 1)
                }
            }

        return rawCandidates
            .asSequence()
            .filter { it.id != currentEpisode.id }
            .filter { it.id !in excludeEpisodeIds }
            .filter { it.episodeType != "trailer" }
            .filter { it.audioUrl.isNotBlank() }
            .distinctBy { it.id }
            .take(maxCount)
            .toList()
    }
}
