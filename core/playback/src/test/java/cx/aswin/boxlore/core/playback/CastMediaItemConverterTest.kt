package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CastMediaItemConverterTest {
    private val converter = BoxLoreCastMediaItemConverter()

    @Test
    fun forwardsAnEligibleStreamingUri() {
        val url = "https://cdn.example.com/episode.mp3"
        val queueItem = converter.toMediaQueueItem(MediaItem.Builder().setUri(url).build())

        assertEquals(url, queueItem.media?.contentUrl)
    }

    @Test
    fun replacesALocalUriWithItsEligibleRemoteStream() {
        val remoteUrl = "https://cdn.example.com/episode.mp3"
        val metadata =
            MediaMetadata
                .Builder()
                .setExtras(CastMediaMetadata.extrasWithRemoteUri(existing = null, remoteUri = remoteUrl))
                .build()
        val mediaItem =
            MediaItem
                .Builder()
                .setUri("content://downloads/episode")
                .setMediaMetadata(metadata)
                .build()

        assertEquals(remoteUrl, converter.toMediaQueueItem(mediaItem).media?.contentUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnIneligibleEffectiveUri() {
        converter.toMediaQueueItem(
            MediaItem
                .Builder()
                .setUri("http://[::1]/private.mp3")
                .build(),
        )
    }

    @Test
    fun excludesTheInternalRemoteUriFromAnalyticsContext() {
        val extras =
            Bundle().apply {
                putString("entry_point", "queue")
                putString("recommendation_id", "rec-1")
                putString(CastMediaMetadata.REMOTE_URI_KEY, "https://cdn.example.com/episode.mp3")
            }

        assertEquals(
            mapOf("recommendation_id" to "rec-1"),
            analyticsEntryPointContext(extras),
        )
    }
}
