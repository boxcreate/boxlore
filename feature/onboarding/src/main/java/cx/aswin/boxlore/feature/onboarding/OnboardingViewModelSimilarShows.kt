package cx.aswin.boxlore.feature.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingSelectedShowDto
import cx.aswin.boxlore.core.network.model.OnboardingSimilarShowsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared search/OPML similar-shows fetch: clear payloads, call API, filter seed IDs, no auto-select.
 */
internal fun OnboardingViewModel.launchSimilarShowsFetch(
    seedShows: List<Podcast>,
    seedIds: Set<String>,
    seedCount: Int,
    region: String,
    errorContext: String,
    beforeApi: suspend () -> Unit = {},
) {
    val finalAction: () -> Unit = {
        _uiState.update {
            it.copy(
                isAiLoading = true,
                isSynthesizing = true,
                aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                onboardingError = null,
                aiCurriculumRows = emptyList(),
                genreChartsPodcasts = emptyList(),
                suggestionSeedCount = seedCount,
            )
        }
        viewModelScope.launch {
            try {
                beforeApi()
                val locale = discoveryLocaleForRegion(region)
                val request =
                    OnboardingSimilarShowsRequest(
                        shows =
                        seedShows.distinctBy { it.title.lowercase().trim() }.take(20).map {
                            OnboardingSelectedShowDto(
                                title = it.title,
                                description = it.description ?: "",
                            )
                        },
                        country = locale.country,
                        languages = locale.languages,
                    )

                val response =
                    withContext(Dispatchers.IO) {
                        podcastRepository.api
                            .getSimilarShows(
                                publicKey = podcastRepository.publicKey,
                                request = request,
                            ).execute()
                    }

                if (response.isSuccessful && response.body() != null) {
                    val rows =
                        OnboardingSuggestionsPresentation.filterCurriculumRowsExcluding(
                            rows =
                            response.body()!!.map {
                                it.copy(episodes = emptyList())
                            },
                            excludeIds = seedIds,
                        )
                    _uiState.update { state ->
                        state.copy(
                            aiCurriculumRows = rows,
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            onboardingError = null,
                        )
                    }
                } else {
                    throw Exception("Failed to load similar shows from backend: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in $errorContext", e)
                _uiState.update { state ->
                    state.copy(
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiLoadingStage = AiLoadingStage.IDLE,
                        onboardingError =
                        "We encountered a temporary issue generating recommendations. Let's try again.",
                    )
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
}
