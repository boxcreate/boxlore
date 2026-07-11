package cx.aswin.boxcast.feature.player.v2.sheets

import cx.aswin.boxcast.core.model.Episode

/**
 * Small caption explaining why an item is in the queue, derived from the provenance
 * persisted on each queue row (contextType + contextSourceId).
 */
internal fun queueSourceLabel(episode: Episode): String? = when (episode.contextType) {
    "LORE" -> "From Lore"
    "AUTO_FILL" -> when (episode.contextSourceId) {
        "same_podcast" -> "Continuing series"
        "resume" -> "Pick up where you left off"
        "subscription" -> "From your subscriptions"
        "server_rec", "personalized_rec" -> "Recommended for you"
        "similar_episode" -> "Based on what you're playing"
        "similar_liked" -> "Based on something you liked"
        "trending" -> episode.podcastGenre
            ?.takeIf { it.isNotBlank() && it != "Podcast" }
            ?.let { "Trending in $it" } ?: "Trending now"
        else -> "Added for you"
    }
    else -> null // MANUAL and unknown rows get no label
}
