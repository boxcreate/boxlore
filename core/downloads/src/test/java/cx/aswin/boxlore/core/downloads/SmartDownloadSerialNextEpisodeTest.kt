package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for how Smart Downloads finds the next episode of a show listened to oldest first
 * ([SmartDownloadCandidateLogic.findSerialNextEpisode]) and shares network pages between shows.
 */
class SmartDownloadSerialNextEpisodeTest {
    @Test
    fun `serial next episode is found past the first page`() = runTest {
        val list = (1..250).map { episode("e$it", "serial") }
        val reads = mutableListOf<Int>()

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e150",
                isUnfinished = { it.id != "e151" },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset ->
                    reads += offset
                    page(list, offset)
                },
                maxEpisodes = 1_000,
            )

        // e151 is finished, so the next unfinished episode after e150 is e152.
        assertEquals("e152", next?.id)
        assertEquals(listOf(0, 100), reads)
    }

    @Test
    fun `serial next episode after the last of a page comes from the next page`() = runTest {
        val list = (1..150).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e100",
                isUnfinished = { true },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 1_000,
            )

        assertEquals("e101", next?.id)
    }

    @Test
    fun `serial with no played episode skips a finished first episode`() = runTest {
        val list = (1..5).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = null,
                isUnfinished = { it.id != "e1" },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 1_000,
            )

        assertEquals("e2", next?.id)
    }

    @Test
    fun `serial anchor is matched under another stored ID of its episode`() = runTest {
        val list = (1..3).map { episode("feed-$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "pi-1",
                isUnfinished = { true },
                groupTokens = { ids -> ids.associateWith { id -> if (id == "pi-1" || id == "feed-1") "episode-1" else id } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 1_000,
            )

        assertEquals("feed-2", next?.id)
    }

    @Test
    fun `serial scan that stops at its cap before the anchor gives no pick when the newest end cannot be read`() = runTest {
        val list = (1..500).map { episode("e$it", "serial") }
        val reads = mutableListOf<Int>()

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e450",
                isUnfinished = { it.id != "e1" },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset ->
                    reads += offset
                    page(list, offset)
                },
                maxEpisodes = 200,
                loadNewestPage = { null },
            )

        // Where the listener is was never read, so a skipped early episode is not offered instead.
        assertNull(next)
        assertEquals(listOf(0, 100), reads)
    }

    @Test
    fun `serial page that fails before the anchor gives no pick instead of a skipped early episode`() = runTest {
        val list = (1..300).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e150",
                // Only e4 (skipped long ago) and e151 are unfinished.
                isUnfinished = { it.id == "e4" || it.id == "e151" },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset ->
                    // The second page fails, as a 429 does: an empty page with nothing more.
                    if (offset == 0) page(list, offset) else SmartDownloadCandidateLogic.SerialPage(emptyList(), 0, false)
                },
                maxEpisodes = 1_000,
            )

        assertNull(next)
    }

    @Test
    fun `serial page that fails after the anchor gives no pick instead of a skipped early episode`() = runTest {
        val list = (1..300).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e50",
                // Everything after e50 on the first page is finished; e4 was skipped long ago.
                isUnfinished = { it.id == "e4" || it.id == "e151" },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset ->
                    if (offset == 0) page(list, offset) else SmartDownloadCandidateLogic.SerialPage(emptyList(), 0, false)
                },
                maxEpisodes = 1_000,
            )

        assertNull(next)
    }

    @Test
    fun `serial whose anchor is not in the whole list falls back to the first unfinished episode`() = runTest {
        val list = (1..150).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "gone",
                isUnfinished = { it.id != "e1" },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 1_000,
            )

        assertEquals("e2", next?.id)
    }

    @Test
    fun `a caught-up serial read from the network loads at most its own pages`() = runTest {
        val list = (1..1_000).map { episode("e$it", "serial") }
        val networkPages = SmartDownloadCandidateLogic.SerialNetworkPages(extraPages = 6, perShowPages = 3, newestPages = 6)
        var loads = 0

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e1000",
                isUnfinished = { false },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset ->
                    loads++
                    if (offset > 0) networkPages.spendExtraPage()
                    page(list, offset)
                },
                maxEpisodes = 100 * networkPages.pagesForNextShow(),
            )

        assertNull(next)
        assertEquals(3, loads)
        // Two of the six shared extra pages are gone.
        assertEquals(3, networkPages.pagesForNextShow())
        networkPages.spendExtraPage()
        networkPages.spendExtraPage()
        networkPages.spendExtraPage()
        assertEquals(2, networkPages.pagesForNextShow())
    }

    @Test
    fun `serial shows read from the network get only their first page once the shared pages run out`() {
        val networkPages = SmartDownloadCandidateLogic.SerialNetworkPages(extraPages = 2, perShowPages = 3, newestPages = 6)

        assertEquals(3, networkPages.pagesForNextShow())
        networkPages.spendExtraPage()
        networkPages.spendExtraPage()
        assertEquals(1, networkPages.pagesForNextShow())
        networkPages.spendExtraPage()
        assertEquals(1, networkPages.pagesForNextShow())
    }

    @Test
    fun `serial caught up on its last episode falls back to the first unfinished one`() = runTest {
        val list = (1..4).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e4",
                isUnfinished = { it.id == "e3" },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 1_000,
            )

        assertEquals("e3", next?.id)
    }

    @Test
    fun `serial scan cut off before the latest play of a caught-up listener picks the new episode from the newest end`() = runTest {
        val list = (1..401).map { episode("e$it", "serial") }
        var newestReads = 0

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                // The latest play is stored under another ID of episode 400.
                anchorId = "pi-400",
                isUnfinished = { number(it) > 400 },
                groupTokens = { ids -> ids.associateWith { id -> if (id == "pi-400" || id == "e400") "episode-400" else id } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 300,
                loadNewestPage = {
                    newestReads++
                    newest(list)
                },
            )

        assertEquals("e401", next?.id)
        assertEquals(1, newestReads)
    }

    @Test
    fun `serial scan cut off with the latest play on the newest episode picks the first unfinished episode read`() = runTest {
        // The listener works through the backlog (up to 60) and also played the newest episode, 450.
        val list = (1..450).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e450",
                isUnfinished = { number(it) in 61..449 },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 300,
                loadNewestPage = { newest(list) },
            )

        assertEquals("e61", next?.id)
    }

    @Test
    fun `serial scan cut off with the latest play in neither range never jumps ahead`() = runTest {
        val list = (1..450).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e330",
                isUnfinished = { number(it) > 330 },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 300,
                loadNewestPage = { newest(list) },
            )

        assertNull(next)
    }

    @Test
    fun `serial scan cut off on a show finished to its newest episode gives no pick`() = runTest {
        val list = (1..450).map { episode("e$it", "serial") }

        val next =
            SmartDownloadCandidateLogic.findSerialNextEpisode(
                anchorId = "e450",
                isUnfinished = { false },
                groupTokens = { ids -> ids.associateWith { it } },
                loadPage = { offset -> page(list, offset) },
                maxEpisodes = 300,
                loadNewestPage = { newest(list) },
            )

        assertNull(next)
    }

    @Test
    fun `serial shows read for their next episode leave out auto-download and newest-first shows, highest score first`() {
        val subs =
            listOf(
                podcastEntity("a-auto", preferredSort = "oldest", autoDownloadEnabled = true),
                podcastEntity("b-low", preferredSort = "oldest"),
                podcastEntity("c-newest", preferredSort = "newest"),
                podcastEntity("d-high", preferredSort = "oldest"),
                podcastEntity("e-unscored", preferredSort = "oldest"),
            )

        val shows = SmartDownloadCandidateLogic.serialShowsToResolve(subs, mapOf("b-low" to 1.0, "d-high" to 5.0, "a-auto" to 9.0))

        assertEquals(listOf("d-high", "b-low", "e-unscored"), shows.map { it.podcastId })
    }

    @Test
    fun `newest-first network pages run out once the shared ones are taken`() {
        val networkPages = SmartDownloadCandidateLogic.SerialNetworkPages(extraPages = 0, perShowPages = 3, newestPages = 2)

        assertTrue(networkPages.takeNewestPage())
        assertTrue(networkPages.takeNewestPage())
        assertFalse(networkPages.takeNewestPage())
    }

    private fun number(episode: Episode) = episode.id.removePrefix("e").toInt()

    /** The first page of [list] newest first, as the newest-first sort reads it. */
    private fun newest(list: List<Episode>) = list.asReversed().take(100)

    private fun page(list: List<Episode>, offset: Int): SmartDownloadCandidateLogic.SerialPage {
        val episodes = list.drop(offset).take(100)
        return SmartDownloadCandidateLogic.SerialPage(episodes, episodes.size, offset + episodes.size < list.size)
    }

    private fun episode(id: String, podcastId: String) = Episode(
        id = id,
        title = "Episode $id",
        description = "",
        audioUrl = "https://audio/$id.mp3",
        imageUrl = "https://img/$id.png",
        podcastImageUrl = "https://img/$podcastId.png",
        podcastTitle = "Podcast $podcastId",
        podcastId = podcastId,
        duration = 1800,
        publishedDate = 0L,
    )

    private fun podcastEntity(id: String, preferredSort: String, autoDownloadEnabled: Boolean = false,) = PodcastEntity(
        podcastId = id,
        title = "Podcast $id",
        author = "Artist $id",
        imageUrl = "https://img/$id.png",
        description = "Description $id",
        isSubscribed = true,
        preferredSort = preferredSort,
        autoDownloadEnabled = autoDownloadEnabled,
    )
}
