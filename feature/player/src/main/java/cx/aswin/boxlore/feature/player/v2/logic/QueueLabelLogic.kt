package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.model.Episode

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
    else -> null
}
