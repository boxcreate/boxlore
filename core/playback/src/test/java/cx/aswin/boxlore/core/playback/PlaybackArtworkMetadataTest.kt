package cx.aswin.boxlore.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackArtworkMetadataTest {
    @Test
    fun `artwork sources are additive and preserve queue provenance without mutating caller extras`() {
        val episode = TestFixtures.episode(imageUrl = "https://art/episode").copy(podcastImageUrl = "https://art/show")
        val existing = Bundle().apply { putString("source_entry_point", "library_latest_episodes") }
        val extras = PlaybackArtworkMetadata.extras(episode, null, existing)
        assertEquals(listOf("https://art/episode", "https://art/show"), extras.getStringArrayList(PlaybackArtworkMetadata.SOURCES))
        assertEquals("library_latest_episodes", extras.getString("source_entry_point"))
        assertEquals(null, existing.getStringArrayList(PlaybackArtworkMetadata.SOURCES))
    }

    // Regression: history saved the player's Android Auto artwork link, which outlived its cover.
    @Test
    fun `saved artwork is the item's real cover, never the player-only link`() {
        val dead = Uri.parse("content://cx.aswin.boxlore.collage/local/38e72bd3")
        val withSources = MediaMetadata.Builder().setArtworkUri(dead)
            .setExtras(Bundle().apply { putStringArrayList(PlaybackArtworkMetadata.SOURCES, arrayListOf("content://cx.aswin.boxlore.collage/art/x", "https://art/episode")) })
            .build()
        assertEquals("https://art/episode", PlaybackArtworkMetadata.savedArtworkUrl(withSources))
        assertEquals(null, PlaybackArtworkMetadata.savedArtworkUrl(MediaMetadata.Builder().setArtworkUri(dead).build()))
        assertEquals("https://art/plain", PlaybackArtworkMetadata.savedArtworkUrl(MediaMetadata.Builder().setArtworkUri(Uri.parse("https://art/plain")).build()))
        assertEquals(null, PlaybackArtworkMetadata.savedArtworkUrl(null))
    }

    @Test
    fun `the original cover is kept ahead of existing sources when the player swaps the artwork`() {
        val existing = Bundle().apply {
            putStringArrayList(PlaybackArtworkMetadata.SOURCES, arrayListOf("https://art/show"))
            putString("source_entry_point", "auto")
        }
        val extras = PlaybackArtworkMetadata.withOriginal(existing, "https://art/episode")
        assertEquals(listOf("https://art/episode", "https://art/show"), extras.getStringArrayList(PlaybackArtworkMetadata.SOURCES))
        assertEquals("auto", extras.getString("source_entry_point"))
        assertEquals(listOf("https://art/show"), PlaybackArtworkMetadata.withOriginal(existing, "content://cx.aswin.boxlore.collage/local/1").getStringArrayList(PlaybackArtworkMetadata.SOURCES))
        assertEquals(null, PlaybackArtworkMetadata.withOriginal(null, null).getStringArrayList(PlaybackArtworkMetadata.SOURCES))
    }
}
