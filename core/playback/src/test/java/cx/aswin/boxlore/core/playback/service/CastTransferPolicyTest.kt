package cx.aswin.boxlore.core.playback.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import cx.aswin.boxlore.core.playback.BoxLoreCastMediaItemConverter
import cx.aswin.boxlore.core.playback.CastMediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastTransferPolicyTest {
    @Test
    fun `filters receiver placeholders and keeps current playable item selected`() {
        val first = playableItem("first")
        val current = playableItem("current")

        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(MediaItem.EMPTY, first, current),
                currentIndex = 2,
            )

        assertEquals(listOf(first, current), result.mediaItems)
        assertEquals(1, result.currentIndex)
        assertFalse(result.resetPosition)
    }

    @Test
    fun `moves to nearest playable item and resets position when current row is invalid`() {
        val next = playableItem("next")

        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(MediaItem.EMPTY, next),
                currentIndex = 0,
            )

        assertEquals(listOf(next), result.mediaItems)
        assertEquals(0, result.currentIndex)
        assertTrue(result.resetPosition)
    }

    @Test
    fun `uses unset index when receiver provides no playable rows`() {
        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(MediaItem.EMPTY, MediaItem.Builder().setUri(Uri.EMPTY).build()),
                currentIndex = 0,
            )

        assertTrue(result.mediaItems.isEmpty())
        assertEquals(C.INDEX_UNSET, result.currentIndex)
        assertTrue(result.resetPosition)
    }

    @Test
    fun `leaves episodes a Cast receiver cannot fetch out of a transfer to Cast`() {
        // Regression: a download or a private-host episode anywhere in the queue crashed the transfer.
        val download = localItem("download")
        val current = playableItem("current")
        val privateHost = MediaItem.Builder().setMediaId("private").setUri("http://192.168.1.20/episode.mp3").build()
        val downloadWithStream = localItem("download-with-stream", remoteUri = "https://cdn.example.com/stream.mp3")

        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(download, current, privateHost, downloadWithStream),
                currentIndex = 1,
                targetIsRemote = true,
            )

        assertEquals(listOf(current, downloadWithStream), result.mediaItems)
        assertEquals(0, result.currentIndex)
        assertFalse(result.resetPosition)
    }

    @Test
    fun `starts the next castable episode from the beginning when the current one cannot be cast`() {
        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(playableItem("previous"), localItem("current"), playableItem("next")),
                currentIndex = 1,
                targetIsRemote = true,
            )

        assertEquals(listOf("previous", "next"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.currentIndex)
        assertTrue(result.resetPosition)
    }

    @Test
    fun `sends nothing to Cast when no episode can be cast`() {
        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(localItem("one"), localItem("two")),
                currentIndex = 1,
                targetIsRemote = true,
            )

        assertTrue(result.mediaItems.isEmpty())
        assertEquals(C.INDEX_UNSET, result.currentIndex)
        assertTrue(result.resetPosition)
    }

    @Test
    fun `keeps local episodes when the queue returns to the phone`() {
        val download = localItem("download")
        val stream = playableItem("stream")

        val result = CastTransferPolicy.sanitize(mediaItems = listOf(download, stream), currentIndex = 1)

        assertEquals(listOf(download, stream), result.mediaItems)
        assertEquals(1, result.currentIndex)
        assertFalse(result.resetPosition)
    }

    @Test
    fun `drops a stand-in Cast row when the queue returns to the phone even if it has an address`() {
        val standIn =
            MediaItem
                .Builder()
                .setMediaId("${BoxLoreCastMediaItemConverter.PLACEHOLDER_ID_PREFIX}7")
                .setUri("https://cdn.example.com/unknown.mp3")
                .build()
        val current = playableItem("current")

        val result = CastTransferPolicy.sanitize(mediaItems = listOf(standIn, current), currentIndex = 1)

        assertEquals(listOf(current), result.mediaItems)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `transfer to the Cast player carries only castable episodes`() {
        val source = mock(Player::class.java)
        val queue = listOf(localItem("download"), playableItem("current"))
        `when`(source.availableCommands).thenReturn(allCommands)
        `when`(source.currentTimeline).thenReturn(Timeline.EMPTY)
        `when`(source.mediaItemCount).thenReturn(queue.size)
        `when`(source.getMediaItemAt(anyInt())).thenAnswer { queue[it.getArgument<Int>(0)] }
        `when`(source.currentMediaItemIndex).thenReturn(1)
        `when`(source.currentPosition).thenReturn(42_000L)
        `when`(source.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        `when`(source.trackSelectionParameters).thenReturn(TrackSelectionParameters.DEFAULT)
        val target = remotePlayer()

        BoxLoreCastTransferCallback().transferState(source, target)

        verify(target).setMediaItems(listOf(queue[1]), 0, 42_000L)
    }

    @Test
    fun `playing an episode Cast cannot stream during a Cast session leaves the Cast queue alone`() {
        // Regression: the request reached the Cast converter, which rejects it, and crashed the service.
        val target = remotePlayer()
        val guarded = CastQueueGuardPlayer(target)

        guarded.setMediaItems(listOf(localItem("download")), 0, 5_000L)
        guarded.setMediaItems(listOf(localItem("download")))
        guarded.setMediaItem(localItem("download"))
        guarded.addMediaItem(localItem("download"))
        guarded.addMediaItems(1, listOf(localItem("download")))
        guarded.replaceMediaItem(0, localItem("download"))

        verify(target, never()).setMediaItems(anyList(), anyInt(), anyLong())
        verify(target, never()).setMediaItems(anyList(), anyBoolean())
        verify(target, never()).addMediaItems(anyList())
        verify(target, never()).addMediaItems(anyInt(), anyList())
        verify(target, never()).replaceMediaItems(anyInt(), anyInt(), anyList())
    }

    @Test
    fun `queueing a mixed list during a Cast session sends only what Cast can stream`() {
        val target = remotePlayer()
        val guarded = CastQueueGuardPlayer(target)
        val first = playableItem("first")
        val tapped = playableItem("tapped")

        guarded.setMediaItems(listOf(localItem("download"), first, tapped), 2, 9_000L)
        guarded.setMediaItems(listOf(localItem("tapped-download"), first), 0, 9_000L)
        guarded.addMediaItems(listOf(localItem("download"), first))

        verify(target).setMediaItems(listOf(first, tapped), 1, 9_000L)
        verify(target).setMediaItems(listOf(first), 0, 0L)
        verify(target).addMediaItems(listOf(first))
    }

    @Test
    fun `local playback gets every request unchanged`() {
        val target = mock(Player::class.java)
        `when`(target.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build())
        val guarded = CastQueueGuardPlayer(target)
        val items = listOf(localItem("download"), playableItem("stream"))

        guarded.setMediaItems(items, 0, 5_000L)
        guarded.addMediaItems(items)
        guarded.setMediaItem(items.first())

        verify(target).setMediaItems(items, 0, 5_000L)
        verify(target).addMediaItems(items)
        verify(target).setMediaItems(listOf(items.first()), true)
    }

    private val allCommands =
        Player.Commands
            .Builder()
            .addAllCommands()
            .build()

    private fun remotePlayer(): Player = mock(Player::class.java).also {
        `when`(it.deviceInfo).thenReturn(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build())
        `when`(it.availableCommands).thenReturn(allCommands)
    }

    private fun localItem(id: String, remoteUri: String? = null): MediaItem = MediaItem
        .Builder()
        .setMediaId(id)
        .setUri("content://downloads/$id")
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setExtras(CastMediaMetadata.extrasWithRemoteUri(existing = null, remoteUri = remoteUri))
                .build(),
        ).build()

    private fun playableItem(id: String): MediaItem = MediaItem
        .Builder()
        .setMediaId(id)
        .setUri("https://cdn.example.com/$id.mp3")
        .build()
}
