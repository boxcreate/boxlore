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
    val isSearching: Boolean = false,
    val isCompleting: Boolean = false,
    val currentRegion: String = "us",
    val initialRegion: String = "us",
    val selectedPodcasts: Map<String, Podcast> = emptyMap(),
    // AI Onboarding fields
    val aiHistory: List<OnboardingHistoryEntry> = emptyList(),
    val aiAssistantMessage: String = "Hi! I'm boxlore. To start, what kind of a listener are you?",
    val aiOptions: List<String> =
        listOf(
            "Storyseeker | Serialized narratives, investigative series, and immersive audio dramas",
            "Deep Diver | Detailed analysis, long-form research, and intellectual essays",
            "Conversationalist | Candid guest interviews, unscripted discussions, and casual banter",
            "Chill Listener | Soothing voices, mindfulness sessions, and relaxing background talk",
        ),
    val aiCurrentTurn: Int = 1,
    val aiCustomInputText: String = "",
    val aiSearchSuggestion: String? = null,
    val aiSelectedOptions: Set<String> = emptySet(),
    val isAiLoading: Boolean = false,
    val isSynthesizing: Boolean = false,
    val aiCurriculumRows: List<OnboardingCurriculumRowDto> = emptyList(),
    val aiLoadingStage: AiLoadingStage = AiLoadingStage.IDLE,
    val onboardingError: String? = null,
    val reachedSuggestionsViaAiFlow: Boolean = false,
    val reachedSuggestionsViaSearchFlow: Boolean = false,
    val reachedSuggestionsViaOpmlFlow: Boolean = false,
    val hasSentCustomInput: Boolean = false,
    val popularPodcasts: List<Podcast> = emptyList(),
    val isPopularLoading: Boolean = false,
    val selectedSearchGenre: String? = null,
)

enum class OnboardingStep {
    WELCOME,
    GENRES,
    SUB_GENRES,
    ACTIVITY_PICKER,
    LENGTH_PICKER,
    SEARCH,
    AI_ONBOARDING,
    AI_SUGGESTIONS,
}
