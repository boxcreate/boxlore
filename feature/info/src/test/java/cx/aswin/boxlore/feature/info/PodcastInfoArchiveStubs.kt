package cx.aswin.boxlore.feature.info

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mockito.Mockito.`when`

/** Stubs a mocked show page model's archive reads: [archived] IDs and the show's [count] of them. */
internal fun stubArchive(
    model: PodcastInfoViewModel,
    archived: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
    count: StateFlow<Int> = MutableStateFlow(archived.value.size),
) {
    `when`(model.archivedEpisodesState).thenReturn(archived)
    `when`(model.archivedEpisodeCount).thenReturn(count)
}
