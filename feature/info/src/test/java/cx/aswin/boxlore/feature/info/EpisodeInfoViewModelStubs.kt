package cx.aswin.boxlore.feature.info

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`

/**
 * Stubs every flow a mocked episode page model hands [EpisodeInfoScreen], so a new read added to the
 * page is stubbed in one place instead of failing each fixture with a null flow.
 */
internal fun stubEpisodeInfoViewModel(
    model: EpisodeInfoViewModel,
    uiState: StateFlow<EpisodeInfoUiState>,
    moreLikeThisOn: Boolean? = true,
    archived: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
    completed: Set<String> = emptySet(),
) {
    `when`(model.uiState).thenReturn(uiState)
    `when`(model.moreLikeThisOn).thenReturn(MutableStateFlow(moreLikeThisOn))
    `when`(model.likedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
    `when`(model.completedEpisodeIds).thenReturn(MutableStateFlow(completed))
    `when`(model.archivedEpisodeIds).thenReturn(archived)
    `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
    `when`(model.isDownloaded(anyString())).thenReturn(flowOf(false))
    `when`(model.isDownloading(anyString())).thenReturn(flowOf(false))
    `when`(model.showRemoveDownloadDialog).thenReturn(MutableStateFlow(false))
}
