package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastMediaItemConverterTest {
    private val converter = BoxLoreCastMediaItemConverter()

    @Test
    fun preservesContextQueueProvenanceAcrossCastRoundTrip() {
        val episode = cx.aswin.boxlore.core.model.Episode("rss:opaque", "Episode", "", "https://cdn.example.com/episode.mp3")
        val contextEpisode = PlaybackQueueContext.episodes(listOf(episode)).single()
        val mediaItem = MediaItem.Builder().setMediaId(episode.id).setUri(episode.audioUrl)
            .setMediaMetadata(MediaMetadata.Builder().setExtras(PlaybackQueueContext.extras(contextEpisode, null)).build()).build()
        val restored = converter.toMediaItem(converter.toMediaQueueItem(mediaItem))
        assertEquals("rss:opaque", restored.mediaId)
        assertTrue(PlaybackQueueContext.isContextItem(restored))
    }

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
    fun turnsAQueueRowWithoutMediaIntoAStandInInsteadOfCrashing() {
        // Regression: the receiver reported a row with no media and Media3's converter threw.
        val row = org.mockito.Mockito.mock(MediaQueueItem::class.java)
        org.mockito.Mockito.`when`(row.itemId).thenReturn(7)

        val item = converter.toMediaItem(row)

        assertEquals("${BoxLoreCastMediaItemConverter.PLACEHOLDER_ID_PREFIX}7", item.mediaId)
        assertNull(item.localConfiguration)
    }

    @Test
    fun turnsAQueueRowWithoutMedia3DataIntoAStandInThatKeepsItsPublicAddress() {
        val url = "https://cdn.example.com/episode.mp3"
        val row = MediaQueueItem.Builder(MediaInfo.Builder(url).setContentUrl(url).build()).build()

        val item = converter.toMediaItem(row)

        assertTrue(item.mediaId.startsWith(BoxLoreCastMediaItemConverter.PLACEHOLDER_ID_PREFIX))
        assertEquals(url, item.localConfiguration?.uri?.toString())
    }

    @Test
    fun aStandInNeverTakesAnAddressCastCouldNotStream() {
        val row = MediaQueueItem.Builder(MediaInfo.Builder("content://downloads/episode").build()).build()

        assertNull(converter.toMediaItem(row).localConfiguration)
    }

    @Test
    fun excludesTheInternalRemoteUriFromAnalyticsContext() {
        val extras =
            Bundle().apply {
                putString("entry_point", "queue")
                putString("recommendation_id", "rec-1")
                putString(CastMediaMetadata.REMOTE_URI_KEY, "https://cdn.example.com/episode.mp3")
                // Regression: these reached playback events as properties.
                putStringArrayList("boxlore_artwork_sources", arrayListOf("https://cdn.example.com/cover.jpg?token=1"))
                putString("cx.aswin.boxlore.auto.SOURCE", "discover")
                putInt("android.media.extra.DOWNLOAD_STATUS", 2)
                putString("boxlore_podcast_id", "123")
            }

        assertEquals(
            mapOf("recommendation_id" to "rec-1"),
            analyticsEntryPointContext(extras),
        )
    }
}
