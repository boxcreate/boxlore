package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeChipSelectionTest {
    private val minute = 60_000L

    private fun refresh(
        reads: HomeChipFeedReads,
        id: String,
        explicitTap: Boolean,
        nowMs: Long,
        isRss: Boolean = false,
        rssCatalogStale: Boolean = false,
        recheck: Boolean = false,
    ) = HomeChipSelection.refreshFor(
        isRss = isRss,
        rssCatalogStale = rssCatalogStale,
        manualRefreshDue = false,
        tapRead = reads.claim(id, explicitTap = explicitTap, nowMs = nowMs),
        recheck = recheck,
    )

    @Test
    fun `tapping a Podcast Index show's chip reads its feed`() {
        // Before, a show read within six hours kept its old episode when its chip was tapped.
        assertEquals(HomeChipRefresh.FEED_READ, refresh(HomeChipFeedReads(), "daily", explicitTap = true, nowMs = 0L))
    }

    @Test
    fun `tapping a healthy rss show's chip reads it as a listener's check, not a forced repair`() {
        // A forced repair read that failed offline stamped the show as read, so no automatic pass
        // read it for six hours; the tap's check keeps the show's wait when it fails.
        assertEquals(HomeChipRefresh.RSS_TAP_READ, refresh(HomeChipFeedReads(), "rss:daily", explicitTap = true, nowMs = 0L, isRss = true))
    }

    @Test
    fun `a stale or once-a-session rss show still gets its repair`() {
        assertEquals(
            HomeChipRefresh.RSS_REPAIR,
            HomeChipSelection.refreshFor(isRss = true, rssCatalogStale = true, manualRefreshDue = false, tapRead = true),
        )
        assertEquals(
            HomeChipRefresh.RSS_REPAIR,
            HomeChipSelection.refreshFor(isRss = true, rssCatalogStale = false, manualRefreshDue = true, tapRead = true),
        )
    }

    @Test
    fun `an automatic selection reads nothing`() {
        assertEquals(HomeChipRefresh.NONE, refresh(HomeChipFeedReads(), "daily", explicitTap = false, nowMs = 0L))
    }

    @Test
    fun `an automatic selection still refreshes a stale rss catalog`() {
        assertEquals(
            HomeChipRefresh.RSS_REPAIR,
            HomeChipSelection.refreshFor(isRss = true, rssCatalogStale = true, manualRefreshDue = false, tapRead = false),
        )
        assertEquals(
            HomeChipRefresh.RSS_REPAIR,
            HomeChipSelection.refreshFor(isRss = true, rssCatalogStale = false, manualRefreshDue = true, tapRead = false),
        )
    }

    @Test
    fun `a second tap within 15 minutes reads nothing`() {
        val reads = HomeChipFeedReads()
        assertEquals(HomeChipRefresh.FEED_READ, refresh(reads, "daily", explicitTap = true, nowMs = 0L))
        assertEquals(HomeChipRefresh.NONE, refresh(reads, "daily", explicitTap = true, nowMs = 14 * minute))
    }

    @Test
    fun `with one followed show, tapping its chip checks it once per 15 minutes`() {
        // Before, the only show's chip ignored taps and its automatic selection read nothing,
        // so it kept its old episode until the six-hour wait ended.
        val reads = HomeChipFeedReads()
        assertEquals(HomeChipRefresh.NONE, refresh(reads, "daily", explicitTap = false, nowMs = 0L))

        assertEquals(HomeChipTap.RECHECK, HomeChipSelection.tapKind(tappedId = "daily", selectedId = "daily", onlyShowId = "daily"))
        assertEquals(HomeChipRefresh.FEED_READ, refresh(reads, "daily", explicitTap = true, nowMs = minute, recheck = true))
        assertEquals(HomeChipRefresh.NONE, refresh(reads, "daily", explicitTap = true, nowMs = 2 * minute, recheck = true))
    }

    @Test
    fun `re-tapping a stale rss show repairs it once per 15 minutes, not on every tap`() {
        // The repair is a full download of the publisher's feed; a feed that keeps failing stays
        // stale, so without the limit every re-tap downloaded it again.
        val reads = HomeChipFeedReads()
        val retap = { nowMs: Long -> refresh(reads, "rss:daily", explicitTap = true, nowMs = nowMs, isRss = true, rssCatalogStale = true, recheck = true) }
        assertEquals(HomeChipRefresh.RSS_REPAIR, retap(0L))
        repeat(10) { assertEquals(HomeChipRefresh.NONE, retap(it * minute + 1)) }
        assertEquals(HomeChipRefresh.RSS_REPAIR, retap(15 * minute))
    }

    @Test
    fun `a refused re-tap refreshes nothing, but a real selection of a stale rss show still repairs it`() {
        assertEquals(
            HomeChipRefresh.NONE,
            HomeChipSelection.refreshFor(isRss = true, rssCatalogStale = true, manualRefreshDue = true, tapRead = false, recheck = true),
        )
        assertEquals(
            HomeChipRefresh.RSS_REPAIR,
            HomeChipSelection.refreshFor(isRss = true, rssCatalogStale = true, manualRefreshDue = false, tapRead = false, recheck = false),
        )
    }

    @Test
    fun `only a tap on the selected show is a recheck`() {
        assertEquals(HomeChipTap.SELECT, HomeChipSelection.tapKind(tappedId = "serial", selectedId = "daily", onlyShowId = null))
        assertEquals(HomeChipTap.SELECT, HomeChipSelection.tapKind(tappedId = "daily", selectedId = null, onlyShowId = null))
        assertEquals(HomeChipTap.SELECT, HomeChipSelection.tapKind(tappedId = null, selectedId = null, onlyShowId = "daily"))
        assertEquals(HomeChipTap.SELECT, HomeChipSelection.tapKind(tappedId = null, selectedId = "daily", onlyShowId = "daily"))
    }

    @Test
    fun `tapping the only show after leaving Home keeps it automatic, so a second show falls back to the Mix`() {
        // Leaving Home for another tab clears the selection while the only chip still looks selected.
        // A tap then made it an explicit filter, so following a second show kept Home on the first.
        val daily = Podcast(id = "daily", title = "Daily", artist = "", imageUrl = "")
        val serial = Podcast(id = "serial", title = "Serial", artist = "", imageUrl = "")
        val tap = HomeChipSelection.tapKind(tappedId = "daily", selectedId = null, onlyShowId = "daily")
        assertEquals(HomeChipTap.RESTORE_ONLY_SHOW, tap)

        // HomeViewModel applies RESTORE_ONLY_SHOW as an automatic selection (it still checks the show).
        val filterSelectionIsAuto = tap == HomeChipTap.RESTORE_ONLY_SHOW
        assertEquals(
            FilterSelectionAction.Clear,
            HomeFilterSelectionLogic.decide(currentSelectedId = "daily", subs = listOf(daily, serial), filterSelectionIsAuto = filterSelectionIsAuto),
        )
    }
}
