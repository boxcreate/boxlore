package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gson can leave Episode.audioUrl null despite its type; Uri.parse(null) used to crash downloads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadAudioUriTest {
    private fun episode(audioUrl: String?): Episode {
        val base = Episode(id = "1", title = "T", description = "", audioUrl = "https://example.com/a.mp3", duration = 0, publishedDate = 0L)
        // Simulate Gson writing null into the non-null field.
        Episode::class.java.getDeclaredField("audioUrl").apply { isAccessible = true }.set(base, audioUrl)
        return base
    }

    @Test
    fun missingOrBlankAudioUrlIsNotDownloadable() {
        assertNull(DownloadAudioUri.parseOrNull(episode(null)))
        assertNull(DownloadAudioUri.parseOrNull(episode("  ")))
    }

    @Test
    fun audioUrlParses() {
        assertEquals("https://example.com/a.mp3", DownloadAudioUri.parseOrNull(episode("https://example.com/a.mp3")).toString())
    }
}
