package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry

enum class AiLoadingStage {
    IDLE,
    GENERATING_RESPONSE,
    SYNTHESIZING_PREFERENCES,
    FETCHING_CATALOGS,
    ASSEMBLING_FEED,
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val selectedGenres: Set<String> = emptySet(),
    val selectedSubGenres: Set<String> = emptySet(),
    val listeningActivities: Set<String> = emptySet(),
    val preferredLengths: Set<String> = emptySet(),
    val activityGenreMap: Map<String, Set<String>> = emptyMap(),
    val lengthGenreMap: Map<String, Set<String>> = emptyMap(),
    val genreChartsPodcasts: List<Podcast> = emptyList(),
    val subscribedPodcastIds: Set<String> = emptySet(),
    val isLoadingPodcasts: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Podcast> = emptyList(),
    /** Hybrid `/search` hits not already in Meili typeahead (coverage for episode-count shrink). */
    val alsoFoundResults: List<Podcast> = emptyList(),
    val isSearching: Boolean = false,
    /** The show search couldn't be read: no results means Try again, not no matches. */
    val searchFailed: Boolean = false,
    val isCompleting: Boolean = false,
    val currentRegion: String = "us",
    val initialRegion: String = "us",
    val selectedPodcasts: Map<String, Podcast> = emptyMap(),
    // AI Onboarding fields
    val aiHistory: List<OnboardingHistoryEntry> = emptyList(),
    val aiGuidedTurn: cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn? = null,
    val aiAssistantMessage: String = "Hi, I’m Echo. Where does your mind like to wander?",
    val aiOptions: List<String> = EchoInitialTopic.entries.map { it.answer },
    val aiCurrentTurn: Int = 1,
    val aiCustomInputText: String = "",
    val aiSearchSuggestion: String? = null,
    val aiSelectedOptions: Set<String> = emptySet(),
    val isAiLoading: Boolean = false,
    val isSynthesizing: Boolean = false,
    val aiCurriculumRows: List<OnboardingCurriculumRowDto> = emptyList(),
    /**
     * Shows picked during Echo's chat (a positive taste-test answer or a search side trip), as
     * they stood when the feed was built; suggestions lists them first, selected.
     */
    val aiPicks: List<Podcast> = emptyList(),
    /** Echo's show deck: requested after the second answer, shown before the next one goes out. */
    val aiDeck: EchoDeck = EchoDeck(),
    val aiLoadingStage: AiLoadingStage = AiLoadingStage.IDLE,
    val onboardingError: String? = null,
    /** Why [onboardingError] happened, for the copy that names the cause. */
    val onboardingErrorKind: OnboardingErrorKind = OnboardingErrorKind.Network,
    val reachedSuggestionsViaAiFlow: Boolean = false,
    val reachedSuggestionsViaSearchFlow: Boolean = false,
    val reachedSuggestionsViaOpmlFlow: Boolean = false,
    /**
     * Search and import stop on a choice before any AI request: see similar shows, or go to Home
     * with the picked or imported shows followed. Cleared by either choice and by Back.
     */
    val similarChoicePending: Boolean = false,
    /** Count of search/OPML seed shows used for the suggestions loader copy (stable during fetch). */
    val suggestionSeedCount: Int = 0,
    val hasSentCustomInput: Boolean = false,
    val popularPodcasts: List<Podcast> = emptyList(),
    val isPopularLoading: Boolean = false,
    val selectedSearchGenre: String? = null,
    /** Onboarding's first question, while [currentStep] is [OnboardingStep.APP_LANGUAGE]. */
    val appLanguageQuestion: AppLanguageQuestion? = null,
    /** From a language pick until Welcome has taken over the question's panel. */
    val openingHandoff: OpeningHandoff? = null,
)

enum class OnboardingStep {
    /** The language question, before Welcome, only when the phone suggests a translation. */
    APP_LANGUAGE,
    WELCOME,
    GENRES,
    SUB_GENRES,
    ACTIVITY_PICKER,
    LENGTH_PICKER,
    SEARCH,
    AI_ONBOARDING,
    AI_SUGGESTIONS,
}

/** What a failed AI request on the onboarding path ran into. */
enum class OnboardingErrorKind {
    /** The connection or the server; trying again can help. */
    Network,

    /** The API asked for the human check and it was skipped or failed, so it stays unavailable for a while. */
    VerificationSkipped,
}
