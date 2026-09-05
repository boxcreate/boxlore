package cx.aswin.boxlore.core.playback.service.auto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoMediaResolverTest {
    @Test
    fun `completed download wins while retaining public remote Cast metadata`() {
        val source =
            AutoMediaResolutionPolicy.resolve(
                downloadCompleted = true,
                downloadUri = "content://downloads/episode",
                historyAudioUrl = "https://cdn.example.com/episode.mp3",
                queueAudioUrl = "https://queue.example.com/episode.mp3",
                historyMimeType = "audio/mpeg",
                queueMimeType = "audio/aac",
            )

        assertEquals("content://downloads/episode", source.playbackUri)
        assertEquals("https://cdn.example.com/episode.mp3", source.castRemoteUri)
        assertEquals("audio/mpeg", source.mimeType)
    }

    @Test
    fun `queue metadata fills missing history values`() {
        val source =
            AutoMediaResolutionPolicy.resolve(
                downloadCompleted = false,
                downloadUri = null,
                historyAudioUrl = null,
                queueAudioUrl = "https://queue.example.com/episode.aac",
                historyMimeType = null,
                queueMimeType = "audio/aac",
            )

        assertEquals("https://queue.example.com/episode.aac", source.playbackUri)
        assertEquals("https://queue.example.com/episode.aac", source.castRemoteUri)
        assertEquals("audio/aac", source.mimeType)
    }

    @Test
    fun `private remote source remains playable locally but is excluded from Cast metadata`() {
        val source =
            AutoMediaResolutionPolicy.resolve(
                downloadCompleted = false,
                downloadUri = null,
                historyAudioUrl = "http://192.168.1.5/episode.mp3",
                queueAudioUrl = null,
                historyMimeType = "audio/mpeg",
                queueMimeType = null,
            )

        assertEquals("http://192.168.1.5/episode.mp3", source.playbackUri)
        assertNull(source.castRemoteUri)
    }

    @Test
    fun `API enclosure MIME type remains available for direct resolution`() {
        val source =
            AutoMediaResolutionPolicy.resolve(
                downloadCompleted = false,
                downloadUri = null,
                historyAudioUrl = "https://api.example.com/episode.m4a",
                queueAudioUrl = null,
                historyMimeType = "audio/mp4",
                queueMimeType = null,
            )

        assertEquals("audio/mp4", source.mimeType)
    }

    @Test
    fun `artist-only item metadata preserves artist and enriches albumTitle and subtitle`() {
        assertTrue(
            AutoMediaResolutionPolicy.needsItemMetadataFallback(
                existingArtist = "Solo Artist",
                existingAlbumTitle = null,
                existingSubtitle = null,
            ),
        )

        // Without external fallback: artist enriches the missing fields
        val resolvedWithoutFallback =
            AutoMediaResolutionPolicy.resolveItemMetadata(
                existingArtist = "Solo Artist",
                existingAlbumTitle = null,
                existingSubtitle = null,
            )
        assertEquals("Solo Artist", resolvedWithoutFallback.artist)
        assertEquals("Solo Artist", resolvedWithoutFallback.albumTitle)
        assertEquals("Solo Artist", resolvedWithoutFallback.subtitle)

        // With fallback podcast name: artist is preserved, unresolved fields are enriched
        val resolvedWithFallback =
            AutoMediaResolutionPolicy.resolveItemMetadata(
                existingArtist = "Solo Artist",
                existingAlbumTitle = null,
                existingSubtitle = null,
                fallbackPodcastName = "Discovered Show",
            )
        assertEquals("Solo Artist", resolvedWithFallback.artist)
        assertEquals("Discovered Show", resolvedWithFallback.albumTitle)
        assertEquals("Discovered Show", resolvedWithFallback.subtitle)
    }

    @Test
    fun `album-only item metadata preserves albumTitle and enriches artist and subtitle`() {
        assertTrue(
            AutoMediaResolutionPolicy.needsItemMetadataFallback(
                existingArtist = null,
                existingAlbumTitle = "Solo Album Show",
                existingSubtitle = null,
            ),
        )

        // Without external fallback: albumTitle enriches artist and subtitle
        val resolvedWithoutFallback =
            AutoMediaResolutionPolicy.resolveItemMetadata(
                existingArtist = null,
                existingAlbumTitle = "Solo Album Show",
                existingSubtitle = null,
            )
        assertEquals("Solo Album Show", resolvedWithoutFallback.artist)
        assertEquals("Solo Album Show", resolvedWithoutFallback.albumTitle)
        assertEquals("Solo Album Show", resolvedWithoutFallback.subtitle)

        // With fallback podcast name: albumTitle is preserved, unresolved fields use fallback
        val resolvedWithFallback =
            AutoMediaResolutionPolicy.resolveItemMetadata(
                existingArtist = null,
                existingAlbumTitle = "Solo Album Show",
                existingSubtitle = null,
                fallbackPodcastName = "Fallback Host",
            )
        assertEquals("Fallback Host", resolvedWithFallback.artist)
        assertEquals("Solo Album Show", resolvedWithFallback.albumTitle)
        assertEquals("Fallback Host", resolvedWithFallback.subtitle)
    }

    @Test
    fun `podcast-name-only local metadata triggers fallback and preserves local podcast name`() {
        assertTrue(
            AutoMediaResolutionPolicy.needsPlaybackMetadataFallback(
                localTitle = null,
                localPodcastName = "Local Podcast Name",
                localArtworkUri = null,
            ),
        )

        val resolved =
            AutoMediaResolutionPolicy.resolvePlaybackMetadata(
                localTitle = null,
                localPodcastName = "Local Podcast Name",
                localArtworkUri = null,
                fallbackTitle = "Episode 42: The Answer",
                fallbackPodcastName = "Remote Show Name",
                fallbackArtworkUri = "https://cdn.example.com/artwork.png",
            )

        // Local podcast name preserved while missing title and artwork are enriched from fallback
        assertEquals("Local Podcast Name", resolved.podcastName)
        assertEquals("Episode 42: The Answer", resolved.title)
        assertEquals("https://cdn.example.com/artwork.png", resolved.artworkUri)
    }

    @Test
    fun `subtitle-only item metadata preserves subtitle and enriches artist and albumTitle`() {
        assertTrue(
            AutoMediaResolutionPolicy.needsItemMetadataFallback(
                existingArtist = null,
                existingAlbumTitle = null,
                existingSubtitle = "Solo Subtitle Show",
            ),
        )

        // Without external fallback: subtitle enriches artist and albumTitle
        val resolvedWithoutFallback =
            AutoMediaResolutionPolicy.resolveItemMetadata(
                existingArtist = null,
                existingAlbumTitle = null,
                existingSubtitle = "Solo Subtitle Show",
            )
        assertEquals("Solo Subtitle Show", resolvedWithoutFallback.artist)
        assertEquals("Solo Subtitle Show", resolvedWithoutFallback.albumTitle)
        assertEquals("Solo Subtitle Show", resolvedWithoutFallback.subtitle)

        // With fallback podcast name: subtitle is preserved, unresolved fields use fallback
        val resolvedWithFallback =
            AutoMediaResolutionPolicy.resolveItemMetadata(
                existingArtist = null,
                existingAlbumTitle = null,
                existingSubtitle = "Solo Subtitle Show",
                fallbackPodcastName = "Discovered Show",
            )
        assertEquals("Discovered Show", resolvedWithFallback.artist)
        assertEquals("Discovered Show", resolvedWithFallback.albumTitle)
        assertEquals("Solo Subtitle Show", resolvedWithFallback.subtitle)
    }

    @Test
    fun `title-only local metadata triggers fallback, preserves title and enriches podcast and artwork`() {
        assertTrue(
            AutoMediaResolutionPolicy.needsPlaybackMetadataFallback(
                localTitle = "Local Episode Title",
                localPodcastName = null,
                localArtworkUri = null,
            ),
        )

        val resolved =
            AutoMediaResolutionPolicy.resolvePlaybackMetadata(
                localTitle = "Local Episode Title",
                localPodcastName = null,
                localArtworkUri = null,
                fallbackTitle = "Remote Episode Title",
                fallbackPodcastName = "Remote Show Name",
                fallbackArtworkUri = "https://cdn.example.com/artwork.png",
            )

        assertEquals("Local Episode Title", resolved.title)
        assertEquals("Remote Show Name", resolved.podcastName)
        assertEquals("https://cdn.example.com/artwork.png", resolved.artworkUri)
    }

    @Test
    fun `artwork-only local metadata triggers fallback, preserves artwork and enriches title and podcast`() {
        assertTrue(
            AutoMediaResolutionPolicy.needsPlaybackMetadataFallback(
                localTitle = null,
                localPodcastName = null,
                localArtworkUri = "https://cdn.example.com/local-art.png",
            ),
        )

        val resolved =
            AutoMediaResolutionPolicy.resolvePlaybackMetadata(
                localTitle = null,
                localPodcastName = null,
                localArtworkUri = "https://cdn.example.com/local-art.png",
                fallbackTitle = "Fallback Episode",
                fallbackPodcastName = "Fallback Show",
                fallbackArtworkUri = "https://cdn.example.com/fallback-art.png",
            )

        assertEquals("Fallback Episode", resolved.title)
        assertEquals("Fallback Show", resolved.podcastName)
        assertEquals("https://cdn.example.com/local-art.png", resolved.artworkUri)
    }

    @Test
    fun `blank and whitespace metadata strings are treated as unresolved`() {
        assertTrue(
            AutoMediaResolutionPolicy.needsItemMetadataFallback(
                existingArtist = "  ",
                existingAlbumTitle = "\t",
                existingSubtitle = "\n",
            ),
        )
        assertTrue(
            AutoMediaResolutionPolicy.needsPlaybackMetadataFallback(
                localTitle = "   ",
                localPodcastName = "",
                localArtworkUri = " \t ",
            ),
        )

        val itemMetadata =
            AutoMediaResolutionPolicy.resolveItemMetadata(
                existingArtist = "   ",
                existingAlbumTitle = "  ",
                existingSubtitle = null,
                fallbackPodcastName = "Fallback Show",
            )
        assertEquals("Fallback Show", itemMetadata.artist)
        assertEquals("Fallback Show", itemMetadata.albumTitle)
        assertEquals("Fallback Show", itemMetadata.subtitle)

        val playbackMetadata =
            AutoMediaResolutionPolicy.resolvePlaybackMetadata(
                localTitle = "  ",
                localPodcastName = "   ",
                localArtworkUri = "\t",
                fallbackTitle = "Episode 1",
                fallbackPodcastName = "Show 1",
                fallbackArtworkUri = "https://example.com/art.jpg",
            )
        assertEquals("Episode 1", playbackMetadata.title)
        assertEquals("Show 1", playbackMetadata.podcastName)
        assertEquals("https://example.com/art.jpg", playbackMetadata.artworkUri)
    }

    @Test
    fun `fully resolved metadata skips fallback`() {
        assertFalse(
            AutoMediaResolutionPolicy.needsItemMetadataFallback(
                existingArtist = "Artist",
                existingAlbumTitle = "Album",
                existingSubtitle = "Subtitle",
            ),
        )
        assertFalse(
            AutoMediaResolutionPolicy.needsPlaybackMetadataFallback(
                localTitle = "Title",
                localPodcastName = "Podcast",
                localArtworkUri = "https://example.com/art.jpg",
            ),
        )
    }
}
