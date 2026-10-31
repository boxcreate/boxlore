package cx.aswin.boxlore.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String? = null,
    val podcastImageUrl: String? = null,
    val podcastTitle: String? = null, // For Queue context
    val podcastId: String? = null, // For Queue fallback refill
    val podcastGenre: String? = null, // For Queue genre matching
    val podcastArtist: String? = null, // For display
    val duration: Int = 0, // seconds
    val publishedDate: Long = 0L, // Unix timestamp
    // --- Podcast 2.0 ---
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val transcripts: List<Transcript>? = null,
    val persons: List<Person>? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeType: String? = null, // "full", "trailer", "bonus"
    val contextType: String? = null,
    val contextSourceId: String? = null,
    val enclosureType: String? = null,
    val retrievalScore: Double? = null,
    val semanticScore: Double? = null,
    val recommendationSource: String? = null,
    val recommendationReason: String? = null,
    val serverRank: Int? = null,
    val recommendationAlgorithmVersion: String? = null,
    val language: String? = null,
    /**
     * boxlore's own ID for this episode, from its show and [catalogKey], when known. [id] stays
     * the local ID. Phase P4, plan section "Identity at every boundary".
     */
    val recipeId: Long? = null,
    /**
     * The episode's trimmed GUID, or its enclosure URL when it has none, when known.
     * Phase P4, plan section "Identity at every boundary".
     */
    val catalogKey: String? = null,
)

/** The longest an episode tagged as a trailer can run and still be treated as one: 15 minutes. */
const val TRAILER_MAX_DURATION_SECONDS = 15 * 60

/**
 * Whether this is a real trailer. Some feeds tag full-length episodes as trailers, so a "trailer"
 * longer than [TRAILER_MAX_DURATION_SECONDS] counts as a full episode. With no known duration the
 * feed's tag stands. [Episode.episodeType] keeps the feed's own value.
 */
val Episode.isTrailer: Boolean
    get() = episodeType.equals("trailer", ignoreCase = true) && duration <= TRAILER_MAX_DURATION_SECONDS

/** The feed's episode type, with full-length episodes the feed calls trailers read as "full". */
val Episode.effectiveEpisodeType: String?
    get() = if (episodeType.equals("trailer", ignoreCase = true) && !isTrailer) "full" else episodeType
