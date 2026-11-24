package cx.aswin.boxlore.feature.explore.logic

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What couldn't be read on Explore, so a failure never reads as "nothing there": a show search that
 * failed shows Try again instead of no matches, a Top chart that failed shows Try again instead of
 * no shows, and a chart page that failed shows Try again at the end of the grid instead of ending
 * the chart. A new request of the same kind clears its flag.
 */
internal data class ExploreLoadFailures(
    val showSearch: Boolean = false,
    val topChart: Boolean = false,
    val nextTopChartPage: Boolean = false,
    /** A mood collection's shows couldn't be read: its page offers Try again instead of "nothing here". */
    val topic: Boolean = false,
) {
    /**
     * Scrolling near the end may load the next chart page only while nothing failed: a failed chart
     * or page waits for Try again, so a lost connection can't loop requests, and a page can't fill a
     * failed chart that is still flagged (which would reload it from the first page later).
     */
    val scrollMayLoadNextPage: Boolean get() = !topChart && !nextTopChartPage

    /** A show search started: its answer replaces the earlier one. */
    fun showSearchStarted(): ExploreLoadFailures = copy(showSearch = false)

    /**
     * A show search answered. Only a new search clears a failure, never editing the text: text that
     * returns to the failed query within the debounce starts no new search ([debouncedShowSearchText]).
     */
    fun showSearchAnswered(failed: Boolean): ExploreLoadFailures = copy(showSearch = failed)
}

/** The search text Find a show searches: settled for [SHOW_SEARCH_DEBOUNCE_MS], and only when it changed. */
@OptIn(FlowPreview::class)
internal fun Flow<String>.debouncedShowSearchText(): Flow<String> = debounce(SHOW_SEARCH_DEBOUNCE_MS).distinctUntilChanged()

internal const val SHOW_SEARCH_DEBOUNCE_MS = 300L

/**
 * Top chart paging. [offset] is where the next page starts. A page that couldn't be read
 * (`loaded` null) is not the end of the chart: the offset stays, so the next request asks for the
 * same page again. Only a page that was read and came back short ends the chart.
 */
internal data class ExploreTopChartPaging(
    val offset: Int = 0,
    val hasMore: Boolean = true,
) {
    /** After a page request that read [loaded] shows, or null when it failed. */
    fun afterPage(loaded: Int?, pageSize: Int): ExploreTopChartPaging =
        if (loaded == null) this else ExploreTopChartPaging(offset = offset + loaded, hasMore = loaded >= pageSize)
}
