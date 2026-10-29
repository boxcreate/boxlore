package cx.aswin.boxlore.feature.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingSelectedShowDto
import cx.aswin.boxlore.core.network.model.OnboardingSimilarShowsRequest
import kotlinx.coroutines.CancellationException
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
        similarShowsJob = viewModelScope.launch {
            try {
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
                        recommendationFormat = "discovery_v2",
                    )

                val response =
                    withContext(Dispatchers.IO) {
                        podcastRepository.api
                            .getSimilarShows(
                                publicKey = podcastRepository.publicKey,
                                request = request,
                            ).execute()
                    }

                // Skipped to Home meanwhile: the answer arrived too late to show.
                if (!SimilarShowsChoice.acceptsResult(_uiState.value)) return@launch
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!SimilarShowsChoice.acceptsResult(_uiState.value)) return@launch
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

/** "See similar shows" after search or import: the one AI request these paths make, once asked for. */
internal fun OnboardingViewModel.chooseSimilarShows() {
    val state = _uiState.value
    if (!state.similarChoicePending || state.isCompleting) return
    val seeds = state.selectedPodcasts.values.toList()
    _uiState.update { it.copy(similarChoicePending = false) }
    AnalyticsHelper.trackOnboardingSimilarChoice(SimilarShowsChoice.path(state), "similar", seeds.size)
    launchSimilarShowsFetch(
        seedShows = seeds,
        seedIds = seeds.map { it.id }.toSet(),
        seedCount = seeds.size,
        region = state.currentRegion,
        errorContext = "chooseSimilarShows",
    )
}

/**
 * "Go to Home" on the choice, or leaving while suggestions load: cancels any request in flight and
 * finishes like "Start without adding", so the picked or imported shows stay followed.
 */
internal fun OnboardingViewModel.goHomeFromSimilar(onDone: () -> Unit) {
    val state = _uiState.value
    if (state.isCompleting) return
    similarShowsJob?.cancel()
    similarShowsJob = null
    AnalyticsHelper.trackOnboardingSimilarChoice(
        SimilarShowsChoice.path(state),
        if (state.similarChoicePending) "home" else "skip_loading",
        state.selectedPodcasts.size,
    )
    _uiState.update {
        it.copy(similarChoicePending = false, isAiLoading = false, isSynthesizing = false, aiLoadingStage = AiLoadingStage.IDLE)
    }
    finishAiOnboarding(onDone)
}
