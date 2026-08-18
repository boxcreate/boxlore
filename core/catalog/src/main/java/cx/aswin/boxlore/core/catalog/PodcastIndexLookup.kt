package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Podcast

internal enum class ExactPodcastLookupType {
    FEED_URL,
    PODCAST_GUID,
}

internal data class ExactPodcastLookupKey(
    val type: ExactPodcastLookupType,
    val value: String,
)

internal sealed interface ExactPodcastLookupResult {
    data class Found(
        val podcast: Podcast,
    ) : ExactPodcastLookupResult

    data object NotFound : ExactPodcastLookupResult

    data object Failed : ExactPodcastLookupResult
}

internal sealed interface PodcastIndexSearchResult {
    data class Success(
        val podcasts: List<Podcast>,
    ) : PodcastIndexSearchResult

    data object Failed : PodcastIndexSearchResult
}
