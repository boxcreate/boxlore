package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The stored audio of an episode the watch holds under two IDs, looked up the way the player does:
 * by the ID of the copy the library names, through the real file store.
 */
class WatchStoredAudioTest {
    @TempDir
    lateinit var folder: File

    private val minute = 60_000L
    private val audio = "https://example.org/same.mp3"
    private val own = WatchEpisode("5027634550352900", "1", "Episode", "Show", artworkUrl = null, durationMinutes = 40, audioUrl = audio)
    private val phones = own.copy(id = "-368593920213691381")

    private fun TestScope.downloads(store: WatchLibraryStore): FileWatchDownloads {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val limits = WatchStorageLimits(limitBytes = { Long.MAX_VALUE }, freeBytes = { Long.MAX_VALUE })
        return FileWatchDownloads(File(folder, "audio"), OkHttpClient(), store, limits, backgroundScope, dispatcher)
    }

    /** Puts finished audio on disk the way a download leaves it: one file named by the episode's ID. */
    private fun stored(episode: WatchEpisode): File =
        File(File(folder, "audio").apply { mkdirs() }, FileWatchDownloads.safeName(episode.id) + ".audio").apply { writeText("audio") }

    // Regression: with the audio stored under the phone's older ID, the copy the library named had no file, and the watch streamed.
    @Test
    fun `finished audio under the older ID is the file the player gets, asked for by either ID`() =
        runTest {
            val store = WatchLibraryStore(File(folder, "library.json"), backgroundScope, StandardTestDispatcher(testScheduler))
            val file = stored(phones)
            store.update { it.download(phones, WatchDownload.Done).progress(own, 10 * minute, 40 * minute) }
            val downloads = downloads(store)

            // The player loads the file of the copy the library hands it.
            assertEquals(file, downloads.file(store.state.value.merged(own).id))
            assertEquals(file, downloads.file(store.state.value.merged(phones).id))
            // Under the own ID itself there is no file: naming that copy is what made the watch stream.
            assertNull(downloads.file(own.id))
        }

    @Test
    fun `once the own ID holds finished audio too, its file is the one the player gets`() =
        runTest {
            val store = WatchLibraryStore(File(folder, "library.json"), backgroundScope, StandardTestDispatcher(testScheduler))
            stored(phones)
            val file = stored(own)
            store.update { it.download(phones, WatchDownload.Done).download(own, WatchDownload.Done) }
            val downloads = downloads(store)

            assertEquals(file, downloads.file(store.state.value.merged(own).id))
            assertEquals(file, downloads.file(store.state.value.merged(phones).id))
        }
}
