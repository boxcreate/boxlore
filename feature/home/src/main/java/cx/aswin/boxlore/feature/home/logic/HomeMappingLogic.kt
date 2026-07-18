package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

internal fun Episode.toRecommendationPodcast(): Podcast =
    Podcast(
        id = podcastId.orEmpty(),
        title = podcastTitle.orEmpty(),
        artist = podcastArtist.orEmpty(),
        imageUrl = podcastImageUrl ?: imageUrl.orEmpty(),
        latestEpisode = this,
    )
