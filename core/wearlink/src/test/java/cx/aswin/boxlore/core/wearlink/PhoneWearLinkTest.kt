package cx.aswin.boxlore.core.wearlink

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneWearLinkTest {
    private val transport = RecordingWearLinkTransport()
    private val store = PhoneLinkStore.InMemory()
    private val library = FakeLibrary()
    private val player = FakePlayer()
    private var now = 1_000L

    private class FakeLibrary : PhoneLibraryPort {
        override val showsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        var shows = listOf(WearLinkShow("1", "Show 1"))
        var history = listOf(row("phone-row", at = 50L))
        val applied = ArrayList<Pair<List<UserSubscriptionSyncDto>, List<ListeningHistorySyncDto>>>()
        var failApply = false
        var saved = mapOf("1" to listOf(WearLinkEpisode("e1", "1", "Episode 1")))

        var newEpisodes = emptyList<WearLinkEpisode>()
        var dailyMix = emptyList<WearLinkEpisode>()

        override suspend fun library(historyLimit: Int) = PhoneLibraryRows(shows, history.take(historyLimit), newEpisodes, dailyMix)

        override suspend fun apply(subscriptions: List<UserSubscriptionSyncDto>, history: List<ListeningHistorySyncDto>) {
            check(!failApply) { "database is busy" }
            applied += subscriptions to history
        }

        override suspend fun episodes(podcastId: String, limit: Int) = saved[podcastId].orEmpty().take(limit)
    }

    private class FakePlayer : PhonePlayerPort {
        override val state = MutableStateFlow(PhonePlayerState())
        val calls = ArrayList<String>()
        var failing = false

        override suspend fun togglePlayPause() {
            check(!failing) { "MediaController method is called from a wrong thread" }
            calls += "toggle"
        }

        override suspend fun pause() {
            calls += "pause"
        }

        override suspend fun seekBack() {
            calls += "back"
        }

        override suspend fun seekForward() {
            calls += "forward"
        }

        override suspend fun setSpeed(speed: Float) {
            calls += "speed $speed"
        }

        override suspend fun play(episode: WearLinkEpisode, positionMs: Long, upNext: List<WearLinkEpisode>) {
            check(!failing) { "MediaController method is called from a wrong thread" }
            val then = if (upNext.isEmpty()) "" else " then ${upNext.joinToString(",") { it.episodeId }}"
            calls += "play ${episode.episodeId} at $positionMs$then"
        }
    }

    /** Lets the link's own waiting pass: work in the background scope only moves when time does. */
    private fun TestScope.settle() {
        advanceTimeBy(PhoneWearLink.SETTLE_MS * 3)
        runCurrent()
    }

    private fun TestScope.link() = PhoneWearLink(transport, library, player, store, backgroundScope) { now }

    private fun changes(revision: Long, vararg history: Pair<Long, String>, follows: List<Pair<Long, String>> = emptyList(), watchId: String = "w") =
        WatchChanges(
            watchId = watchId,
            revision = revision,
            subscriptions = follows.map { (rev, id) -> WatchSubscriptionChange(rev, UserSubscriptionSyncDto(id, subscribedAt = 5L, updatedAt = 5L)) },
            history = history.map { (rev, id) -> WatchHistoryChange(rev, row(id, at = 900L)) },
            storedBytes = 77L,
            downloads = 2,
        )

    private suspend fun PhoneWearLink.hears(changes: WatchChanges) =
        onItem(LinkItem("node", WearLinkProtocol.PATH_WATCH_CHANGES, WearLinkCodec.encode(changes)))

    private fun publishedLibrary() = WearLinkCodec.decode<PhoneLibrary>(transport.published[WearLinkProtocol.PATH_PHONE_LIBRARY])

    @Test
    fun `a phone that has never met a watch does nothing at all`() = runTest {
        val link = link()

        link.start()
        library.showsChanged.tryEmit(Unit)
        player.state.value = PhonePlayerState(WearLinkEpisode("e", "1", "E"), isPlaying = true)
        settle()

        assertTrue(transport.published.isEmpty())
        assertFalse(link.status.value.watchKnown)
    }

    @Test
    fun `the watch's first word wakes the link, which answers with the library and the settings`() = runTest {
        val link = link()

        link.hears(changes(revision = 0L))
        settle()

        assertTrue(store.watchKnown)
        assertEquals(WearLinkStatus(watchKnown = true, reachable = true, watch = WatchStatus(77L, 2, seenAt = 1_000L)), link.status.value)
        val sent = publishedLibrary()!!
        assertEquals(listOf("1"), sent.shows.map { it.podcastId })
        assertEquals(listOf("phone-row"), sent.history.map { it.episodeId })
        assertEquals(WearLinkSettings(), WearLinkCodec.decode<WearLinkSettings>(transport.published[WearLinkProtocol.PATH_PHONE_SETTINGS]))
    }

    @Test
    fun `changes made on the watch are merged once and confirmed in the answer`() = runTest {
        val link = link()

        link.hears(changes(3L, 2L to "a", 3L to "b", follows = listOf(1L to "9")))
        settle()

        val (subscriptions, history) = library.applied.single()
        assertEquals(listOf("9"), subscriptions.map { it.podcastId })
        assertEquals(listOf("a", "b"), history.map { it.episodeId })
        assertEquals(mapOf("w" to 3L), publishedLibrary()!!.acked)

        link.hears(changes(3L, 2L to "a", 3L to "b", follows = listOf(1L to "9")))
        link.hears(changes(4L, 3L to "b", 4L to "c"))
        settle()

        assertEquals(2, library.applied.size)
        assertEquals(listOf("c"), library.applied.last().second.map { it.episodeId })
        assertEquals(mapOf("w" to 4L), publishedLibrary()!!.acked)
    }

    @Test
    fun `a merge that fails is not confirmed, so the watch sends it again`() = runTest {
        val link = link()
        library.failApply = true

        link.hears(changes(2L, 2L to "a"))
        settle()
        assertNull(store.acked()["w"])

        library.failApply = false
        link.hears(changes(2L, 2L to "a"))
        settle()
        assertEquals(listOf("a"), library.applied.single().second.map { it.episodeId })
        assertEquals(2L, store.acked()["w"])
    }

    @Test
    fun `a watch that started over, or a second watch, is merged from its own beginning`() = runTest {
        val link = link()
        link.hears(changes(9L, 9L to "a"))
        settle()

        link.hears(changes(1L, 1L to "fresh"))
        link.hears(changes(1L, 1L to "other", watchId = "second"))
        settle()

        assertEquals(listOf("a", "fresh", "other"), library.applied.flatMap { it.second }.map { it.episodeId })
        assertEquals(mapOf("w" to 1L, "second" to 1L), store.acked())
    }

    @Test
    fun `New episodes and the Daily Mix travel with the library`() = runTest {
        val link = link()
        library.newEpisodes = listOf(WearLinkEpisode("n1", "1", "New one"))
        library.dailyMix = listOf(WearLinkEpisode("m1", "1", "Mix one"), WearLinkEpisode("m2", "1", "Mix two"))

        link.hears(changes(0L))
        settle()

        assertEquals(listOf("n1"), publishedLibrary()!!.newEpisodes.map { it.episodeId })
        assertEquals(listOf("m1", "m2"), publishedLibrary()!!.dailyMix.map { it.episodeId })
    }

    @Test
    fun `followed shows changing sends the library again`() = runTest {
        val link = link()
        link.hears(changes(0L))
        settle()

        library.shows = listOf(WearLinkShow("1", "Show 1"), WearLinkShow("2", "Show 2"))
        library.showsChanged.emit(Unit)
        settle()

        assertEquals(listOf("1", "2"), publishedLibrary()!!.shows.map { it.podcastId })
    }

    @Test
    fun `the player is reported when it starts, stops or jumps, never while it simply plays`() = runTest {
        val link = link()
        val episode = WearLinkEpisode("e", "1", "E", durationMs = 3_600_000L)
        link.hears(changes(0L))
        settle()
        fun reported() = WearLinkCodec.decode<PhoneNowPlaying>(transport.published[WearLinkProtocol.PATH_PHONE_NOW_PLAYING])

        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 10_000L)
        settle()
        assertEquals(PhoneNowPlaying(episode, isPlaying = true, positionMs = 10_000L, speed = 1f, at = 1_000L), reported())

        now = 31_000L
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 40_500L)
        settle()
        assertEquals(1_000L, reported()!!.at)

        now = 32_000L
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 600_000L)
        settle()
        assertEquals(600_000L, reported()!!.positionMs)

        now = 40_000L
        player.state.value = PhonePlayerState(episode, isPlaying = false, positionMs = 608_000L)
        settle()
        assertFalse(reported()!!.isPlaying)
        assertEquals(40_000L, reported()!!.at)
    }

    @Test
    fun `an episode's length learned after it started is sent on`() = runTest {
        val link = link()
        val episode = WearLinkEpisode("e", "1", "E", durationMs = 0L)
        link.hears(changes(0L))
        settle()
        fun reported() = WearLinkCodec.decode<PhoneNowPlaying>(transport.published[WearLinkProtocol.PATH_PHONE_NOW_PLAYING])

        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 10_000L)
        settle()
        assertEquals(0L, reported()!!.episode?.durationMs)

        now = 3_000L
        player.state.value = PhonePlayerState(episode.copy(durationMs = 6_240_000L), isPlaying = true, positionMs = 12_000L)
        settle()
        assertEquals(6_240_000L, reported()!!.episode?.durationMs)
        assertEquals(12_000L, reported()!!.positionMs)
    }

    @Test
    fun `the phone's queue travels with its player, is sent again when it changes, and is cut to a short list`() = runTest {
        val link = link()
        val episode = WearLinkEpisode("e", "1", "E", durationMs = 3_600_000L)
        fun queued(count: Int) = (1..count).map { WearLinkEpisode("q$it", "1", "Q$it") }
        link.hears(changes(0L))
        settle()
        fun reported() = WearLinkCodec.decode<PhoneNowPlaying>(transport.published[WearLinkProtocol.PATH_PHONE_NOW_PLAYING])

        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 10_000L, upNext = queued(2))
        settle()
        assertEquals(listOf("q1", "q2"), reported()!!.upNext.map { it.episodeId })

        // Steady playback, but the listener removed one from the queue on the phone.
        now = 4_000L
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 13_000L, upNext = queued(1))
        settle()
        assertEquals(listOf("q1"), reported()!!.upNext.map { it.episodeId })

        now = 5_000L
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 14_000L, upNext = queued(50))
        settle()
        assertEquals(PhoneNowPlaying.UP_NEXT_LIMIT, reported()!!.upNext.size)
    }

    private fun watchSays(playing: Boolean) =
        LinkItem("watch", WearLinkProtocol.PATH_WATCH_NOW_PLAYING, WearLinkCodec.encode(WatchNowPlaying(isPlaying = playing, episodeId = "w", at = 1L)))

    private fun sentToWatch() = transport.sent.filter { it.first == WearLinkProtocol.PATH_WATCH_COMMAND }.map { WearLinkCodec.decode<WearLinkCommand>(it.second)!! }

    @Test
    fun `starting the phone while the watch plays pauses the watch and offers it back`() = runTest {
        val link = link()
        val episode = WearLinkEpisode("e", "1", "E", durationMs = 3_600_000L)
        link.hears(changes(0L))
        settle()
        link.onItem(watchSays(playing = true))
        now = 60_000L

        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 10_000L)
        settle()

        assertEquals(listOf(WearLinkCommand.PAUSE), sentToWatch().map { it.action })
        assertTrue(link.playOnWatchOffer.value)

        link.dismissPlayOnWatchOffer()
        assertFalse(link.playOnWatchOffer.value)
    }

    @Test
    fun `a phone start with the watch idle, or one the watch asked for, is no take-over`() = runTest {
        val link = link()
        val episode = WearLinkEpisode("e", "1", "E", durationMs = 3_600_000L)
        link.hears(changes(0L))
        settle()
        now = 60_000L

        link.onItem(watchSays(playing = false))
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 10_000L)
        settle()
        assertFalse(link.playOnWatchOffer.value)

        // "Play on phone" on the watch: its own "still playing" note may arrive after the phone started.
        player.state.value = PhonePlayerState(episode, isPlaying = false, positionMs = 12_000L)
        settle()
        link.onItem(watchSays(playing = true))
        link.onCommand(WearLinkCodec.encode(WearLinkCommand(WearLinkCommand.PLAY_EPISODE, positionMs = 5L, episode = episode)))
        now = 62_000L
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 5L)
        settle()

        assertFalse(link.playOnWatchOffer.value)
        assertTrue(sentToWatch().isEmpty())
    }

    @Test
    fun `Play on watch hands over the episode, its place and the queue, then stops the phone`() = runTest {
        val link = link()
        val episode = WearLinkEpisode("e", "1", "E", durationMs = 3_600_000L)
        link.hears(changes(0L))
        settle()
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 10_000L, upNext = listOf(WearLinkEpisode("q", "1", "Q")))
        settle()
        // Twenty seconds on, with nothing reported since: the hand-over asks the player where it is now.
        now += 20_000L
        player.state.value = PhonePlayerState(episode, isPlaying = true, positionMs = 30_000L, upNext = listOf(WearLinkEpisode("q", "1", "Q")))

        link.playOnWatch()
        settle()

        val command = sentToWatch().single()
        assertEquals(WearLinkCommand.PLAY_EPISODE, command.action)
        assertEquals("e", command.episode?.episodeId)
        assertEquals(30_000L, command.positionMs)
        assertEquals(listOf("q"), command.upNext.map { it.episodeId })
        assertEquals(listOf("pause"), player.calls)
    }

    @Test
    fun `a watch out of reach leaves the phone playing`() = runTest {
        val link = link()
        link.hears(changes(0L))
        settle()
        player.state.value = PhonePlayerState(WearLinkEpisode("e", "1", "E"), isPlaying = true, positionMs = 10_000L)
        settle()
        transport.reachable.value = false

        link.playOnWatch()
        settle()

        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun `playback moved to the phone brings the watch's queue along`() = runTest {
        val link = link()
        val command =
            WearLinkCommand(
                WearLinkCommand.PLAY_EPISODE,
                positionMs = 42L,
                episode = WearLinkEpisode("e", "1", "E"),
                upNext = listOf(WearLinkEpisode("a", "1", "A"), WearLinkEpisode("b", "2", "B")),
            )

        link.onCommand(WearLinkCodec.encode(command))

        assertEquals(listOf("play e at 42 then a,b"), player.calls)
    }

    @Test
    fun `watch commands reach the phone's player`() = runTest {
        val link = link()
        val episode = WearLinkEpisode("e", "1", "E")

        for (command in listOf(
            WearLinkCommand(WearLinkCommand.TOGGLE),
            WearLinkCommand(WearLinkCommand.PAUSE),
            WearLinkCommand(WearLinkCommand.SEEK_BACK),
            WearLinkCommand(WearLinkCommand.SEEK_FORWARD),
            WearLinkCommand(WearLinkCommand.SET_SPEED, speed = 1.5f),
            WearLinkCommand(WearLinkCommand.PLAY_EPISODE, positionMs = 42L, episode = episode),
            WearLinkCommand("something a newer watch says"),
        )) {
            link.onCommand(WearLinkCodec.encode(command))
        }
        link.onCommand(byteArrayOf(1, 2, 3))

        assertEquals(listOf("toggle", "pause", "back", "forward", "speed 1.5", "play e at 42"), player.calls)
    }

    // Regression: on a real phone the player threw when asked to take playback back, and the app crashed.
    @Test
    fun `a player that fails on a command does not take the app down`() = runTest {
        val link = link()
        player.failing = true

        link.onCommand(WearLinkCodec.encode(WearLinkCommand(WearLinkCommand.PLAY_EPISODE, positionMs = 1L, episode = WearLinkEpisode("e", "1", "E"))))
        link.onCommand(WearLinkCodec.encode(WearLinkCommand(WearLinkCommand.TOGGLE)))

        player.failing = false
        link.onCommand(WearLinkCodec.encode(WearLinkCommand(WearLinkCommand.PAUSE)))
        assertEquals(listOf("pause"), player.calls)
    }

    @Test
    fun `the watch can ask for a show's saved episodes`() = runTest {
        val link = link()

        val answer = link.onRequest(WearLinkProtocol.PATH_EPISODES, WearLinkCodec.encode(EpisodeListRequest("1")))
        val none = link.onRequest(WearLinkProtocol.PATH_EPISODES, WearLinkCodec.encode(EpisodeListRequest("unknown")))

        assertEquals(listOf("e1"), WearLinkCodec.decode<EpisodeListReply>(answer)!!.episodes.map { it.episodeId })
        assertTrue(WearLinkCodec.decode<EpisodeListReply>(none)!!.episodes.isEmpty())
        assertNull(link.onRequest("/boxlore/other", byteArrayOf()))
    }

    @Test
    fun `watch settings follow whichever side changed them last`() = runTest {
        val link = link()
        link.hears(changes(0L))
        settle()

        now = 5_000L
        link.setStorageLimitMb(250)
        link.setStorageLimitMb(123)
        link.setShowsLayout(WearLinkSettings.LAYOUT_LIST)
        settle()
        val mine = WearLinkSettings(WearLinkSettings.LAYOUT_LIST, 250, changedAt = 5_000L)
        assertEquals(mine, link.settings.value)
        assertEquals(mine, store.settings)
        assertEquals(mine, WearLinkCodec.decode<WearLinkSettings>(transport.published[WearLinkProtocol.PATH_PHONE_SETTINGS]))

        val older = WearLinkSettings(WearLinkSettings.LAYOUT_HONEYCOMB, 2000, changedAt = 4_000L)
        link.onItem(LinkItem("node", WearLinkProtocol.PATH_WATCH_SETTINGS, WearLinkCodec.encode(older)))
        assertEquals(mine, link.settings.value)

        val newer = WearLinkSettings(WearLinkSettings.LAYOUT_HONEYCOMB, 1000, changedAt = 9_000L)
        link.onItem(LinkItem("node", WearLinkProtocol.PATH_WATCH_SETTINGS, WearLinkCodec.encode(newer)))
        settle()
        assertEquals(newer, link.settings.value)
        assertEquals(newer, store.settings)
    }

    @Test
    fun `what the watch published while the phone app was closed is read at start`() = runTest {
        store.watchKnown = true
        transport.peerItems[WearLinkProtocol.PATH_WATCH_CHANGES] = WearLinkCodec.encode(changes(2L, 2L to "a"))

        link().start()
        settle()

        assertEquals(listOf("a"), library.applied.single().second.map { it.episodeId })
        assertEquals(mapOf("w" to 2L), publishedLibrary()!!.acked)
    }

    private companion object {
        fun row(id: String, at: Long) = ListeningHistorySyncDto(episodeId = id, podcastId = "1", progressMs = 10L, lastPlayedAt = at, updatedAt = at)
    }
}
