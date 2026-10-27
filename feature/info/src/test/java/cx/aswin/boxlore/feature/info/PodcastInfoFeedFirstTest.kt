package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowPageFeedRead
import cx.aswin.boxlore.core.catalog.ShowPageFeedResult
import cx.aswin.boxlore.core.catalog.ShowPageFeedState
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The feed-first show page's reads and reloads ([PodcastInfoFeedFirst]) against a scripted source:
 * a fallback stops further feed reads until the page opens again, newer episodes above keep the
 * rows the listener was on, offline without a copy says so, the end of the copy waits for a
 * connection, concurrent searches share one archive read, and following hands the page over.
 */
class PodcastInfoFeedFirstTest {
    private val source = FakeSource()
    private val ui = FakeUi()
    private val feedFirst = PodcastInfoFeedFirst(source, ui)
    private val podcast = TestFixtures.podcast(id = SHOW, title = "Show")

    @Test
    fun `after a fallback, search, the oldest sort and scrolling read no feed until the page opens again`() = runTest {
        source.list = episodes(0 until 20)
        feedFirst.open(SHOW, podcast)
        ui.show(podcast, source.list)
        source.results += ShowPageFeedResult.FALLBACK

        assertEquals(ShowPageFeedResult.FALLBACK, feedFirst.completeArchive(this, podcast)?.result)
        repeat(3) { assertNull(feedFirst.completeArchive(this, podcast), "no read after the fallback") }

        assertEquals(1, source.reads.size)
        assertTrue(feedFirst.isActive(SHOW), "pull to refresh still reads the feed")
        assertFalse(feedFirst.readsFeed(SHOW))
        assertEquals(ShowFeedStatus.FALLBACK, ui.current?.feedStatus)

        feedFirst.open(SHOW, podcast)
        assertTrue(feedFirst.readsFeed(SHOW), "opening the page tries the feed again")
    }

    @Test
    fun `newer episodes above keep the rows the listener was on`() = runTest {
        source.list = episodes(0 until 100)
        feedFirst.open(SHOW, podcast)
        ui.show(podcast, source.list.take(40))
        // The read finds five newer episodes, which go above the list.
        source.onRead = { source.list = episodes(-5 until 0) + source.list }

        feedFirst.read(podcast, Scope.NEWEST)

        val shown = requireNotNull(ui.current).piEpisodes.map { it.id }
        assertEquals("e-5", shown.first())
        assertEquals(44, shown.indexOf("e39"), "the row that was last is still in the list, below the five new ones")
        assertEquals(shown.size, ui.lastOffset)
    }

    @Test
    fun `offline without a stored copy never says saved episodes show`() = runTest {
        feedFirst.open(SHOW, podcast)
        ui.show(podcast, emptyList())
        source.results += ShowPageFeedResult.OFFLINE
        source.hasCopy = false

        feedFirst.read(podcast, Scope.NEWEST)

        assertEquals(ShowFeedStatus.OFFLINE_NO_COPY, ui.current?.feedStatus)
    }

    @Test
    fun `the end of the copy waits for a connection, and the list stays open for it`() = runTest {
        source.list = episodes(0 until 20)
        source.archivePending = true
        feedFirst.open(SHOW, podcast)
        ui.show(podcast, source.list)
        source.results += ShowPageFeedResult.OFFLINE
        source.online = false

        feedFirst.completeArchive(this, podcast, extra = 20, atCopyEnd = true)

        assertTrue(feedFirst.holdsAtCopyEnd())
        assertEquals(ShowFeedStatus.OFFLINE, ui.current?.feedStatus)
        assertTrue(requireNotNull(ui.current).hasMoreEpisodes, "scrolling picks up again once online")
        source.online = true
        assertFalse(feedFirst.holdsAtCopyEnd())
    }

    @Test
    fun `searches typed while the archive reads wait on that one read`() = runTest {
        source.list = episodes(0 until 20)
        feedFirst.open(SHOW, podcast)
        ui.show(podcast, source.list)
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        source.results += ShowPageFeedResult.UPDATED
        source.archiveAfterRead = true

        val first = async { feedFirst.completeArchive(this@runTest, podcast) }
        val second = async { feedFirst.completeArchive(this@runTest, podcast) }
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)

        assertEquals(ShowPageFeedResult.UPDATED, first.await()?.result)
        assertEquals(ShowPageFeedResult.UPDATED, second.await()?.result)
        assertEquals(listOf(Scope.ARCHIVE), source.reads)
        assertTrue(feedFirst.archiveComplete)
        assertNull(feedFirst.completeArchive(this, podcast), "a whole archive is never read for search again")
    }

    @Test
    fun `a read that ends after the page moved to another show changes nothing`() = runTest {
        source.list = episodes(0 until 20)
        feedFirst.open(SHOW, podcast)
        ui.show(podcast, source.list)
        val before = ui.current
        source.onRead = { ui.podcastId = "other" }

        assertNull(feedFirst.read(podcast, Scope.NEWEST))
        assertEquals(before?.piEpisodes, ui.current?.piEpisodes)
    }

    @Test
    fun `a serial show reads its archive as it opens, and following hands the page over`() = runTest {
        source.list = episodes(0 until 20)
        val serial = podcast.copy(type = "serial")
        val opened = requireNotNull(feedFirst.open(SHOW, serial))
        ui.show(serial, source.list)
        source.results += ShowPageFeedResult.UPDATED

        feedFirst.begin(serial, EpisodeSort.NEWEST, opened)

        assertEquals(listOf(Scope.ARCHIVE), source.reads)
        feedFirst.stop()
        assertFalse(feedFirst.isActive(SHOW))
        assertFalse(feedFirst.readsFeed(SHOW))
    }

    private class FakeSource : PodcastInfoFeedFirst.Source {
        var list: List<Episode> = emptyList()
        var archivePending = false
        var archiveAfterRead = false
        var hasCopy = true
        var online = true
        var gate: CompletableDeferred<Unit>? = null
        var onRead: () -> Unit = {}
        val results = ArrayDeque<ShowPageFeedResult>()
        val reads = mutableListOf<Scope>()
        private var archiveComplete = false

        override suspend fun open(podcastId: String, meta: PodcastMeta): ShowPageFeedState =
            ShowPageFeedState(cached = list.isNotEmpty(), archiveComplete = archiveComplete, recheckDue = false, oldestFirst = false)

        override suspend fun read(podcastId: String, meta: PodcastMeta, scope: Scope, savedFeedUrl: String?): ShowPageFeedRead {
            reads += scope
            gate?.await()
            onRead()
            if (archiveAfterRead && scope == Scope.ARCHIVE) archiveComplete = true
            return ShowPageFeedRead(results.removeFirstOrNull() ?: ShowPageFeedResult.UPDATED, archiveComplete, hasCopy)
        }

        override suspend fun page(podcastId: String, limit: Int, offset: Int, sort: String): PodcastRepository.EpisodePage {
            val page = list.drop(offset).take(limit)
            return PodcastRepository.EpisodePage(page, hasMore = offset + page.size < list.size || archivePending, sourceCount = page.size)
        }

        override fun online(): Boolean = online
    }

    private class FakeUi : PodcastInfoFeedFirst.Ui {
        var current: PodcastInfoUiState.Success? = null
        var podcastId = SHOW
        var lastOffset = -1

        fun show(podcast: cx.aswin.boxlore.core.model.Podcast, episodes: List<Episode>) {
            current = PodcastInfoUiState.Success(podcast = podcast, episodes = episodes, piEpisodes = episodes, isSubscribed = false)
        }

        override fun state(): PodcastInfoUiState.Success? = current

        override fun set(state: PodcastInfoUiState.Success) {
            current = state
        }

        override fun currentPodcastId(): String = podcastId

        override fun setOffset(offset: Int) {
            lastOffset = offset
        }

        override suspend fun remount(state: PodcastInfoUiState.Success, episodes: List<Episode>, hasMore: Boolean): PodcastInfoUiState.Success =
            state.copy(episodes = episodes, piEpisodes = episodes, hasMoreEpisodes = hasMore)
    }

    private companion object {
        const val SHOW = "920666"

        fun episodes(range: IntProgression): List<Episode> = range.map { TestFixtures.episode(id = "e$it", podcastId = SHOW) }
    }
}
