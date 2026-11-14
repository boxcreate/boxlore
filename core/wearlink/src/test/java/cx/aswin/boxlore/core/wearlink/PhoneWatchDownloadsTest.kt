package cx.aswin.boxlore.core.wearlink

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneWatchDownloadsTest {
    private val transport = RecordingWearLinkTransport()
    private val episode = WearLinkEpisode("phone-id", "1", "E", audioUrl = "https://example.org/e.mp3")

    private fun orders() = transport.sent.map { WearLinkCodec.decode<WearLinkCommand>(it.second)!! }

    @Test
    fun `an episode is found on the watch by its ID or, under another ID, by its audio`() {
        val stored = listOf(WatchStoredEpisode("watch-id", "https://example.org/e.mp3", done = true), WatchStoredEpisode("other", "https://example.org/o.mp3"))

        assertEquals(WatchCopy.OnWatch, WatchCopyRules.of("phone-id", "https://example.org/e.mp3", stored))
        assertEquals(WatchCopy.Downloading, WatchCopyRules.of("other", null, stored))
        assertEquals(WatchCopy.None, WatchCopyRules.of("phone-id", "https://example.org/elsewhere.mp3", stored))
        assertEquals(WatchCopy.None, WatchCopyRules.of("phone-id", null, emptyList()))
    }

    @Test
    fun `sending shows the download at once, and the watch's report then decides`() = runTest {
        val downloads = PhoneWatchDownloads(transport, backgroundScope)
        val results = ArrayList<Boolean>()

        downloads.send(episode) { results += it }
        testScheduler.runCurrent()

        assertEquals(listOf(true), results)
        assertEquals(listOf(WearLinkCommand.DOWNLOAD_EPISODE), orders().map { it.action })
        assertEquals("phone-id", orders().single().episode?.episodeId)
        assertEquals(WatchCopy.Downloading, WatchCopyRules.of("phone-id", episode.audioUrl, downloads.stored.value))

        downloads.report(listOf(WatchStoredEpisode("watch-id", episode.audioUrl, done = true)))
        assertEquals(WatchCopy.OnWatch, WatchCopyRules.of("phone-id", episode.audioUrl, downloads.stored.value))

        // The watch refused it, for instance over the storage limit: its report no longer lists it.
        downloads.report(emptyList())
        assertEquals(WatchCopy.None, WatchCopyRules.of("phone-id", episode.audioUrl, downloads.stored.value))
    }

    @Test
    fun `removing forgets the episode whichever ID the watch reported it under`() = runTest {
        val downloads = PhoneWatchDownloads(transport, backgroundScope)
        downloads.report(listOf(WatchStoredEpisode("watch-id", episode.audioUrl, done = true)))

        downloads.remove(episode)
        testScheduler.runCurrent()

        assertEquals(listOf(WearLinkCommand.REMOVE_DOWNLOAD), orders().map { it.action })
        assertTrue(downloads.stored.value.isEmpty())
    }

    @Test
    fun `a watch out of reach is told nothing and nothing is assumed`() = runTest {
        val downloads = PhoneWatchDownloads(transport, backgroundScope)
        transport.reachable.value = false
        val results = ArrayList<Boolean>()

        downloads.send(episode) { results += it }
        testScheduler.runCurrent()

        assertEquals(listOf(false), results)
        assertTrue(downloads.stored.value.isEmpty())
    }
}
