package cx.aswin.boxlore.feature.library.playlists

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.UpNextSnapshot
import cx.aswin.boxlore.core.testing.MainDispatcherExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PlaylistsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakePlaylistRepository()
    private val completed = MutableStateFlow(emptySet<String>())
    private val played = mutableListOf<Pair<String, List<String>>>()
    private val restored = mutableListOf<UpNextSnapshot>()
    private var queue: UpNextSnapshot? = null
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var stopRecording: () -> Unit

    @BeforeEach fun record() {
        stopRecording = AnalyticsHelper.installRecordingSink(events)
    }

    @AfterEach fun stop() = stopRecording()

    private fun episode(id: String) = Episode(id, "Episode $id", "", "https://cdn.example/$id.mp3")

    private fun viewModel(playlistId: String? = null, queueLimit: Int = 50) = PlaylistsViewModel(
        repository,
        PlaylistPlaybackPorts(
            followedShows = flowOf(listOf(Podcast("a", "A", "", ""))),
            folders = flowOf(emptyList()),
            completedEpisodeIds = completed,
            startPlayback = { id, episodes -> played += id to episodes.map { it.id } },
            upNextSnapshot = { queue },
            restoreUpNext = { restored += it },
        ),
        playlistId,
        queueLimit = queueLimit,
    )

    private fun actions() = events.filter { it.first == "playlist_modified" }.map { it.second["action"] }

    @Test
    fun `the list page creates with shows, renames and deletes`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.playlists.collect {} }
        var created: String? = null
        vm.create("Comedy", listOf("a", "b"), PlaylistMix.NEWEST_FIRST, perShowLimit = null, iconKey = "comedy", origin = "genre") { created = it.id }
        assertEquals("p1", created)
        val stored = repository.getPlaylists().single()
        assertEquals(PlaylistMix.NEWEST_FIRST to null, stored.mix to stored.perShowLimit)
        assertEquals("comedy", stored.iconKey)
        vm.rename("p1", "Laughs")
        val list = vm.playlists.first { it?.isNotEmpty() == true }!!
        assertEquals(listOf("Laughs" to 2), list.map { it.name to it.showCount })
        vm.delete("p1")
        assertTrue(repository.getPlaylists().isEmpty())
        assertEquals(listOf("create", "delete"), actions())
    }

    @Test
    fun `a playlist page shows its shows and what plays next, and saves how it plays`() = runTest {
        repository.createPlaylist("Mix", listOf("a", "b"))
        repository.next = (1..15).map { episode("$it") }
        val vm = viewModel("p1")
        backgroundScope.launch { vm.detail.collect {} }
        val ready = vm.detail.first { it is PlaylistDetailState.Ready } as PlaylistDetailState.Ready
        assertEquals(listOf("a", "b"), ready.shows.map { it.podcastId })
        assertEquals(PlaylistsViewModel.COMING_UP, ready.comingUp.size)

        vm.setPlayback(PlaylistMix.ONE_SHOW_AT_A_TIME, 2)
        val binge = vm.detail.first { (it as? PlaylistDetailState.Ready)?.playlist?.mix == PlaylistMix.ONE_SHOW_AT_A_TIME } as PlaylistDetailState.Ready
        assertEquals(2, binge.playlist.perShowLimit)
    }

    @Test
    fun `play queues the first batch, never past the queue limit, and offers Undo of what was queued`() = runTest {
        repository.createPlaylist("Mix", listOf("a"))
        repository.next = (1..30).map { episode("$it") }
        val previous = UpNextSnapshot(listOf(episode("old")), 0, 5L, true, Podcast("s", "S", "", ""))
        queue = previous
        val vm = viewModel("p1")
        var replaced: UpNextSnapshot? = null

        vm.play(onReplaced = { replaced = it })

        assertEquals(listOf("p1" to (1..20).map { "$it" }), played)
        assertEquals(previous, replaced)
        vm.undoPlay(previous)
        assertEquals(listOf(previous), restored)

        viewModel("p1", queueLimit = 5).play()
        assertEquals(5, played.last().second.size)
        assertEquals(listOf("play", "play"), actions())
    }

    @Test
    fun `play with nothing queued offers no Undo, and an empty playlist plays nothing`() = runTest {
        repository.createPlaylist("Mix", listOf("a"))
        repository.next = listOf(episode("1"))
        var replaced: UpNextSnapshot? = null
        viewModel("p1").play(onReplaced = { replaced = it })
        assertNull(replaced)
        assertEquals(1, played.size)

        repository.next = emptyList()
        var nothing = false
        viewModel("p1").play(onNothingToPlay = { nothing = true })
        assertTrue(nothing)
        assertEquals(1, played.size)
    }

    @Test
    fun `shows are added once and removed with Undo`() = runTest {
        repository.createPlaylist("Mix", listOf("a"))
        val vm = viewModel("p1")
        backgroundScope.launch { vm.detail.collect {} }
        vm.addShows(listOf("a", "b"))
        val ready = vm.detail.first { (it as? PlaylistDetailState.Ready)?.shows?.size == 2 } as PlaylistDetailState.Ready

        var removed: cx.aswin.boxlore.core.model.PlaylistShow? = null
        vm.removeShow(ready.shows.first()) { removed = it }
        assertEquals(listOf("b"), repository.getShowIds()["p1"])
        vm.undoRemove(removed!!)
        assertEquals(setOf("a", "b"), repository.getShowIds()["p1"]!!.toSet())
        assertEquals(listOf("add_show", "remove_show"), actions())
    }

    @Test
    fun `coming up is worked out again when the page comes back`() = runTest {
        repository.createPlaylist("Mix", listOf("a"))
        repository.next = listOf(episode("1"))
        val vm = viewModel("p1")
        backgroundScope.launch { vm.detail.collect {} }
        vm.detail.first { (it as? PlaylistDetailState.Ready)?.comingUp?.map(Episode::id) == listOf("1") }

        // A new episode was stored while the page was away; nothing the page observes changed.
        repository.next = listOf(episode("new"), episode("1"))
        vm.refreshComingUp()

        val fresh = vm.detail.first { (it as? PlaylistDetailState.Ready)?.comingUp?.size == 2 } as PlaylistDetailState.Ready
        assertEquals(listOf("new", "1"), fresh.comingUp.map { it.id })
    }

    @Test
    fun `only the newest Play acts when an earlier playlist's read finishes after it`() = runTest {
        repository.createPlaylist("Slow", listOf("a"))
        repository.createPlaylist("Fast", listOf("a"))
        repository.next = listOf(episode("1"))
        queue = UpNextSnapshot(listOf(episode("old")), 0, 5L, true, Podcast("s", "S", "", ""))
        val slowRead = CompletableDeferred<Unit>()
        repository.readGates["p1"] = slowRead
        val vm = viewModel()
        val replacedFor = mutableListOf<String>()
        var nothingToPlay = 0

        vm.play("p1", onReplaced = { replacedFor += "p1" }, onNothingToPlay = { nothingToPlay++ })
        vm.play("p2", onReplaced = { replacedFor += "p2" }, onNothingToPlay = { nothingToPlay++ })
        slowRead.complete(Unit)

        assertEquals(listOf("p2"), played.map { it.first })
        assertEquals(listOf("p2"), replacedFor)
        assertEquals(0, nothingToPlay)
    }

    @Test
    fun `how it plays is saved in the order it was changed, even when a save is slow`() = runTest {
        repository.createPlaylist("Mix", listOf("a"))
        val slowSave = CompletableDeferred<Unit>()
        repository.playbackGates += slowSave
        val vm = viewModel("p1")

        vm.setPlayback(PlaylistMix.NEWEST_FIRST, 3)
        vm.setPlayback(PlaylistMix.NEWEST_FIRST, 2)
        slowSave.complete(Unit)

        val stored = repository.getPlaylists().single()
        assertEquals(PlaylistMix.NEWEST_FIRST to 2, stored.mix to stored.perShowLimit)
    }
}
