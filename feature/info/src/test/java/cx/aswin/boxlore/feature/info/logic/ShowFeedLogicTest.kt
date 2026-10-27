package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.catalog.ShowPageFeedResult
import cx.aswin.boxlore.core.catalog.ShowPageFeedState
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.ShowFeedStatus
import cx.aswin.boxlore.feature.info.components.showFeedStatusText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The feed-first show page's decisions: read depth, the status line, archive gating and page growth. */
class ShowFeedLogicTest {
    private val fresh = ShowPageFeedState(cached = true, archiveComplete = false, recheckDue = false, oldestFirst = false)

    @Test
    fun `a page opens on the newest items unless it lists oldest first`() {
        assertEquals(Scope.NEWEST, ShowFeedLogic.openingScope(EpisodeSort.NEWEST, fresh))
        assertEquals(Scope.ARCHIVE, ShowFeedLogic.openingScope(EpisodeSort.OLDEST, fresh), "serial shows open oldest first")
        assertEquals(Scope.ARCHIVE, ShowFeedLogic.openingScope(EpisodeSort.NEWEST, fresh.copy(oldestFirst = true)))
    }

    @Test
    fun `a recent copy is shown without a read, an old one or a missing archive is read`() {
        assertFalse(ShowFeedLogic.readsOnOpen(fresh, Scope.NEWEST))
        assertEquals(ShowFeedStatus.CACHED, ShowFeedLogic.openingStatus(fresh))
        assertTrue(ShowFeedLogic.readsOnOpen(fresh.copy(recheckDue = true), Scope.NEWEST))
        assertTrue(ShowFeedLogic.readsOnOpen(fresh, Scope.ARCHIVE))
        assertFalse(ShowFeedLogic.readsOnOpen(fresh.copy(archiveComplete = true), Scope.ARCHIVE))
        assertEquals(ShowFeedStatus.NONE, ShowFeedLogic.openingStatus(fresh.copy(cached = false)))
    }

    @Test
    fun `the status line follows the read`() {
        assertEquals(ShowFeedStatus.READING_NEWEST, ShowFeedLogic.readingStatus(Scope.NEWEST))
        assertEquals(ShowFeedStatus.COMPLETING_ARCHIVE, ShowFeedLogic.readingStatus(Scope.ARCHIVE))
        assertEquals(ShowFeedStatus.NONE, ShowFeedLogic.statusAfter(ShowPageFeedResult.UPDATED))
        assertEquals(ShowFeedStatus.NONE, ShowFeedLogic.statusAfter(ShowPageFeedResult.UNCHANGED))
        assertEquals(ShowFeedStatus.OFFLINE, ShowFeedLogic.statusAfter(ShowPageFeedResult.OFFLINE))
        assertEquals(ShowFeedStatus.FALLBACK, ShowFeedLogic.statusAfter(ShowPageFeedResult.FALLBACK))
    }

    @Test
    fun `only the reading, fallback and offline states show a line`() {
        assertNull(showFeedStatusText(ShowFeedStatus.NONE))
        assertNull(showFeedStatusText(ShowFeedStatus.CACHED))
        assertEquals(R.string.podcast_info_feed_reading, showFeedStatusText(ShowFeedStatus.READING_NEWEST))
        assertEquals(R.string.podcast_info_feed_completing, showFeedStatusText(ShowFeedStatus.COMPLETING_ARCHIVE))
        assertEquals(R.string.podcast_info_feed_fallback, showFeedStatusText(ShowFeedStatus.FALLBACK))
        assertEquals(R.string.podcast_info_feed_offline, showFeedStatusText(ShowFeedStatus.OFFLINE))
    }

    @Test
    fun `search, the oldest sort and scrolling past the stored newest wait for the archive`() {
        assertTrue(ShowFeedLogic.needsArchive(false, ShowFeedLogic.ArchiveAction.SEARCH))
        assertTrue(ShowFeedLogic.needsArchive(false, ShowFeedLogic.ArchiveAction.SORT_OLDEST))
        assertTrue(ShowFeedLogic.needsArchive(false, ShowFeedLogic.ArchiveAction.LOAD_MORE, pageSize = 20, pageCount = 0))
        assertFalse(ShowFeedLogic.needsArchive(false, ShowFeedLogic.ArchiveAction.LOAD_MORE, pageSize = 20, pageCount = 20))
        assertFalse(ShowFeedLogic.needsArchive(true, ShowFeedLogic.ArchiveAction.SEARCH))
    }

    @Test
    fun `a reload after a read keeps every row that showed`() {
        assertEquals(20, ShowFeedLogic.reloadCount(showing = 0, pageSize = 20))
        assertEquals(60, ShowFeedLogic.reloadCount(showing = 60, pageSize = 20))
        assertEquals(120, ShowFeedLogic.reloadCount(showing = 100, pageSize = 20, extra = 20))
    }

    @Test
    fun `stored-copy episodes take the show's details only where they have none`() {
        val podcast = TestFixtures.podcast(id = "920666", title = "Curious world", imageUrl = "https://img.example/show.jpg")
        val bare = TestFixtures.episode(id = "e1", title = "One").copy(podcastTitle = null, podcastImageUrl = null, podcastId = null)
        val own = TestFixtures.episode(id = "e2", title = "Two").copy(podcastTitle = "Own", podcastImageUrl = "https://img.example/own.jpg")

        val filled = ShowFeedLogic.withShow(listOf(bare, own), podcast)

        assertEquals("Curious world", filled[0].podcastTitle)
        assertEquals("https://img.example/show.jpg", filled[0].podcastImageUrl)
        assertEquals("920666", filled[0].podcastId)
        assertEquals(own, filled[1])
        assertEquals("Curious world", ShowFeedLogic.metaOf(podcast).title)
    }

    @Test
    fun `pull to refresh on a feed-first page reads the feed`() {
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.FEED_FIRST,
            PodcastInfoPullRefreshLogic.target(isRss = false, chip = DirectFeedChipState.Hidden, feedFirst = true),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.FEED_FIRST,
            PodcastInfoPullRefreshLogic.target(isRss = false, isSubscribed = true, chip = DirectFeedChipState.Hidden, feedFirst = true),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.RSS_CATALOG,
            PodcastInfoPullRefreshLogic.target(isRss = true, chip = DirectFeedChipState.Hidden, feedFirst = true),
        )
    }
}
