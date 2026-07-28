package cx.aswin.boxlore.core.catalog

/**
 * Inputs for [PodcastRepository.getSimilarEpisodes].
 * Bundled to keep the repository signature under detekt's parameter-list threshold.
 */
data class SimilarEpisodesQuery(
    val episodeId: String,
    val podcastId: String,
    val title: String,
    val description: String,
    val podcastTitle: String,
    val categories: String = "",
    val author: String = "",
    val limit: Int = 10,
    val country: String? = null,
    val languages: List<String>? = null,
)
