package cx.aswin.boxlore.wear.link

import cx.aswin.boxlore.core.wearlink.PhoneNowPlaying
import cx.aswin.boxlore.core.wearlink.WearLinkCommand
import cx.aswin.boxlore.core.wearlink.WearLinkEpisode
import cx.aswin.boxlore.wear.data.IdleWatchPlayback
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.data.WatchPlayback
import cx.aswin.boxlore.wear.model.WatchEpisode
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Starting an episode on the watch while the phone has a player of its own. */
class LinkedWatchPlaybackTest {
    @TempDir
    lateinit var folder: File

    private val minute = 60_000L
    private val audio = "https://example.org/blackshades.mp3"

    /** The episode as the watch saved it, and the same audio as the phone names it, under another ID. */
    private val mine = WatchEpisode("watch-id", "show", "Blackshades", "Darknet Diaries", artworkUrl = null, durationMinutes = 40, audioUrl = audio, durationMs = 40 * minute)
    private val phones = WearLinkEpisode("phone-id", "show", "Blackshades", audioUrl = audio, durationMs = 40 * minute)
    private val queued = WearLinkEpisode("next-id", "show", "Next one", audioUrl = "https://example.org/next.mp3", durationMs = 30 * minute)

    private class FakeLink(playing: PhoneNowPlaying?, reachable: Boolean = true) : WatchLink {
        override val phoneReachable = MutableStateFlow(reachable)
        override val phoneNowPlaying = MutableStateFlow(playing)
        val sent = mutableListOf<String>()

        override suspend fun episodes(showId: String): List<WearLinkEpisode>? = null

        override fun send(command: WearLinkCommand, onLost: () -> Unit) {
            sent += command.action
        }
    }

    /** The watch's own player: notes each episode it is told to start and the place saved for it then. */
    private class FakePlayer(private val store: WatchLibraryStore) : WatchPlayback by IdleWatchPlayback() {
        val started = mutableListOf<Pair<String, Long?>>()

        override fun play(episodeId: String) {
            started += episodeId to store.state.value.positions[episodeId]
        }
    }

    private class Setup(val playback: LinkedWatchPlayback, val player: FakePlayer, val link: FakeLink, val store: WatchLibraryStore)

    /** The watch last saved the episode at 17 minutes; [phone] is what the phone's player says. */
    private fun TestScope.setup(phone: PhoneNowPlaying?, now: Long): Setup {
        val store = WatchLibraryStore(File(folder, "library.json"), backgroundScope, StandardTestDispatcher(testScheduler))
        store.update { it.progress(mine, 17 * minute, mine.durationMs) }
        val player = FakePlayer(store)
        val link = FakeLink(phone)
        return Setup(LinkedWatchPlayback(player, link, store, backgroundScope, clock = { now }), player, link, store)
    }

    private fun onPhone(episode: WearLinkEpisode, playing: Boolean, positionMs: Long, at: Long) =
        PhoneNowPlaying(episode = episode, isPlaying = playing, positionMs = positionMs, speed = 1f, at = at, upNext = listOf(queued))

    // Regression: "Play on watch" for the episode the phone was playing started minutes behind the phone.
    @Test
    fun `starting the episode the phone is playing carries on from where the phone is now, with its queue`() =
        runTest {
            // The phone said 17 minutes when it started, four minutes ago, and has played on since.
            val setup = setup(onPhone(phones, playing = true, positionMs = 17 * minute, at = 1_000L), now = 1_000L + 4 * minute)

            setup.playback.play("watch-id")

            assertEquals(listOf("watch-id" to 21 * minute), setup.player.started)
            assertEquals(listOf(WearLinkCommand.PAUSE), setup.link.sent)
            assertEquals(listOf("next-id"), setup.store.state.value.upNext.map(WatchEpisode::id))
        }

    @Test
    fun `starting another episode while the phone plays stops the phone and starts at the watch's own place`() =
        runTest {
            val setup = setup(onPhone(queued, playing = true, positionMs = 5 * minute, at = 1_000L), now = 1_000L + 4 * minute)

            setup.playback.play("watch-id")

            assertEquals(listOf("watch-id" to 17 * minute), setup.player.started)
            assertEquals(listOf(WearLinkCommand.PAUSE), setup.link.sent)
        }

    @Test
    fun `a paused phone on the same episode leaves the watch's own place alone`() =
        runTest {
            // The watch may have played on since the phone stopped; the phone's old place must not win.
            val setup = setup(onPhone(phones, playing = false, positionMs = 3 * minute, at = 1_000L), now = 1_000L + 4 * minute)

            setup.playback.play("watch-id")

            assertEquals(listOf("watch-id" to 17 * minute), setup.player.started)
            assertEquals(emptyList<String>(), setup.link.sent)
        }
}
