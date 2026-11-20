package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.player.R

internal fun queueSourceLabel(episode: Episode): UiText? = when (episode.contextType) {
    "LORE" -> UiText.res(R.string.player_queue_source_lore)
    "AUTO_FILL" -> when (episode.contextSourceId) {
        "same_podcast" -> UiText.res(R.string.player_queue_source_same_podcast)
        "resume" -> UiText.res(R.string.player_queue_source_resume)
        "resume_stale" -> UiText.res(R.string.player_queue_source_resume_stale)
        "subscription" -> UiText.res(R.string.player_queue_source_subscription)
        "server_rec", "personalized_rec" -> UiText.res(R.string.player_queue_source_recommended)
        "similar_episode" -> UiText.res(R.string.player_queue_source_similar_episode)
        "similar_liked" -> UiText.res(R.string.player_queue_source_similar_liked)
        "trending" -> trendingLabel(episode.podcastGenre)
        else -> UiText.res(R.string.player_queue_source_added)
    }
    else -> null
}

/** "Trending in Technology", or "Trending now" when the genre is missing or just "Podcast". */
private fun trendingLabel(genre: String?): UiText = genre
    ?.takeIf { it.isNotBlank() && it != "Podcast" }
    ?.let { UiText.res(R.string.player_queue_source_trending_in, it) }
    ?: UiText.res(R.string.player_queue_source_trending)
