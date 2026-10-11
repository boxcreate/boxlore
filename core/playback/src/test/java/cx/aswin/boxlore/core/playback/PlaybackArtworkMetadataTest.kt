package cx.aswin.boxlore.core.playback

import android.os.Bundle
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
}
