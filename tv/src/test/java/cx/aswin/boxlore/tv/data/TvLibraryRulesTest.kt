package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.tv.model.ShowSort
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvLibraryRulesTest {
    private fun episode(id: String, released: Long?, showId: String = "1") =
        TvEpisode(id, showId, "Episode $id", "Show", null, 30, releasedAtMillis = released, catalogKey = "key-$id")

    private fun show(id: String, title: String = "Show $id") = TvShow(id, title, "", null)

    @Test
    fun `a fresh feed read replaces the newest episodes and keeps older ones the read did not cover`() {
        val shown = listOf(episode("gone", 300), episode("kept", 100))
        val read = listOf(episode("newest", 400), episode("again", 200))
        assertEquals(listOf("newest", "again", "kept"), TvLibraryRules.replaced(shown, read).map { it.id })
        // Two releases with their own feed keys stay two, even on one reused audio file.
        val rerun = episode("rerun", 50).copy(audioUrl = "https://example.com/same.mp3")
        val original = episode("original", 400).copy(audioUrl = "https://example.com/same.mp3")
        assertEquals(listOf("original", "rerun"), TvLibraryRules.replaced(listOf(rerun), listOf(original)).map { it.id })
    }

    @Test
    fun `the page keeps a list once shown and says when nothing could be loaded`() {
        val list = listOf(episode("a", 1))
        assertEquals(ShowEpisodes.Loaded(list), TvLibraryRules.state(EpisodeLoad.Failed, list, showUnknown = false))
        assertEquals(ShowEpisodes.Failed, TvLibraryRules.state(EpisodeLoad.Failed, emptyList(), showUnknown = false))
        assertEquals(ShowEpisodes.Failed, TvLibraryRules.state(null, emptyList(), showUnknown = true))
        assertEquals(ShowEpisodes.Loading, TvLibraryRules.state(EpisodeLoad.Loading, emptyList(), showUnknown = false))
        assertEquals(ShowEpisodes.Loaded(emptyList()), TvLibraryRules.state(EpisodeLoad.Loaded, emptyList(), showUnknown = false))
        // The newest are on the page while the rest of the feed is read.
        assertEquals(ShowEpisodes.Loaded(list, more = true), TvLibraryRules.state(EpisodeLoad.LoadingMore, list, showUnknown = false))
    }

    @Test
    fun `Your shows sort as the phone's orders`() {
        val shows = listOf(show("1", "Zebra"), show("2", "apple"), show("3", "Mango"))
        val state =
            TvLibraryState(shows = shows)
                .markPlayed(episode("x", 1, showId = "3"))
                .markPlayed(episode("y", 1, showId = "3"))
                .progress(episode("z", 1, showId = "2"), 60_000L, 600_000L)
                .newest(episode("n1", 500, showId = "1"), 0)
                .newest(episode("n3", 100, showId = "3"), 0)
        fun order(sort: ShowSort) = TvLibraryRules.sorted(shows, state, sort).map { it.id }

        assertEquals(listOf("2", "3", "1"), order(ShowSort.Smart))
        assertEquals(listOf("1", "3", "2"), order(ShowSort.RecentlyUpdated))
        assertEquals(listOf("2", "3", "1"), order(ShowSort.AToZ))
        assertEquals("3", order(ShowSort.MostListened).first())
        assertEquals(listOf("1", "2", "3"), order(ShowSort.Manual))
    }

    @Test
    fun `a followed show's feed is read again only after six hours`() {
        val sixHours = TvLibraryRules.REFRESH_AFTER_MS
        assertTrue(TvLibraryRules.wantsRefresh(null, 0))
        assertTrue(!TvLibraryRules.wantsRefresh(1_000, 1_000 + sixHours - 1))
        assertTrue(TvLibraryRules.wantsRefresh(1_000, 1_000 + sixHours))
    }

    @Test
    fun `a show page reads the whole feed only when the newest filled the page, and not again soon`() {
        val now = 10 * TvLibraryRules.WHOLE_FEED_AGAIN_MS
        assertTrue(TvLibraryRules.wantsWholeFeed(fromFeed = true, read = 50, page = 50, lastReadAtMillis = null, nowMillis = now))
        assertFalse(TvLibraryRules.wantsWholeFeed(fromFeed = true, read = 12, page = 50, lastReadAtMillis = null, nowMillis = now))
        assertFalse(TvLibraryRules.wantsWholeFeed(fromFeed = false, read = 50, page = 50, lastReadAtMillis = null, nowMillis = now))
        assertFalse(TvLibraryRules.wantsWholeFeed(fromFeed = true, read = 50, page = 50, lastReadAtMillis = now - 1, nowMillis = now))
        assertTrue(TvLibraryRules.wantsWholeFeed(fromFeed = true, read = 50, page = 50, lastReadAtMillis = now - TvLibraryRules.WHOLE_FEED_AGAIN_MS, nowMillis = now))
    }
}
