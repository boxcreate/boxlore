package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.prefs.collectDiscoverySection
import cx.aswin.boxlore.feature.info.EpisodeInfoUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Loads [episodeId]'s Try these episodes (More like this) while the listener keeps it on. Off clears
 * it with no request, and switching it off cancels a request in flight. Results and loading changes
 * only land while [episodeId] is the page shown, so an older page's reply cannot show on a newer one.
 */
internal suspend fun collectSimilarEpisodes(
    episodeId: String,
    on: Flow<Boolean>,
    state: MutableStateFlow<EpisodeInfoUiState>,
    fetch: suspend () -> List<Episode>,
    onError: (Exception) -> Unit = {},
) {
    fun update(change: (EpisodeInfoUiState.Success) -> EpisodeInfoUiState.Success) {
        val current = state.value as? EpisodeInfoUiState.Success ?: return
        if (current.episode.id == episodeId) state.value = change(current)
    }
    collectDiscoverySection(
        keys = flowOf(episodeId),
        on = on,
        clear = { update { it.copy(similarEpisodes = emptyList(), similarEpisodesLoading = false) } },
    ) {
        update { it.copy(similarEpisodesLoading = true) }
        try {
            val episodes = fetch()
            update { it.copy(similarEpisodes = episodes, similarEpisodesLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
            update { it.copy(similarEpisodesLoading = false) }
        }
    }
}
