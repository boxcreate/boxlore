package cx.aswin.boxlore.core.network.model

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingPart(@SerialName("text") val text: String)

@Serializable
data class OnboardingHistoryEntry(
    @SerialName("role") val role: String, // "user" or "model"
    @SerialName("parts") val parts: List<OnboardingPart>
)

@Serializable
data class OnboardingNextTurnRequest(
    @SerialName("history") val history: List<OnboardingHistoryEntry>,
    @SerialName("maxTurns") val maxTurns: Int? = null,
    @SerialName("responseFormat") val responseFormat: String? = null,
    @SerialName("recommendationFormat") val recommendationFormat: String? = null,
    /** Opt-ins such as `finish_hint` (adds [GuidedOnboardingTurn.finish]); omitted when null. */
    @SerialName("features") val features: List<String>? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("languages") val languages: List<String>? = null,
    /** How the listener reacted to show deck cards; sent with next-turn, synthesize and probe-deck. */
    @SerialName("probeReactions") val probeReactions: List<OnboardingProbeReaction>? = null,
)

/** `reaction` is `listen`, `like`, `dislike` or `unknown`; the API keeps up to 12. */
@Serializable
data class OnboardingProbeReaction(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("reaction") val reaction: String,
)

/** `POST onboarding/probe-deck`: 2-4 cards, or none (never an error for the listener). */
@Serializable
data class OnboardingProbeDeckResponse(@SerialName("cards") val cards: List<OnboardingProbeCard> = emptyList())

/**
 * A well-known show Echo asks about. [id] is the Podcast Index feed ID. The preview fields point
 * at a real episode, starting about 40% in; they are all absent when no usable episode was found.
 */
@Serializable
data class OnboardingProbeCard(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String? = null,
    @SerialName("artwork") val artwork: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("hook") val hook: String = "",
    @SerialName("question") val question: String = "",
    @SerialName("whyItFits") val whyItFits: String? = null,
    @SerialName("previewUrl") val previewUrl: String? = null,
    @SerialName("previewStartSeconds") val previewStartSeconds: Int? = null,
    @SerialName("previewDurationSeconds") val previewDurationSeconds: Int? = null,
    @SerialName("previewEpisodeTitle") val previewEpisodeTitle: String? = null,
)

fun OnboardingProbeCard.toPodcast(): Podcast =
    Podcast(
        id = id.toString(),
        title = title,
        artist = author ?: "Unknown",
        imageUrl = (artwork ?: "").let { url -> if (url.startsWith("//")) "https:$url" else url },
        genre = "Podcast",
    )

@Serializable
data class OnboardingNextTurnResponse(
    @SerialName("assistantMessage") val assistantMessage: String,
    @SerialName("options") val options: List<String> = emptyList(),
    @SerialName("searchSuggestion") val searchSuggestion: String? = null,
    @SerialName("guided") val guided: GuidedOnboardingTurn? = null,
)

@Serializable
data class GuidedOnboardingTurn(
    val version: Int = 0,
    val kind: String = "",
    val response: String? = null,
    val question: String? = null,
    val choices: List<String> = emptyList(),
    val actions: List<GuidedOnboardingAction> = emptyList(),
    val intent: String = "",
    /** `hidden`, `available` or `prominent`; sent only to apps that opt into `finish_hint`. */
    val finish: String? = null,
)

@Serializable
data class GuidedOnboardingAction(val id: String)

@Serializable
data class OnboardingSynthesizeRequest(
    @SerialName("history") val history: List<OnboardingHistoryEntry>,
    @SerialName("recommendationFormat") val recommendationFormat: String? = null,
)

@Serializable
data class OnboardingQuery(
    @SerialName("rowTitle") val rowTitle: String,
    @SerialName("searchQuery") val searchQuery: String,
    @SerialName("popularSuggestions") val popularSuggestions: List<String> = emptyList()
)

@Serializable
data class OnboardingCurriculumRequest(
    @SerialName("queries") val queries: List<OnboardingQuery>,
    @SerialName("country") val country: String? = null,
    /** Chip codes; proxy expands (e.g. id→id+in) and defaults from country when omitted. */
    @SerialName("languages") val languages: List<String> = emptyList(),
    @SerialName("recommendationFormat") val recommendationFormat: String? = null,
)

@Serializable
data class OnboardingCurriculumPodcastDto(
    @SerialName("id") val id: Long,
    @SerialName("itunesId") val itunesId: Long? = null,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("artwork") val artwork: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("categories") val categories: Map<String, String>? = null,
    @SerialName("description") val description: String? = null
)

@Serializable
data class OnboardingCurriculumEpisodeDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("datePublished") val datePublished: Long? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("enclosureUrl") val enclosureUrl: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("feedImage") val feedImage: String? = null,
    @SerialName("feedId") val feedId: Long? = null,
    @SerialName("feedTitle") val feedTitle: String? = null,
    @SerialName("description") val description: String? = null
)

@Serializable
data class OnboardingCurriculumRowDto(
    @SerialName("rowTitle") val rowTitle: String,
    @SerialName("podcasts") val podcasts: List<OnboardingCurriculumPodcastDto> = emptyList(),
    @SerialName("episodes") val episodes: List<OnboardingCurriculumEpisodeDto> = emptyList()
)

// Mapping extensions
fun OnboardingCurriculumPodcastDto.toPodcast(): Podcast {
    val cleanUrl = (this.artwork ?: this.image ?: "").let { url ->
        if (url.startsWith("//")) "https:$url" else url
    }
    return Podcast(
        id = this.id.toString(),
        title = this.title,
        artist = this.author ?: "Unknown",
        imageUrl = cleanUrl,
        description = this.description,
        genre = this.categories?.values?.firstOrNull() ?: "Podcast"
    )
}

fun OnboardingCurriculumEpisodeDto.toEpisode(): Episode {
    val cleanEpImg = this.image ?: this.feedImage ?: ""
    val cleanFeedImg = this.feedImage ?: ""
    return Episode(
        id = this.id.toString(),
        title = this.title,
        description = this.description ?: "",
        audioUrl = this.enclosureUrl ?: "",
        imageUrl = if (cleanEpImg.startsWith("//")) "https:$cleanEpImg" else cleanEpImg,
        podcastImageUrl = if (cleanFeedImg.startsWith("//")) "https:$cleanFeedImg" else cleanFeedImg,
        podcastTitle = this.feedTitle,
        podcastId = this.feedId?.toString(),
        duration = this.duration ?: 0,
        publishedDate = this.datePublished ?: 0L
    )
}

@Serializable
data class OnboardingGenreSynthRequest(
    @SerialName("genres") val genres: List<String>,
    @SerialName("subGenres") val subGenres: List<String>,
    @SerialName("activity") val activity: String,
    @SerialName("length") val length: String,
    @SerialName("country") val country: String? = null,
    @SerialName("languages") val languages: List<String> = emptyList(),
    @SerialName("skipGemini") val skipGemini: Boolean = false,
    @SerialName("recommendationFormat") val recommendationFormat: String? = null,
)

@Serializable
data class OnboardingSelectedShowDto(@SerialName("title") val title: String, @SerialName("description") val description: String)

@Serializable
data class OnboardingSimilarShowsRequest(
    @SerialName("shows") val shows: List<OnboardingSelectedShowDto>,
    @SerialName("country") val country: String? = null,
    @SerialName("languages") val languages: List<String> = emptyList(),
    @SerialName("recommendationFormat") val recommendationFormat: String? = null,
)
