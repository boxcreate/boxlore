package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.HomeRecommendations
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.getHistoryForRecommendations
import cx.aswin.boxlore.core.prefs.DiscoverySections
import cx.aswin.boxlore.core.prefs.collectDiscoverySection
import cx.aswin.boxlore.feature.home.logic.buildHomeEditorialRows
import cx.aswin.boxlore.feature.home.logic.editorialRowDefinitionsFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What the region-, language- and daypart-keyed Home sections load for. */
internal data class HomeSectionRequest(
    val region: String,
    val languages: List<String>,
    val daypart: ContentDaypart,
)

/** Whether the listener keeps one switchable Home section on. */
internal fun HomeViewModel.sectionOn(pick: (DiscoverySections) -> Boolean): Flow<Boolean> =
    userPrefs.discoverySectionsStream.map(pick).distinctUntilChanged()

private fun HomeViewModel.sectionRequests(): Flow<HomeSectionRequest> =
    combine(
        userPrefs.regionStream,
        userPrefs.contentLanguagesStream,
        clockContextFlow.map { clock -> clock.daypart }.distinctUntilChanged(),
    ) { region, languages, daypart -> HomeSectionRequest(region, languages, daypart) }
        .distinctUntilChanged()

/**
 * Curated rows load apart from recommendations, so a slow personal request never leaves the
 * greeting empty. Off clears them without a request.
 */
internal fun HomeViewModel.loadEditorialRows() {
    viewModelScope.launch(Dispatchers.Default) {
        homeCacheRestoreGate.awaitRestore()
        var rowsRegion: String? = null
        collectDiscoverySection(
            keys = sectionRequests(),
            on = sectionOn { it.curatedRows },
            clear = {
                rowsRegion = null
                _editorialRows.value = emptyList()
                _isEditorialRowsLoading.value = false
            },
        ) { request ->
            // Preserve matching rows during a background reload. Replace the result only after
            // this request finishes, never at request start.
            val definitions = editorialRowDefinitionsFor(request.daypart)
            val activeProviderIds = definitions.map { it.providerId }.toSet()
            _editorialRows.value =
                if (rowsRegion != request.region) emptyList() else _editorialRows.value.filter { it.providerId in activeProviderIds }
            rowsRegion = request.region
            _isEditorialRowsLoading.value = true
            try {
                val podcastsByProvider =
                    podcastRepository.getCuratedVibes(
                        vibeIds = definitions.map { it.providerId },
                        country = request.region,
                        languages = request.languages,
                    )
                _editorialRows.value = buildHomeEditorialRows(daypart = request.daypart, podcastsByProvider = podcastsByProvider)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(
                    "HomeViewModel",
                    "Curated Home rows failed for region=${request.region} and daypart=${request.daypart}",
                    error,
                )
            } finally {
                _isEditorialRowsLoading.value = false
            }
        }
    }
}

/**
 * Picked for you loads after trending so the hero and charts are not queued behind the slower
 * personal request. Off clears it without a request, and Daily Mix then fills from your shows.
 */
internal fun HomeViewModel.loadRecommendations() {
    viewModelScope.launch(Dispatchers.Default) {
        homeCacheRestoreGate.awaitRestore()
        collectDiscoverySection(
            keys = sectionRequests(),
            on = sectionOn { it.pickedForYou },
            clear = {
                _recommendations.value = emptyList()
                _isRecommendationsLoaded.value = true
            },
        ) { request ->
            _isTrendingLoaded.first { it }
            _isRecommendationsLoaded.value = false
            try {
                android.util.Log.d("BoxCastTiming", "VM: Background personalized Home screen load for region=${request.region}")
                val recommendations = fetchHomeRecommendations(request)
                applyHomeRecommendations(recommendations, _recommendations, _isRecommendationsFallback) { picks, isFallback ->
                    try {
                        val json = Json { ignoreUnknownKeys = true }
                        boxcastPrefs.saveRecommendationsCache(json.encodeToString(picks), isFallback)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (ce: Exception) {
                        android.util.Log.e("HomeViewModel", "Failed to cache recommendations", ce)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BoxCastTiming", "VM: Recommendations load failed", e)
            } finally {
                _isRecommendationsLoaded.value = true
            }
        }
    }
}

/**
 * Shows [result] in Picked for you ([picks], [isFallback]) and saves it with [save], one entry per
 * episode ID and title. A failed request with no last good picks ([HomeRecommendations.failed]) keeps
 * the picks Home shows (restored from its cache or loaded earlier) and saves nothing. Returns whether
 * the picks were replaced.
 */
internal suspend fun applyHomeRecommendations(
    result: HomeRecommendations,
    picks: MutableStateFlow<List<Episode>>,
    isFallback: MutableStateFlow<Boolean>,
    save: suspend (List<Episode>, Boolean) -> Unit,
): Boolean {
    if (result.failed) return false
    val distinct =
        result.items
            .distinctBy { it.id }
            .distinctBy { it.title.lowercase().trim() }
    picks.value = distinct
    isFallback.value = result.isFallback
    save(distinct, result.isFallback)
    return true
}

private suspend fun HomeViewModel.fetchHomeRecommendations(request: HomeSectionRequest) = coroutineScope {
    val interests = boxcastPrefs.getUserGenres().toList()
    val history = async { playbackRepository.getHistoryForRecommendations(15) }
    val subscribedIds = async { subscriptionRepository.subscribedPodcastIds.first().toList() }
    val subscribedPodcasts = async { subscriptionRepository.subscribedPodcasts.first() }
    podcastRepository.getHomeRecommendations(
        country = request.region,
        history = history.await(),
        interests = interests,
        subscribedPodcastIds = subscribedIds.await(),
        subscribedGenres = subscribedPodcasts.await().mapNotNull { it.recommendationGenre }.distinct(),
        languages = request.languages,
    )
}
