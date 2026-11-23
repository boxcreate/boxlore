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
    fun preservesPlaylistProvenanceAcrossCastRoundTrip() {
        val episode = cx.aswin.boxlore.core.model.Episode("42", "Episode", "", "https://cdn.example.com/episode.mp3")
        val contextEpisode = PlaybackQueueContext.playlistEpisodes(listOf(episode), "list-a").single()
        val mediaItem = MediaItem.Builder().setMediaId(episode.id).setUri(episode.audioUrl)
            .setMediaMetadata(MediaMetadata.Builder().setExtras(PlaybackQueueContext.extras(contextEpisode, null)).build()).build()
        val restored = converter.toMediaItem(converter.toMediaQueueItem(mediaItem))
        assertEquals(PlaybackQueueContext.PLAYLIST, PlaybackQueueContext.contextEntryPoint(restored))
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

    @Test
    fun sendsTheCoversWebAddressInsteadOfTheInAppLink() {
        // Regression: the TV got the Android Auto cover link (content://<app>.collage/art/...) and showed no cover.
        val inAppLink = "content://cx.aswin.boxlore.collage/art/abc"
        val saved = "https://cdn.example.com/episode-cover.jpg"
        fun item(artwork: String?, sources: List<String> = emptyList()) = MediaItem.Builder()
            .setUri("https://cdn.example.com/episode.mp3")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(artwork?.let(android.net.Uri::parse))
                    .setExtras(Bundle().apply { putStringArrayList(PlaybackArtworkMetadata.SOURCES, ArrayList(sources)) })
                    .build(),
            ).build()
        fun BoxLoreCastMediaItemConverter.cover(item: MediaItem) =
            toMediaQueueItem(item).media?.metadata?.images?.firstOrNull()?.url?.toString()

        assertEquals(saved, converter.cover(item(inAppLink, listOf(inAppLink, saved))))
        assertEquals(saved, converter.cover(item(saved)))
        assertNull(converter.cover(item(inAppLink)))
        // An item restored with only the link: the address registered behind it.
        val registered = BoxLoreCastMediaItemConverter { link -> if (link.toString() == inAppLink) listOf(saved) else emptyList() }
        assertEquals(saved, registered.cover(item(inAppLink)))
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
    fun aRowAnotherSenderLoadedBecomesAStandInInsteadOfCrashing() {
        // Regression: a row with its own custom data but no Media3 payload made Media3's converter throw.
        val url = "https://cdn.example.com/other.mp3"
        val media = MediaInfo.Builder(url).setContentUrl(url).setCustomData(org.json.JSONObject().put("sender", "other")).build()

        val item = converter.toMediaItem(MediaQueueItem.Builder(media).build())

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
