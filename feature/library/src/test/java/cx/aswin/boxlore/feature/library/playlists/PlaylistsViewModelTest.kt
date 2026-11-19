package cx.aswin.boxlore.feature.library.playlists

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.MainDispatcherExtension
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PlaylistsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakePlaylistRepository()
    private val completed = MutableStateFlow(setOf("b"))
    private val played = mutableListOf<Pair<String, List<String>>>()

    private fun episode(id: String) = Episode(id, "Episode $id", "", "https://cdn.example/$id.mp3")

    private fun viewModel(playlistId: String? = null) =
        PlaylistsViewModel(repository, completed, { id, episodes -> played += id to episodes.map { it.id } }, playlistId)

    @Test
    fun `the list page creates, renames and deletes`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.playlists.collect {} }
        var created: String? = null
        vm.create("Commute") { created = it.id }
        assertEquals("p1", created)
        vm.rename("p1", "Drive")
        assertEquals(listOf("Drive"), vm.playlists.first { it?.isNotEmpty() == true }!!.map { it.name })
        vm.delete("p1")
        assertTrue(repository.getPlaylists().isEmpty())
    }

    @Test
    fun `a playlist page plays from the first unplayed episode in the order on screen`() = runTest {
        repository.createPlaylist("Mix", listOf(episode("a"), episode("b"), episode("c")))
        val vm = viewModel("p1")
        backgroundScope.launch { vm.detail.collect {} }
        val ready = vm.detail.first { it is PlaylistDetailState.Ready } as PlaylistDetailState.Ready
        assertEquals(listOf("a", "b", "c"), ready.episodes.map { it.episode.id })

        vm.play()
        val (a, b, c) = ready.episodes.map { it.itemId }
        vm.play(order = listOf(c, b, a))
        vm.play(startItemId = b)

        assertEquals(
            listOf("p1" to listOf("a", "c"), "p1" to listOf("c", "a"), "p1" to listOf("b", "c")),
            played,
        )
    }

    @Test
    fun `an order saved as the page leaves is written even after the ViewModel is cleared`() = runTest {
        repository.createPlaylist("Mix", listOf(episode("a"), episode("b")))
        val vm = viewModel("p1")
        vm.viewModelScope.cancel()

        vm.reorder(listOf(2L, 1L))

        assertEquals(listOf(listOf(2L, 1L)), repository.reorders)
    }

    @Test
    fun `remove hands back the row for undo, and reorder saves the order`() = runTest {
        repository.createPlaylist("Mix", listOf(episode("a"), episode("b")))
        val vm = viewModel("p1")
        backgroundScope.launch { vm.detail.collect {} }
        val ready = vm.detail.first { it is PlaylistDetailState.Ready } as PlaylistDetailState.Ready
        var removed = emptyList<cx.aswin.boxlore.core.model.PlaylistEpisode>()

        vm.remove(ready.episodes.first()) { removed = it }
        assertEquals(listOf("b"), repository.getEpisodes("p1").map { it.episode.id })
        vm.undoRemove(removed)
        assertEquals(listOf("a", "b"), repository.getEpisodes("p1").map { it.episode.id })

        val ids = ready.episodes.map { it.itemId }
        vm.reorder(ids.reversed())
        assertEquals(listOf(ids.reversed()), repository.reorders)
    }

    @Test
    fun `a deleted playlist's page reads as missing`() = runTest {
        val vm = viewModel("gone")
        backgroundScope.launch { vm.detail.collect {} }
        assertEquals(PlaylistDetailState.Missing, vm.detail.first { it != PlaylistDetailState.Loading })
    }
}
