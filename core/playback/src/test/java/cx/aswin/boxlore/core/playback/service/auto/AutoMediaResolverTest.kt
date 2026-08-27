package cx.aswin.boxlore.core.playback.service.auto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
