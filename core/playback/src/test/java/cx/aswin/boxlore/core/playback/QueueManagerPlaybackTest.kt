package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueManagerPlaybackTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var queueRepository: QueueRepository
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var queueCoordinator: PlaybackQueueCoordinator

    private fun <T> safeEq(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun <T> safeCapture(
        captor: ArgumentCaptor<T>,
        fallback: T,
    ): T {
        captor.capture()
        return fallback
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        queueRepository = mock(QueueRepository::class.java)
        playbackRepository = mock(PlaybackRepository::class.java)
        queueCoordinator = mock(PlaybackQueueCoordinator::class.java)
        `when`(playbackRepository.queueCoordinator).thenReturn(queueCoordinator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `playEpisode propagates preferredSort and explicit contextSourceId`() =
        runTest(testDispatcher) {
            val queueManager = QueueManager(queueRepository, playbackRepository)
            val episodeItem =
                EpisodeItem(
                    id = 101L,
                    title = "Test Episode",
                    enclosureUrl = "https://example.com/audio.mp3",
                )
            val podcast =
                Podcast(
                    id = "pod-1",
                    title = "Test Podcast",
                    artist = "Test Artist",
                    imageUrl = "https://example.com/art.png",
                    preferredSort = "newest",
                )
            val bundle =
                Bundle().apply {
                    putString("source_entry_point", "podcast_detail")
                }

            queueManager.playEpisode(
                episode = episodeItem,
                podcast = podcast,
                preferredSort = "oldest",
                entryPointContext = bundle,
            )
            advanceUntilIdle()

            val podcastCaptor = ArgumentCaptor.forClass(Podcast::class.java)

            @Suppress("UNCHECKED_CAST")
            val episodeListCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Episode>>

            verify(queueRepository).clearQueue()
            verify(queueRepository).addToQueue(
                safeEq(episodeItem),
                safeCapture(podcastCaptor, podcast),
                safeEq(null),
                safeEq("podcast_detail"),
            )
            assertEquals("oldest", podcastCaptor.value.preferredSort)

            verify(queueCoordinator).playQueue(
                safeCapture(episodeListCaptor, emptyList()),
                safeCapture(podcastCaptor, podcast),
                safeEq(0),
                safeEq(PlaybackEntryPoint.GENERIC),
                safeEq(null),
                safeEq(bundle),
            )
            assertEquals("podcast_detail", episodeListCaptor.value.first().contextSourceId)
            assertEquals("oldest", podcastCaptor.value.preferredSort)
        }

    @Test
    fun `playEpisode falls back to entry_point bundle string when source_entry_point is missing`() =
        runTest(testDispatcher) {
            val queueManager = QueueManager(queueRepository, playbackRepository)
            val episodeItem =
                EpisodeItem(
                    id = 102L,
                    title = "Fallback Episode",
                    enclosureUrl = "https://example.com/audio2.mp3",
                )
            val podcast =
                Podcast(
                    id = "pod-2",
                    title = "Fallback Podcast",
                    artist = "Artist",
                    imageUrl = "https://example.com/art.png",
                )
            val bundle =
                Bundle().apply {
                    putString("entry_point", "home_for_you")
                }

            queueManager.playEpisode(
                episode = episodeItem,
                podcast = podcast,
                preferredSort = null,
                entryPointContext = bundle,
            )
            advanceUntilIdle()

            verify(queueRepository).addToQueue(
                safeEq(episodeItem),
                safeEq(podcast),
                safeEq(null),
                safeEq("home_for_you"),
            )
        }

    @Test
    fun `playEpisode falls back to coarse entryPoint name when bundle is null`() =
        runTest(testDispatcher) {
            val queueManager = QueueManager(queueRepository, playbackRepository)
            val episodeItem =
                EpisodeItem(
                    id = 103L,
                    title = "No Bundle Episode",
                    enclosureUrl = "https://example.com/audio3.mp3",
                )
            val podcast =
                Podcast(
                    id = "pod-3",
                    title = "No Bundle Podcast",
                    artist = "Artist",
                    imageUrl = "https://example.com/art.png",
                )

            queueManager.playEpisode(
                episode = episodeItem,
                podcast = podcast,
                preferredSort = null,
                entryPointContext = null,
            )
            advanceUntilIdle()

            verify(queueRepository).addToQueue(
                safeEq(episodeItem),
                safeEq(podcast),
                safeEq(null),
                safeEq("generic"),
            )
        }

    @Test
    fun `setOutputVolume sets device volume when remote route and command available`() {
        val mockController = mock(MediaController::class.java)
        `when`(mockController.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)).thenReturn(true)

        val player = mock(PlaybackRepository::class.java)
        val handle = PlaybackMediaControllerHandle()
        handle.controller = mockController

        val stateFlow =
            MutableStateFlow(
                PlayerState(
                    playbackRoute =
                        PlaybackRouteState(
                            deviceName = "Living Room TV",
                            isRemote = true,
                            volume = 10,
                            maximumVolume = 20,
                        ),
                ),
            )

        `when`(player.mediaHandle).thenReturn(handle)
        `when`(player.playerStateFlow).thenReturn(stateFlow)

        player.setOutputVolume(15)

        verify(mockController).setDeviceVolume(15, C.VOLUME_FLAG_SHOW_UI)
    }

    @Test
    fun `setOutputVolume does not set device volume when command is not available`() {
        val mockController = mock(MediaController::class.java)
        `when`(mockController.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)).thenReturn(false)

        val player = mock(PlaybackRepository::class.java)
        val handle = PlaybackMediaControllerHandle()
        handle.controller = mockController

        val stateFlow =
            MutableStateFlow(
                PlayerState(
                    playbackRoute =
                        PlaybackRouteState(
                            deviceName = "Living Room TV",
                            isRemote = true,
                            volume = 10,
                            maximumVolume = 20,
                        ),
                ),
            )

        `when`(player.mediaHandle).thenReturn(handle)
        `when`(player.playerStateFlow).thenReturn(stateFlow)

        player.setOutputVolume(15)

        verify(mockController, never()).setDeviceVolume(anyInt(), anyInt())
    }

    @Test
    fun `setOutputVolume does not set device volume when route is local`() {
        val mockController = mock(MediaController::class.java)
        `when`(mockController.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)).thenReturn(true)

        val player = mock(PlaybackRepository::class.java)
        val handle = PlaybackMediaControllerHandle()
        handle.controller = mockController

        val stateFlow =
            MutableStateFlow(
                PlayerState(
                    playbackRoute =
                        PlaybackRouteState(
                            deviceName = "Phone Speaker",
                            isRemote = false,
                            volume = 10,
                            maximumVolume = 20,
                        ),
                ),
            )

        `when`(player.mediaHandle).thenReturn(handle)
        `when`(player.playerStateFlow).thenReturn(stateFlow)

        player.setOutputVolume(15)

        verify(mockController, never()).setDeviceVolume(anyInt(), anyInt())
    }

    @Test
    fun `setOutputVolume does not crash when controller is null`() {
        val player = mock(PlaybackRepository::class.java)
        val handle = PlaybackMediaControllerHandle()
        handle.controller = null

        val stateFlow = MutableStateFlow(PlayerState())
        `when`(player.mediaHandle).thenReturn(handle)
        `when`(player.playerStateFlow).thenReturn(stateFlow)

        player.setOutputVolume(10)
    }
}
