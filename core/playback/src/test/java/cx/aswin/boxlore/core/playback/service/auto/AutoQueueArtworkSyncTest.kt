package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every episode in the car's queue shows its cover, not only the one playing. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoQueueArtworkSyncTest {
    private val scope = TestScope(StandardTestDispatcher())
    private val host = mock(AutoBrowseLibraryHost::class.java)
    private val player = mock(Player::class.java)
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AutoArtworkSourceStore.clearMemoryForTests()
        `when`(host.asContext()).thenReturn(context)
        `when`(host.serviceScope).thenReturn(scope)
        `when`(player.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build())
    }

    private fun webItem(id: String): MediaItem = item(id, "https://cdn.example/$id.jpg")

    private fun carItem(id: String): MediaItem = item(id, AutoArtworkRepository.remoteUri(context, "https://cdn.example/$id.jpg").toString())

    private fun item(id: String, artwork: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(Uri.parse(artwork)).build())
        .build()

    private fun queue(playing: Int, vararg items: MediaItem) {
        `when`(player.mediaItemCount).thenReturn(items.size)
        `when`(player.currentMediaItemIndex).thenReturn(playing)
        items.forEachIndexed { index, item -> `when`(player.getMediaItemAt(index)).thenReturn(item) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun replaced(from: Int, to: Int): List<MediaItem> {
        val items = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MediaItem>>
        verify(player).replaceMediaItems(Mockito.eq(from), Mockito.eq(to), items.capture())
        return items.value
    }

    private fun neverReplaced() = verify(player, never()).replaceMediaItems(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyList())

    @Test
    fun queuedEpisodesWithWebCoversAreSwappedAroundThePlayingOne() {
        queue(1, webItem("a"), webItem("playing"), carItem("c"), webItem("d"), webItem("e"))

        AutoQueueArtworkSync(host).refresh(player)

        assertEquals(listOf("a"), replaced(0, 1).map { it.mediaId })
        val tail = replaced(3, 5)
        assertEquals(listOf("d", "e"), tail.map { it.mediaId })
        assertEquals(listOf("content", "content"), tail.map { it.mediaMetadata.artworkUri?.scheme })
    }

    @Test
    fun aQueueTheCarCanAlreadyShowIsLeftAlone() {
        queue(0, webItem("playing"), carItem("b"), carItem("c"))

        AutoQueueArtworkSync(host).refresh(player)

        neverReplaced()
    }

    @Test
    fun aCastQueueKeepsItsWebCovers() {
        `when`(player.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        queue(0, webItem("playing"), webItem("b"))

        AutoQueueArtworkSync(host).refresh(player)

        neverReplaced()
    }

    @Test
    fun theQueueIsCheckedWhenTheCarConnectsAndWheneverItChanges() = scope.runTest {
        val sync = AutoQueueArtworkSync(host)
        queue(0, webItem("playing"), webItem("b"))

        sync.start(player)
        runCurrent()
        assertEquals(listOf("b"), replaced(1, 2).map { it.mediaId })

        // Smart Queue adds an episode, then playback moves on to the next one: one pass covers both.
        Mockito.clearInvocations(player)
        queue(1, webItem("playing"), carItem("b"), webItem("added"))
        sync.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        sync.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        runCurrent()
        assertEquals(listOf("playing"), replaced(0, 1).map { it.mediaId })
        assertEquals(listOf("added"), replaced(2, 3).map { it.mediaId })
    }

    @Test
    fun aQueueThatOnlyReloadedItsLengthsIsNotChecked() = scope.runTest {
        val sync = AutoQueueArtworkSync(host)
        queue(0, carItem("playing"))
        sync.start(player)
        runCurrent()
        queue(0, carItem("playing"), webItem("b"))

        sync.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        runCurrent()

        neverReplaced()
    }

    @Test
    fun nothingIsWatchedOnceTheCarHasGone() = scope.runTest {
        val sync = AutoQueueArtworkSync(host)
        queue(0, carItem("playing"))
        sync.start(player)
        runCurrent()

        sync.stop()
        queue(0, carItem("playing"), webItem("b"))
        sync.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        runCurrent()

        verify(player).addListener(sync)
        verify(player).removeListener(sync)
        neverReplaced()
    }
}
