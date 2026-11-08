package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesEpisodesLoaderTest {
    /**
     * A show of [count] episodes, ep-1 the oldest. Its saved copy answers a window from a known
     * episode on (200 at most); for an ID it doesn't hold it gives [unknownWindow], as the saved copy
     * gives the show's start and the server its newest.
     */
    private class FakeShow(
        val episodes: List<Episode>,
        private val unknownWindow: (List<Episode>) -> List<Episode> = { it.take(WINDOW) },
        private val aliases: Map<String, String> = emptyMap(),
    ) {
        var windowReads = 0
        var pageReads = 0
        var windowFails = false
        var pagesFail = false

        suspend fun window(aroundId: String): List<Episode> {
            windowReads++
            if (windowFails) throw IOException("window")
            val index = episodes.indexOfFirst { it.id == aroundId }
            return if (index == -1) unknownWindow(episodes) else episodes.drop(index).take(WINDOW)
        }

        suspend fun page(offset: Int): SeriesEpisodesLoader.Page {
            pageReads++
            if (pagesFail) throw IOException("page")
            val page = episodes.drop(offset).take(SeriesEpisodesLoader.PAGE_SIZE)
            return SeriesEpisodesLoader.Page(page, hasMore = offset + page.size < episodes.size)
        }

        /** Every ID of an episode shares one token; an alias stands for the episode it names. */
        suspend fun tokens(ids: Collection<String>): Map<String, String> = ids.associateWith { aliases[it] ?: it }

        fun loader() = SeriesEpisodesLoader(window = ::window, oldestPage = ::page, groupTokens = ::tokens)
    }

    private fun show(count: Int): List<Episode> = (1..count).map { TestFixtures.episode(id = "ep-$it", publishedDate = it * DAY) }

    private fun finished(range: IntRange) = range.mapTo(HashSet()) { "ep-$it" }

    @Test
    fun `a place deep in a long series is read from the saved copy, past the old 100 limit`() = runTest {
        val show = FakeShow(show(300))

        val loaded = show.loader().load("ep-150", finished(1..149))

        assertEquals("ep-150", loaded.placeId)
        assertEquals("ep-150", loaded.episodes.first().id)
        assertEquals(1, show.windowReads)
        assertEquals(0, show.pageReads)
        assertEquals("ep-150", HomeShowEpisodesLogic.ahead(loaded.episodes, loaded.placeId, finished(1..149)).first().id)
    }

    @Test
    fun `a place stored under another ID is found page by page`() = runTest {
        val show = FakeShow(show(1_000), aliases = mapOf("owner-650" to "ep-650"))

        val loaded = show.loader().load("owner-650", finished(1..650))

        assertEquals("ep-650", loaded.placeId)
        assertEquals(
            "ep-651",
            HomeShowEpisodesLogic.ahead(loaded.episodes, loaded.placeId, finished(1..650)).first().id,
        )
        // Seven pages to find it and the next one; the earlier pages aren't kept.
        assertEquals(8, show.pageReads)
        assertTrue(loaded.episodes.size <= 2 * SeriesEpisodesLoader.PAGE_SIZE + 10)
    }

    @Test
    fun `a show with only the server's newest episodes finds an early place page by page`() = runTest {
        // No saved copy yet, so the window is the server's newest 200, newest first.
        val show = FakeShow(show(1_000), unknownWindow = { it.takeLast(WINDOW).asReversed() }, aliases = mapOf("pi-90" to "ep-90"))

        val loaded = show.loader().load("pi-90", finished(1..90))

        assertEquals("ep-90", loaded.placeId)
        assertEquals("ep-91", HomeShowEpisodesLogic.ahead(loaded.episodes, loaded.placeId, finished(1..90)).first().id)
    }

    @Test
    fun `a season released at one moment is read whole and played in episode order`() = runTest {
        val sameMoment = 50 * DAY
        val season = listOf(3, 1, 4, 2, 5).map { TestFixtures.episode(id = "s2e$it", publishedDate = sameMoment).copy(seasonNumber = 2, episodeNumber = it) }
        val episodes = show(10) + season
        // The saved copy reads on from the place in its own storage order, so it can cut the season.
        val show = FakeShow(episodes)

        val loaded = show.loader().load("s2e4", setOf("s2e1", "s2e2", "s2e3", "s2e4") + finished(1..10))

        assertEquals("s2e4", loaded.placeId)
        assertEquals(listOf("s2e1", "s2e2", "s2e3", "s2e4", "s2e5"), loaded.episodes.filter { it.id.startsWith("s2") }.map { it.id })
        assertEquals("s2e5", HomeShowEpisodesLogic.ahead(loaded.episodes, loaded.placeId, setOf("s2e1", "s2e2", "s2e3", "s2e4") + finished(1..10)).single().id)
    }

    @Test
    fun `a show not started yet is read from its start`() = runTest {
        val show = FakeShow(show(300))

        val loaded = show.loader().load(null, emptySet())

        assertNull(loaded.placeId)
        assertEquals("ep-1", loaded.episodes.first().id)
        assertEquals(0, show.windowReads)
        assertEquals(1, show.pageReads)
    }

    @Test
    fun `a place that can't be found falls back to the start of the show`() = runTest {
        val show = FakeShow(show(150))

        val loaded = show.loader().load("gone", emptySet())

        assertNull(loaded.placeId)
        assertEquals("ep-1", loaded.episodes.first().id)
    }

    @Test
    fun `a long run of finished episodes after the place doesn't end the list early`() = runTest {
        // Finished 1 to 450, then replayed episode 10 to the end: the place by time is ep-10.
        val show = FakeShow(show(600))
        val done = finished(1..450)

        val loaded = show.loader().load("ep-10", done)

        assertEquals("ep-451", HomeShowEpisodesLogic.ahead(loaded.episodes, loaded.placeId, done).first().id)
    }

    @Test
    fun `a finished series stops reading at its end`() = runTest {
        val show = FakeShow(show(250))
        val done = finished(1..250)

        val loaded = show.loader().load("ep-250", done)

        assertTrue(HomeShowEpisodesLogic.ahead(loaded.episodes, loaded.placeId, done).isEmpty())
        assertTrue(show.windowReads <= 2)
    }

    @Test
    fun `a failed saved-copy read still finds the place page by page`() = runTest {
        val show = FakeShow(show(300)).apply { windowFails = true }

        val loaded = show.loader().load("ep-220", finished(1..219))

        assertEquals("ep-220", loaded.placeId)
    }

    @Test
    fun `a failed page read reaches the caller`() {
        val show = FakeShow(show(300)).apply { pagesFail = true }

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { show.loader().load(null, emptySet()) }
        }
    }

    private companion object {
        const val DAY = 86_400L
        const val WINDOW = 200
    }
}
