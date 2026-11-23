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
    liked: Set<String> = emptySet(),
    completed: Set<String> = emptySet(),
    archived: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
    queued: Set<String> = emptySet(),
    downloaded: Boolean = false,
    downloading: Boolean = false,
) {
    `when`(model.uiState).thenReturn(uiState)
    `when`(model.moreLikeThisOn).thenReturn(MutableStateFlow(moreLikeThisOn))
    `when`(model.likedEpisodeIds).thenReturn(MutableStateFlow(liked))
    `when`(model.completedEpisodeIds).thenReturn(MutableStateFlow(completed))
    `when`(model.archivedEpisodeIds).thenReturn(archived)
    `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(queued))
    `when`(model.isDownloaded(anyString())).thenReturn(flowOf(downloaded))
    `when`(model.isDownloading(anyString())).thenReturn(flowOf(downloading))
    `when`(model.showRemoveDownloadDialog).thenReturn(MutableStateFlow(false))
}
