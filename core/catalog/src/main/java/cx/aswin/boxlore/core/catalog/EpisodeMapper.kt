package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.EpisodeItem

fun EpisodeItem.toEpisode(): Episode = Episode(
    id = id.toString(),
    title = title,
    description = description ?: "",
    audioUrl = enclosureUrl ?: "",
    imageUrl = (image?.takeIf { it.isNotBlank() } ?: feedImage?.takeIf { it.isNotBlank() }).toHttps().takeIf { it.isNotEmpty() },
    podcastImageUrl = feedImage.toHttps().takeIf { it.isNotEmpty() },
    podcastTitle = feedTitle,
    podcastId = feedId?.toString(),
    duration = duration ?: 0,
    publishedDate = datePublished ?: 0L,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
    enclosureType = enclosureType
)
