package cx.aswin.boxlore.feature.info.logic

/**
 * "Hide completed episodes" filters the episodes already loaded, and the next page loads only
 * when the last row is drawn. With every loaded episode finished there was no row, so nothing ever
 * loaded and the page stayed blank (listener report on 0.0.29, worst after reversing the order).
 * While too few unfinished episodes show, the page keeps loading pages until enough appear or the
 * show runs out, then says the listener is all caught up.
 */
internal object HideCompletedFill {
    /** Fewer visible episodes than this, with more to load, loads the next page. */
    const val MIN_VISIBLE = 8

    enum class State {
        /** Hiding is off, a search is showing, or enough episodes are visible. */
        NONE,

        /** Completed episodes are hidden and more pages are loading to find unfinished ones. */
        FINDING,

        /** Completed episodes are hidden and the show has no unfinished episode left. */
        CAUGHT_UP,
    }

    fun state(hideCompleted: Boolean, searching: Boolean, visibleCount: Int, loadedCount: Int, hasMore: Boolean, isLoadingMore: Boolean,): State = when {
        !hideCompleted || searching || visibleCount >= MIN_VISIBLE -> State.NONE
        hasMore || isLoadingMore -> State.FINDING
        visibleCount == 0 && loadedCount > 0 -> State.CAUGHT_UP
        else -> State.NONE
    }

    /** True when the page should ask for the next page now. */
    fun loadsMore(hideCompleted: Boolean, searching: Boolean, visibleCount: Int, hasMore: Boolean, isLoadingMore: Boolean,): Boolean = hideCompleted && !searching && visibleCount < MIN_VISIBLE && hasMore && !isLoadingMore
}
